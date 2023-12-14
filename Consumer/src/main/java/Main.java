import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import org.apache.commons.dbcp2.BasicDataSource;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

public class Main {
    private static final String databaseURL = "albumdb.c3hm6sf3alr0.us-west-2.rds.amazonaws.com";
    private static final String rabbitMQURL = "35.89.7.253";
    private static final String QUEUE_NAME = "Sentiment";
    // After tedious testing I realized 74 is the ideal amount
    private static final int consumerCount = 74;

    public static void main(String[] args) throws IOException, TimeoutException {
        ConnectionFactory factory = generateFactory();
        BasicDataSource dataSource = setupDataSource();
        Connection connection = factory.newConnection();
        Thread[] array = new Thread[consumerCount];
        for (int i = 0; i < consumerCount; i++) {
            Channel channel = connection.createChannel();
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            array[i] = new ConsumerThread(channel, dataSource, QUEUE_NAME);
            array[i].start();
        }
    }
    private static ConnectionFactory generateFactory() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitMQURL);
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");
        return factory;
    }

    private static BasicDataSource setupDataSource() {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://"+databaseURL+":3306/album_info");
        dataSource.setUsername("root");
        dataSource.setPassword("password");

        // Optionally, you can configure additional properties, such as the initial size and max total connections
        dataSource.setInitialSize(5); // Set the initial number of connections
        dataSource.setMaxTotal(18);   // Set the maximum number of connections
        return dataSource;
    }
}
