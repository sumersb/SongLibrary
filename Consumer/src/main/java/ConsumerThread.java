import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import org.apache.commons.dbcp2.BasicDataSource;

import java.io.IOException;
import java.sql.PreparedStatement;

public class ConsumerThread extends Thread {
    Channel channel;
    BasicDataSource dataSource;
    String QUEUE_NAME;

    public ConsumerThread(Channel channel, BasicDataSource dataSource, String QUEUE_NAME) {
        this.channel = channel;
        this.dataSource = dataSource;
        this.QUEUE_NAME = QUEUE_NAME;
    }

    public void run() {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            //Extract URI and process elements
            String URI = new String(delivery.getBody(), "UTF-8");
            System.out.println(URI);
            String[] uriElements = URI.split("/");
            Integer album = Integer.valueOf(uriElements[uriElements.length-1]);
            String sentiment = uriElements[uriElements.length-2];

            //Get database connection
            try (java.sql.Connection con = dataSource.getConnection()) {

                //Sets query depending on whether sentiment is like or dislike
                String query = sentiment.equalsIgnoreCase("like") ?
                        "INSERT INTO albumSentiment (album_id, likes, dislikes)\n" +
                                "VALUES (?, 1, 0)\n" +
                                "ON DUPLICATE KEY UPDATE likes = likes + 1" :
                        "INSERT INTO albumSentiment (album_id, likes, dislikes)\n" +
                                "VALUES (?, 0, 1)\n" +
                                "ON DUPLICATE KEY UPDATE dislikes = dislikes + 1";


                //Set parameters for preparedStatement
                try(PreparedStatement preparedStatement = con.prepareStatement(query)) {
                    preparedStatement.setInt(1,album);

                    //Execute preparedStatement
                    preparedStatement.executeUpdate();

                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("new fail");
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("fnoiwqndoinwqndiqwo");
            }
        };
        try {
            //Consume queue at end of life
            channel.basicConsume(QUEUE_NAME, true, deliverCallback, consumerTag -> { });
        } catch (IOException e) {
            System.out.println("failing :(");
            e.printStackTrace();
        }

    }

}