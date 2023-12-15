import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.LinkedBlockingQueue;
import com.tdunning.math.stats.MergingDigest;
public class ClientThreadManager {

    private static final float MILLISECOND_TO_SECOND= (float) 1 /1000;

    private static final long SECOND_TO_MILLISECOND = 1000;

    private static final int STARTUP_THREAD_COUNT = 10;

    private static  final int ITERATIONS_PER_STARTUP_THREAD = 100;

    private static final int ITERATIONS_PER_THREAD = 100;

    private static final int GET_REVIEW_THREAD = 3;

    private int threadGroupSize;

    private int numThreadGroups;

    private static final int REQUESTS_PER_ITERATION = 4;

    private int delay;

    private String IPAddr;

    private String path;


    public ClientThreadManager(int threadGroupSize, int numThreadGroups, int delay, String URI) {
        this.threadGroupSize = threadGroupSize;
        this.numThreadGroups = numThreadGroups;
        this.delay = delay;
        parseURL(URI);
        System.out.println(IPAddr);
        System.out.println(path);
    }

    public void callThreads() throws InterruptedException, FileNotFoundException {
        ThreadSafeLargestNumber largestNumber = new ThreadSafeLargestNumber(-1);
        largestNumber.update(Integer.parseInt("32"));
        //Create threadsafe queue to store latency information
        LinkedBlockingQueue<CallInfo> callInfoQueue= new LinkedBlockingQueue<>();
        //Creates writer instance to process callInfo information of all threads
        Writer writer = new Writer(callInfoQueue);
        writer.start();


        // Initialize jagged threadArray with 10 Threads in First Index and threadGroupSize threads in rest of indexes
        Thread[][] threadArray = createThreadArray(callInfoQueue, largestNumber);
        Thread[] sentimentThreads = createSentimentClientArray(callInfoQueue, largestNumber);

        initThreadArray((threadArray[0]));
        finishThreadArray((threadArray[0]));

        long startTime = System.currentTimeMillis();

        initAllClientThreads(threadArray);
        long getReviewWallTime = initSentimentandCompleteAll(threadArray,sentimentThreads);

        long overallWallTime = System.currentTimeMillis() - startTime;

        //Add marker to callInfoQueue to tell writer to stop
        callInfoQueue.add(new CallInfo(-1,null,-1,-1));

        writer.join();

        //Pass information stores to printStats
        printStats(overallWallTime, getReviewWallTime, writer);
    }

    private void printStats(long overallWallTime, long getReviewWallTime, Writer writer) {

        //ITERATIONS_PER_THREAD multiplied BY 2 to account for get and post request
        int callSize = numThreadGroups * threadGroupSize * ITERATIONS_PER_THREAD * REQUESTS_PER_ITERATION ;

        //Self-explanatory
        float wallTime =  overallWallTime * MILLISECOND_TO_SECOND;

        float getWallTime = getReviewWallTime * MILLISECOND_TO_SECOND;

        float throughput = (float) (writer.getPostSentimentSuccess()+writer.getPostSuccess()) / wallTime;

        float getReviewThroughput = (float) (writer.getGetReviewSuccess() / getWallTime);
        float getReviewMean = (float) writer.getGetReviewSum() / writer.getGetReviewSuccess();
        double getReviewMedian = writer.getGetReviewDigest().quantile(0.5);
        double getReviewPercentile99 = writer.getGetReviewDigest().quantile(0.99);

        float postMean = (float) writer.getPostLatencySum() / writer.getPostSuccess();
        double postMedian = writer.getPostDigest().quantile(0.5);
        double postPercentile99 = writer.getPostDigest().quantile(0.99);

        float postSentimentMean = (float) writer.getPostSentimentSum() / writer.getPostSentimentSuccess();
        double postSentimentMedian = writer.getPostSentimentDigest().quantile(0.5);
        double postSentimentPercentile99 = writer.getPostSentimentDigest().quantile(0.99);



        //Print out all information to terminal
        System.out.println();
        System.out.println("Number Thread Groups: " + numThreadGroups);
        System.out.println("Thread Group Size: " + threadGroupSize);

        System.out.println();
        System.out.println("Server Calls: " + callSize);
        System.out.println("Success: "+ (writer.getPostSentimentSuccess()+writer.getPostSuccess()));
        System.out.println("Failures: "+(callSize-(writer.getPostSentimentSuccess()+writer.getPostSuccess())));

        System.out.println();
        System.out.println("Wall Time: " + wallTime + " seconds");
        System.out.println("Throughput: " + throughput + " calls per second");

        System.out.println();
        System.out.println("Min Sentiment Latency: " + writer.getMinPostSentimentLatency());
        System.out.println("Sentiment Mean: " + postSentimentMean);
        System.out.println("Sentiment Median: " + postSentimentMedian);
        System.out.println("Sentiment 99 Percentile: " + postSentimentPercentile99);
        System.out.println("Sentiment Max Latency: " + writer.getMaxPostSentimentLatency());

        System.out.println();
        System.out.println("Min Post Album Latency: " + writer.getMinPostLatency());
        System.out.println("Post Album Mean: " + postMean);
        System.out.println("Post Album Median: " + postMedian);
        System.out.println("Post Album 99 Percentile: " + postPercentile99);
        System.out.println("Post Album Max Latency: " + writer.getMaxPostLatency());

        System.out.println();
        System.out.println("Get Review Success: " + writer.getGetReviewSuccess());
        System.out.println("Get Review Wall Time: " + getWallTime);
        System.out.println("Get Review Throughput: " + getReviewThroughput);
        System.out.println("Min Get Review Latency: " + writer.getMinGetReviewLatency());
        System.out.println("Get Review Mean: " + getReviewMean);
        System.out.println("Get Review Median: " + getReviewMedian);
        System.out.println("Get Review 99 Percentile: " + getReviewPercentile99);
        System.out.println("Get Review Max Latency: " + writer.getMaxGetReviewLatency());


    }


