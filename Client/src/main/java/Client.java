import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.*;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.httpclient.methods.multipart.Part;


import java.awt.*;
import java.io.*;
import java.util.concurrent.LinkedBlockingQueue;

public class Client extends Thread {

    private final String path;
    private final String reviewPath;
    private final int iterations;
    private final HttpClient client;
    private PostMethod postMethod;
    private PostMethod postLike;
    private PostMethod postDislike;
    private final LinkedBlockingQueue<CallInfo> latencyQueue;
    ThreadSafeLargestNumber largestNumber;


    public Client(String url, String path, int iterations, LinkedBlockingQueue<CallInfo> latencyQueue, ThreadSafeLargestNumber largestNumber) throws FileNotFoundException {
        this.path = "/"+path;
        this.reviewPath = "/"+path+"/review";
        this.iterations = iterations;
        this.latencyQueue = latencyQueue;
        this.largestNumber = largestNumber;

        client = new HttpClient();

        // Create a post method instance.
        postMethod = generatePostAlbum(url, path);
        // Create a post Album method instance
        postLike = new PostMethod(url);
        postDislike = new PostMethod(url);
    }


    public void run() {
        int statusCode;
        long startTime;
        long endTime;
        long latency;

        //Iterates iterations times calling post then get method
        for (int i = 0; i<iterations ; i++) {
            try {
                ImageMetaData postInfo = executePostAlbum(client, postMethod, latencyQueue);
                if (postInfo == null) {throw new HttpException();}
                //Get Album Key from server and adjust the like and dislike path
                largestNumber.update(Integer.parseInt(postInfo.getAlbumID()));

                postLike.setPath(reviewPath+"/like/"+postInfo.getAlbumID());
                postDislike.setPath(reviewPath+"/dislike/"+postInfo.getAlbumID());

                executePostSentiment(client,postLike,latencyQueue);
                executePostSentiment(client,postLike,latencyQueue);
                executePostSentiment(client, postDislike, latencyQueue);

            } catch (Exception e) {
                System.err.println("Fatal protocol violation: " + e.getMessage());
                e.printStackTrace();
            }
        }

    }
    private ImageMetaData executePostAlbum(HttpClient client, PostMethod postMethod, LinkedBlockingQueue<CallInfo> latencyInfo) throws IOException {
        long startTime = System.currentTimeMillis();
        int response = client.executeMethod(postMethod);
        if (response != HttpStatus.SC_CREATED) {
            System.out.println("Request failed");
            System.err.println("Method failed: " + postMethod.getStatusLine());
            System.err.println(postMethod.getResponseBodyAsString());
        }
        ImageMetaData postInfo = JsonUtils.jsonToObject(postMethod.getResponseBodyAsString(),ImageMetaData.class);
        postMethod.releaseConnection();
        long endTime = System.currentTimeMillis();
        long latency = endTime-startTime;
        if (latencyInfo != null) {
            latencyInfo.add(new CallInfo(startTime, "PostAlbum", latency, response));
        }
        return postInfo;
    }

    private void executePostSentiment(HttpClient client, PostMethod postMethod, LinkedBlockingQueue<CallInfo> latencyInfo) throws IOException {
        long startTime = System.currentTimeMillis();
        int response = client.executeMethod(postMethod);
        if (response != HttpStatus.SC_CREATED) {
            System.out.println("Request failed");
            System.err.println("Method failed: " + postMethod.getStatusLine());
            System.err.println(postMethod.getResponseBodyAsString());
        }
        postMethod.releaseConnection();
        long endTime = System.currentTimeMillis();
        long latency = endTime-startTime;
        if (latencyInfo != null) {
            latencyInfo.add(new CallInfo(startTime, "PostSentiment", latency, response));
        }
    }

    private PostMethod generatePostAlbum(String url, String path) throws FileNotFoundException {
        postMethod = new PostMethod(url+this.path+"/albums");
        Part[] parts = {
                new StringPart("profile", "{\"artist\": \"Shakira\", \"title\": \"waka waka\", \"year\": \"2012\"}", "UTF-8"),
                new FilePart("image",new File("src/assets/nmtb.png"),"image/png", null)
        };
        MultipartRequestEntity requestEntity = new MultipartRequestEntity(parts, postMethod.getParams());
        postMethod.setRequestEntity(requestEntity);
        postMethod.setRequestHeader("accept", "application/json");
        postMethod.getParams().setParameter(HttpMethodParams.RETRY_HANDLER,
                new DefaultHttpMethodRetryHandler(5, false));
        return postMethod;
    }
}