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
import org.apache.commons.dbcp2.BasicDataSource;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;


@WebServlet(name = "SentimentServlet", urlPatterns = "/review/*")
public class SentimentServlet extends HttpServlet {


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
        RabbitMQChannelPool channelPool = null;
        try {
            channelPool = (RabbitMQChannelPool) getServletContext().getAttribute("rmqChannelPool");
            String QUEUE_NAME = (String) getServletContext().getAttribute("QUEUE_NAME");
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
            if (channelPool!= null && channel != null) {
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
        JedisPool jedisPool = (JedisPool) getServletContext().getAttribute("jedisPool");
        try (Jedis jedis = jedisPool.getResource()) {
            String sentiment = jedis.get(album_id);
            if (sentiment != null) {
                res.getWriter().write(sentiment);
                res.setStatus(HttpServletResponse.SC_OK);
                System.out.println("HERE");
                return;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            BasicDataSource dataSource = (BasicDataSource) getServletContext().getAttribute("dataPool");
            Sentiment sentiment = queryRedisAndDB(dataSource,album_id);
            if (sentiment == null) {
                res.getWriter().write(JsonUtils.objectToJson(new ErrorMsg("No album found")));
                res.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            respondSentiment(res,sentiment);

        } catch (Exception e) {
            //Checks for errors if error occurs
            System.out.println("Unable to query db and datasource");
            System.out.println(e.getMessage());
            res.getWriter().write(JsonUtils.objectToJson(new ErrorMsg(e.getMessage())));
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private Sentiment queryRedisAndDB(BasicDataSource dataSource, String album_id) throws IOException, SQLException {
            Sentiment value = searchDB(dataSource,album_id);
            return value;
    }


    private void respondSentiment(HttpServletResponse res, Sentiment sentiment) throws IOException {
        res.getWriter().write(JsonUtils.objectToJson(sentiment));
        res.setStatus(HttpServletResponse.SC_OK);
    }

    private Sentiment searchDB(BasicDataSource dataSource,String album_id) throws SQLException {
        String query = "SELECT likes, dislikes FROM albums WHERE album_id = ?";
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


}
