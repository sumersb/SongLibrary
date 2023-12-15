import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DeliverCallback;
import org.apache.commons.dbcp2.BasicDataSource;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.dbcp2.BasicDataSource;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class ConsumerThread extends Thread {
    Channel channel;
    BasicDataSource dataSource;
    JedisPool jedisPool;
    String QUEUE_NAME;

    public ConsumerThread(Channel channel, BasicDataSource dataSource,JedisPool jedisPool, String QUEUE_NAME) {
        this.channel = channel;
        this.dataSource = dataSource;
        this.QUEUE_NAME = QUEUE_NAME;
        this.jedisPool = jedisPool;
    }

    public void run() {
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            //Extract URI and process elements
            String URI = new String(delivery.getBody(), "UTF-8");
            String[] uriElements = URI.split("/");
            Integer album = Integer.valueOf(uriElements[uriElements.length-1]);
            System.out.println(URI);
            String sentiment = uriElements[uriElements.length-2];
            String query = sentiment.equalsIgnoreCase("like") ?
                    "UPDATE albums SET likes = likes + 1 WHERE album_id = ?":
                    "UPDATE albums SET dislikes = dislikes + 1 WHERE album_id = ?";
            if (updateDB(query,album)) {
                searchDB(album);
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

    private void searchDB(int album) {
        String query = "SELECT likes,dislikes FROM albums where album_id = ?";
        try (java.sql.Connection connection= dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1,album);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    try (Jedis jedis = jedisPool.getResource()) {
                        jedis.set(String.valueOf(album),JsonUtils.objectToJson(new Sentiment(resultSet.getInt("likes"),resultSet.getInt("dislikes"))));
                        System.out.println("HERE");
                    }
                }
            } catch (Exception e) {e.printStackTrace();}
        } catch (Exception e) {e.printStackTrace();}
    }

    private boolean updateDB(String query, int album) {
        try (java.sql.Connection connection= dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1,album);
                return preparedStatement.executeUpdate() != 0;
            } catch (Exception e) {e.printStackTrace(); return false;}
        } catch (Exception e) {e.printStackTrace();return false;}
    }

}