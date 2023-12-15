import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.util.concurrent.LinkedBlockingQueue;

import com.tdunning.math.stats.MergingDigest;




public class Writer extends Thread {



    private LinkedBlockingQueue<CallInfo> callInfoQueue;

    private int postSuccess = 0;
    private long postLatencySum = 0;
    private long maxPostLatency = Long.MIN_VALUE;
    private long minPostLatency = Long.MAX_VALUE;
    private MergingDigest postDigest = new MergingDigest(50,1000,100);

    private int postSentimentSuccess = 0;
    private long postSentimentSum = 0;
    private long maxPostSentimentLatency = Long.MIN_VALUE;
    private long minPostSentimentLatency = Long.MAX_VALUE;
    private MergingDigest postSentimentDigest = new MergingDigest(50,1000,100);


    private int getReviewSuccess = 0;
    private long getReviewSum = 0;
    private long maxGetReviewLatency = Long.MIN_VALUE;
    private long minGetReviewLatency = Long.MAX_VALUE;
    private MergingDigest getReviewDigest = new MergingDigest(50,1000,100);

    private final int MILLISECONDS_PER_SECOND = 1000;


    public Writer(LinkedBlockingQueue<CallInfo> callInfoQueue) {
        this.callInfoQueue = callInfoQueue;
    }

    public void run() {
        //Creates variables to track throughput every second
        long start = System.currentTimeMillis();
        long throughputEndTime = start;
        long callEndTime;
        int callCount = 0;
        int second=0;

        CSVFormat csvFormat = CSVFormat.DEFAULT.withHeader("Start Time","Request Type","Latency","Response Code");
        try {
            FileWriter fileWriter = new FileWriter("latency.csv");
            CSVPrinter csvPrinter = new CSVPrinter(fileWriter,csvFormat);
            while (true) {
                CallInfo callInfo = callInfoQueue.take();
                long latency = callInfo.getLatency();
                if (callInfo.getResponseCode() == -1) {
                    break;
                } else {
                    csvPrinter.printRecord(
                            callInfo.getStartTime(),
                            callInfo.getRequestType(),
                            latency,
                            callInfo.getResponseCode()
                    );
                    if (callInfo.getRequestType().equals("PostSentiment")) {
                        postSentimentDigest.add(latency);
                        maxPostSentimentLatency = Math.max(maxPostSentimentLatency,latency);
                        minPostSentimentLatency = Math.min(minPostSentimentLatency,latency);
                        postSentimentSuccess += 1;
                        postSentimentSum += latency;

                    } else if (callInfo.getRequestType().equals("PostAlbum")) {
                        postDigest.add(latency);
                        maxPostLatency = Math.max(maxPostLatency,latency);
                        minPostLatency = Math.min(minPostLatency,latency);
                        postSuccess += 1;
                        postLatencySum += latency;
                    } else if (callInfo.getRequestType().equals("getReview")) {
                        getReviewDigest.add(latency);
                        maxGetReviewLatency = Math.max(maxGetReviewLatency,latency);
                        minGetReviewLatency = Math.min(minGetReviewLatency,latency);
                        getReviewSuccess += 1;
                        getReviewSum += latency;
                    }
                    callEndTime = callInfo.getStartTime() + latency;

                    //Calculates and prints throughput every second
                    if (System.currentTimeMillis()>=throughputEndTime) {
                       System.out.print("("+((throughputEndTime-start)/MILLISECONDS_PER_SECOND)+","+callCount+"),");
                        throughputEndTime+=MILLISECONDS_PER_SECOND;
                        callCount=1;
                        second+=1;
                    } else {
                        callCount+=1;
                    }
                }
            }
            csvPrinter.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

         ;
    }

    public LinkedBlockingQueue<CallInfo> getCallInfoQueue() {
        return callInfoQueue;
    }


    public MergingDigest getPostDigest() {
        return postDigest;
    }

    public long getMaxPostLatency() {
        return maxPostLatency;
    }

    public long getMinPostLatency() {
        return minPostLatency;
    }

    public int getPostSuccess() {
        return postSuccess;
    }

    public long getPostLatencySum() {
        return postLatencySum;
    }


    public MergingDigest getPostSentimentDigest() {return postSentimentDigest;}

    public int getPostSentimentSuccess() {return postSentimentSuccess;}

    public long getPostSentimentSum() {return postSentimentSum;}

    public long getMaxPostSentimentLatency() {return maxPostSentimentLatency;}

    public long getMinPostSentimentLatency() {return minPostSentimentLatency;}

    public int getGetReviewSuccess() {
        return getReviewSuccess;
    }

    public long getGetReviewSum() {
        return getReviewSum;
    }

    public long getMaxGetReviewLatency() {
        return maxGetReviewLatency;
    }

    public long getMinGetReviewLatency() {
        return minGetReviewLatency;
    }

    public MergingDigest getGetReviewDigest() {
        return getReviewDigest;
    }
}
