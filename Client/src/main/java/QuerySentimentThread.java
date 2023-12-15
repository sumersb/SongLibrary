import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

public class QuerySentimentThread extends Thread{

    private final String url;
    private final String reviewPath;
    private final LinkedBlockingQueue<CallInfo> latencyQueue;
    private final HttpClient client = new HttpClient();
    private GetMethod getMethod;
    ThreadSafeLargestNumber largestNumber;

    public QuerySentimentThread(String URL, String path, LinkedBlockingQueue<CallInfo> latencyQueue, ThreadSafeLargestNumber largestNumber) {
        this.url = URL;
        this.reviewPath = "/"+path+"/review/";
        this.latencyQueue = latencyQueue;
        this.largestNumber = largestNumber;
        getMethod = new GetMethod(url);
    }

    public void run() {
        while (true) {
            try {
                executeGetMethod(getMethod);
            } catch (Exception e) {
                System.out.println("Failed Get Request");
                e.printStackTrace();
            }
        }
    }

    private void executeGetMethod(GetMethod getMethod) throws IOException, HttpException {
        getMethod.setPath(reviewPath+largestNumber.getRandomInt());
        long startTime = System.currentTimeMillis();
        int response = client.executeMethod(getMethod);
        if (response != HttpStatus.SC_OK) {
            System.out.println("Request failed");
            System.err.println("Method failed: " + getMethod.getStatusLine());
            System.err.println(getMethod.getResponseBodyAsString());
            throw new HttpException();
        }
        long endTime = System.currentTimeMillis();
        getMethod.releaseConnection();
        long latency = endTime - startTime;
        latencyQueue.add(new CallInfo(startTime, "getReview", latency, response));
    }
}
