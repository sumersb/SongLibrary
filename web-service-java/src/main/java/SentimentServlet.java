import javax.servlet.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import javax.sql.DataSource;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.commons.dbcp2.BasicDataSource;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;


@WebServlet(name = "SentimentServlet", urlPatterns = "/review/*")
public class SentimentServlet extends HttpServlet {

    private static final String QUEUE_NAME = "Sentiment";
    private static final String rabbitMQURL = "35.89.7.253";
    private static final int poolSize = 10;
    private ConnectionFactory connectionFactory;
    private Connection connection;
    private RabbitMQChannelPool channelPool;

    private static final String evenRedisHost = "evencache.oidyud.clustercfg.usw2.cache.amazonaws.com";
    private static final int redisPort = 6379;
    private static final int redisPoolSize = 100;
    private static JedisPool evenJedisPool;

    private String evenDBURL = "albumdb.c3hm6sf3alr0.us-west-2.rds.amazonaws.com";
    private BasicDataSource evenDataSource;




    public SentimentServlet() throws ServletException, IOException, TimeoutException {
        super.init();
        connectionFactory = createRabbitMQConnectionFactory();
        connection = connectionFactory.newConnection();
        channelPool = new RabbitMQChannelPool(connection, poolSize,QUEUE_NAME);
        evenJedisPool = createJedisPool(evenRedisHost);
        evenDataSource = setupDataSource(evenDBURL);
    }
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        //Extract URI from doPost request
        String URI = req.getRequestURI();

        //Validate URI to make sure correct format
        if (URI == null || URI.isEmpty() || !validatePostURI(URI)) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            res.getWriter().write(JsonUtils.objectToJson(new ErrorMsg("Invalid URL")));
            return;
        }

        Channel channel = null;
        try {
            //Borrow channel from pool
            channel = channelPool.borrowChannel();
            try {
                channel.basicPublish("", QUEUE_NAME, null, URI.getBytes());
            } catch (Exception e) {
                res.getWriter().write(JsonUtils.objectToJson(new ErrorMsg(e.getMessage())));
                System.out.println("Channel unable to be published to");
            }

            res.setStatus(HttpServletResponse.SC_CREATED);
        } catch (Exception e) {
            //Checks for errors if error occurs
            System.out.println("Unable to get pool or borrow channel from pool");
            System.out.println(e.getMessage());
            res.getWriter().write(JsonUtils.objectToJson(new ErrorMsg(e.getMessage())));
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } finally {
            //Returns channel to pool
            if (channel != null) {
                channelPool.releaseChannel(channel);
            }
        }
    }


    boolean validatePostURI(String URI) {
        String regex = ".*review/(like|dislike)/\\d+$";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(URI);
        return matcher.matches();
    }

    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        //Extract URI from doPost request
        String URI = req.getRequestURI();

        //Validate URI to make sure correct format
        if (URI == null || URI.isEmpty() || !validateGetURI(URI)) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            res.getWriter().write(JsonUtils.objectToJson(new ErrorMsg("Invalid URL")));
            return;
        }

        String[] splitURI = URI.split("/");
        String album_id = splitURI[splitURI.length-1];

        try {
            //Borrow channel from pool
            if (Integer.parseInt(album_id) % 2 == 0) {
                queryRedisAndDB(res, evenJedisPool, evenDataSource, album_id);
                return;
            }
            queryRedisAndDB(res,evenJedisPool,evenDataSource,album_id);
            return;


        } catch (Exception e) {
            //Checks for errors if error occurs
            System.out.println("Unable to query db and datasource");
            System.out.println(e.getMessage());
            res.getWriter().write(JsonUtils.objectToJson(new ErrorMsg(e.getMessage())));
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } 
    }

    private void queryRedisAndDB(HttpServletResponse res, JedisPool jedisPool, BasicDataSource dataSource, String album_id) throws IOException, SQLException {
        if (Integer.parseInt(album_id) % 2 == 0) {
            Sentiment value = searchRedis(jedisPool,album_id);
            if (value != null) {
                respondSentiment(res,value);
                return;
            }
            Sentiment values = searchDB(dataSource,album_id);
            if (value != null) {
                respondSentiment(res,value);
                return;
            }
            res.getWriter().write(JsonUtils.objectToJson(new ErrorMsg("Album not found")));
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    private Sentiment searchRedis(JedisPool jedisPool, String album_id) {
        Sentiment result = null;
        try (Jedis jedis = jedisPool.getResource()) {
            result = JsonUtils.jsonToObject(jedis.get(album_id),Sentiment.class);
        } catch (Exception e) {
            System.out.println("Failed at jedis query");
            System.out.println(e.getMessage());
        }
        return result;
    }

    private void respondSentiment(HttpServletResponse res, Sentiment sentiment) throws IOException {
        res.getWriter().write(JsonUtils.objectToJson(sentiment));
        res.setStatus(HttpServletResponse.SC_OK);
    }

    private Sentiment searchDB(BasicDataSource dataSource,String album_id) throws SQLException {
        String query = "SELECT title, artist FROM albums WHERE album_id = ?";
        try (java.sql.Connection con = dataSource.getConnection();
            PreparedStatement preparedStatement = con.prepareStatement(query)) {
            preparedStatement.setInt(1,Integer.parseInt(album_id));
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return new Sentiment(resultSet.getInt("likes"),resultSet.getInt("dislikes"));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    boolean validateGetURI(String URI) {
        String regex = ".*review/\\d+$";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(URI);
        return matcher.matches();
    }

    private ConnectionFactory createRabbitMQConnectionFactory() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitMQURL);
        factory.setPort(5672);
        factory.setUsername("guest");
        factory.setPassword("guest");
        return factory;
    }

    private JedisPool createJedisPool(String redisHost) {
        JedisPoolConfig jpc = new JedisPoolConfig();
        jpc.setMaxTotal(redisPoolSize);
        jpc.setMinIdle(10);
        jpc.setMaxIdle(50);
        return new JedisPool(jpc, redisHost, redisPort);
    }

    private static BasicDataSource setupDataSource(String dbURL) {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://"+dbURL+":3306/album_info");
        dataSource.setUsername("root");
        dataSource.setPassword("password");

        // Optionally, you can configure additional properties, such as the initial size and max total connections
        dataSource.setInitialSize(5); // Set the initial number of connections
        dataSource.setMaxTotal(18);   // Set the maximum number of connections

        return dataSource;
    }

}