    private Thread[][] createThreadArray(LinkedBlockingQueue<CallInfo> latencyQueue, ThreadSafeLargestNumber largestNumber) throws FileNotFoundException {
        Thread[][] clientArray = new Thread[numThreadGroups+1][];

        clientArray[0] = new Thread[STARTUP_THREAD_COUNT];

        for (int i = 1; i < numThreadGroups+1; i++) {
            clientArray[i] = new Thread[threadGroupSize];
        }

        for (int i = 0; i < clientArray.length; i++) {
            for (int j = 0; j < clientArray[0].length; j++) {
                if (i == 0) {
                    clientArray[i][j] = new Client(IPAddr, path,ITERATIONS_PER_STARTUP_THREAD, null,largestNumber);
                } else {
                    clientArray[i][j] = new Client(IPAddr, path, ITERATIONS_PER_THREAD,latencyQueue, largestNumber);
                }
            }
        }
        return clientArray;
    }

    private Thread[] createSentimentClientArray(LinkedBlockingQueue<CallInfo> latencyQueue, ThreadSafeLargestNumber largestNumber) {
        Thread[] semtimentClientArray = new Thread[GET_REVIEW_THREAD];
        for (int i = 0; i < GET_REVIEW_THREAD; i++) {
            semtimentClientArray[i] = new QuerySentimentThread(IPAddr,path,latencyQueue,largestNumber);
        }
        return semtimentClientArray;
    }

    private void initAllClientThreads(Thread[][] clientArray) throws InterruptedException {
        for (int i = 1; i < clientArray.length; i++) {
            initThreadArray(clientArray[i]);
            //Delay between next thread group iteration
            Thread.sleep( delay * SECOND_TO_MILLISECOND);
        }
    }

    private long initSentimentandCompleteAll(Thread[][] clientArray, Thread[] getReviewArray) throws InterruptedException {
        Long startTime = null;
        Long endTime = null;
        for (int i = 1; i < clientArray.length ; i++) {
            finishThreadArray(clientArray[i]);
            if (i == 1) {
                System.out.println("Review Threads started");
                startTime = System.currentTimeMillis();
                initThreadArray(getReviewArray);
            }
        }
        forceEndArray(getReviewArray);
        endTime = System.currentTimeMillis();
        return endTime - startTime;
    }

    private void initThreadArray(Thread[] array) {
        for (int i = 0; i < array.length; i++) {
            array[i].start();
        }
    }

    private void finishThreadArray(Thread[] array) throws InterruptedException {
        for (int i = 0; i < array.length; i++) {
            array[i].join();
        }
    }

    private void forceEndArray(Thread[] array) {
        for (Thread thread: array) {
            thread.interrupt();
        }
    }


    private void parseURL(String URL) {
        try {
            URI uri = new URI(URL);

            // Extracting the host (http://localhost:8080)
            String host = uri.getScheme() + "://" + uri.getHost();
            if (uri.getPort() != -1) {
                host += ":" + uri.getPort();
            }

            // Extracting the path (AlbumStore_war_exploded)
            String path = uri.getPath().substring(1); // Remove leading slash

            this.IPAddr = host;
            this.path = path;
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public int getThreadGroupSize() {
        return threadGroupSize;
    }

    public void setThreadGroupSize(int threadGroupSize) {
        this.threadGroupSize = threadGroupSize;
    }

    public int getNumThreadGroups() {
        return numThreadGroups;
    }

    public void setNumThreadGroups(int numThreadGroups) {
        this.numThreadGroups = numThreadGroups;
    }

    public int getDelay() {
        return delay;
    }

    public void setDelay(int delay) {
        this.delay = delay;
    }

    public String getIPAddr() {
        return IPAddr;
    }

    public void setIPAddr(String IPAddr) {
        this.IPAddr = IPAddr;
    }

}
