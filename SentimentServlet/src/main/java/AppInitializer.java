import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.dbcp2.BasicDataSource;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

@WebListener
public class AppInitializer implements ServletContextListener {
    private static final String databaseURL = "albumdb.c3hm6sf3alr0.us-west-2.rds.amazonaws.com";
    private BasicDataSource dataPool;

    private static final String rabbitMQURL = "54.185.22.86";
    private static final String QUEUE_NAME = "Sentiment";
    private static final int RMQ_POOL_SIZE = 10;
    private ConnectionFactory rmqConnectionFactory ;
    private Connection connection;
    private RabbitMQChannelPool rmqChannelPool;

    private static final String redisURL = "35.89.145.83";
    private static final int redisPort = 6379;
    private JedisPool jedisPool;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        try {
            ServletContext servletContext = sce.getServletContext();

            dataPool = setupDataSource();
            // Example: Set an attribute to be used by servlets
            servletContext.setAttribute("dataPool", dataPool);
            dataPool = setupDataSource();

            rmqConnectionFactory = createRabbitMQConnectionFactory();
            connection = rmqConnectionFactory.newConnection();
            rmqChannelPool = new RabbitMQChannelPool(connection,RMQ_POOL_SIZE,QUEUE_NAME);
            servletContext.setAttribute("rmqChannelPool", rmqChannelPool);
            servletContext.setAttribute("QUEUE_NAME",QUEUE_NAME);

            jedisPool = setupJedisPool();
            servletContext.setAttribute("jedisPool",jedisPool);

        } catch (Exception e) {
            System.out.println("Failed startup");
        }

    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Perform cleanup tasks here
        try {
            dataPool.close();
            rmqChannelPool.close();
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }

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

    private JedisPool setupJedisPool() {
        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(30);
        jedisPoolConfig.setMaxIdle(10);
        jedisPoolConfig.setMinIdle(5);
        jedisPoolConfig.setMaxWaitMillis(3000);
        return new JedisPool(jedisPoolConfig,redisURL,redisPort);
    }

    private ConnectionFactory createRabbitMQConnectionFactory() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitMQURL);
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");
        return factory;
    }
}