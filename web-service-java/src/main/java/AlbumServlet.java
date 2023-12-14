import javax.servlet.*;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.*;

import org.apache.commons.dbcp2.BasicDataSource;

@WebServlet(name = "AlbumServlet", urlPatterns = "/albums/*")
@MultipartConfig(fileSizeThreshold = 1024 * 1024,
                maxFileSize = 1024 * 1024,
maxRequestSize = 1024 * 1024)
public class AlbumServlet extends HttpServlet {

    private static BasicDataSource dataPool;

    public AlbumServlet() throws ServletException {
        super.init();
        dataPool = setupDataSource();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("text/plain");
        String urlPath = req.getRequestURI();

        if (urlPath == null || urlPath.isEmpty() || !isGetRequestPathValid(urlPath)) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            res.getWriter().write(JsonUtils.objectToJson(new ErrorMsg("Invalid URL")));
            return;
        }

        if (urlPath.endsWith("/")) {
            urlPath=urlPath.substring(0,urlPath.length()-1);
        }
        String[] urlParts = urlPath.split("/");

        searchAlbum(res,urlParts[urlParts.length-1]);
    }

    public boolean isGetRequestPathValid(String urlPath) {
        String regex = ".*albums/[^/]+/?$";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(urlPath);
        return  matcher.matches();
    }

    public void searchAlbum(HttpServletResponse res, String albumID) throws IOException {
        try (Connection connection = dataPool.getConnection()){
            String query = "SELECT * FROM albums WHERE album_id = ?";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1,albumID);
                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (!resultSet.next()) {
                        handleException(res, "No album found", HttpServletResponse.SC_NOT_FOUND);
                        return;
                    }
                    processResultSet(res, resultSet);
                } catch (Exception e) {
                    handleException(res,e.getMessage(),HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                }
            } catch (Exception e) {
                handleException(res, e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            handleException(res, e.getMessage(), HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void processResultSet(HttpServletResponse res, ResultSet rs) throws IOException {
        try {
            AlbumInfo albumInfo = new AlbumInfo(
                    rs.getString("artist"),
                    rs.getString("title"),
                    rs.getString("year")
            );
            res.setContentType("application/json");
            res.getWriter().write(JsonUtils.objectToJson(albumInfo));
            res.setStatus(HttpServletResponse.SC_OK);
        } catch (Exception e) {
            handleException(res, e.getMessage(),HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        res.setContentType("application/json");
        String urlPath = req.getRequestURI();

        // check we have a URL!
        if (urlPath == null || urlPath.isEmpty() || !isPostRequestPathValid(urlPath)) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            res.getWriter().write(JsonUtils.objectToJson(new ErrorMsg("Invalid URL")));
            return;
        }

        postAlbum(req,res);

    }

    public boolean isPostRequestPathValid(String urlPath) {
        String regex = ".*albums/?$";
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(urlPath);
        return matcher.matches();
    }

    public void postAlbum(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

        // Extracting JSON payload
        String profileJson = req.getParameter("profile");
        try {
            // Extracting Album Information
            AlbumInfo albumInfo = JsonUtils.jsonToObject(profileJson, AlbumInfo.class);

            // Extracting image
            Part imagePart = req.getPart("image");
            long imageSize = imagePart.getSize();
            byte[] imageData = (imagePart != null && imagePart.getSize() > 0) ? getByteArrayFromPart(imagePart) : null;

            postAlbumtoDB(albumInfo,imageSize, imageData, res);

        } catch (Exception e) {
            handleException(res,e.getMessage(), HttpServletResponse.SC_BAD_REQUEST);
        }

    }

    private void postAlbumtoDB(AlbumInfo albumInfo,long imageSize, byte[] imageData, HttpServletResponse res) throws IOException {

        String query = "INSERT INTO albums (artist, title, year, image_size, image) VALUES (?,?,?,?,?)";
        try ( Connection connection = dataPool.getConnection()) {
            String[] key = {"album_id"};
            try (PreparedStatement preparedStatement = connection.prepareStatement(query, key)) {
                setPrepareStatement(preparedStatement, albumInfo,imageSize, imageData);

                int rowsAffected = preparedStatement.executeUpdate();

                if (rowsAffected == 0) {
                    handleException(res, "Rows effected failure", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    return;
                }

                processGeneratedKeys(preparedStatement, res, imageSize);


            } catch (Exception e) {
                handleException(res,e.getMessage(),HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } catch (Exception e) {
            handleException(res, e.getMessage(),HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
    private void setPrepareStatement(PreparedStatement preparedStatement, AlbumInfo albumInfo,Long imageSize, byte[] imageData) throws SQLException {
        preparedStatement.setString(1, albumInfo.getArtist());
        preparedStatement.setString(2, albumInfo.getTitle());
        preparedStatement.setString(3, albumInfo.getYear());
        preparedStatement.setLong(4, imageSize);
        preparedStatement.setBytes(5,imageData);
    }

    private void processGeneratedKeys(PreparedStatement preparedStatement, HttpServletResponse res, long imageSize) throws IOException, SQLException {
        try (ResultSet rs = preparedStatement.getGeneratedKeys()) {
            if (!rs.next()) {
                handleException(res,"Unable to get rs.next()",HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                return;
            }
            String generatedKey = rs.getString(1);
            writeImageMetaDataToResponse(res, generatedKey, imageSize);
        } catch (SQLException e) {
            handleException(res, "Error processing generated keys",HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    private void handleException(HttpServletResponse res, String errorMessage, int status) throws IOException {
        res.getWriter().write(JsonUtils.objectToJson(new ErrorMsg(errorMessage)));
        res.setStatus(status);
    }

    private void writeImageMetaDataToResponse(HttpServletResponse res, String generatedKey,long imageSize) throws IOException {
        res.getWriter().write(JsonUtils.objectToJson(new ImageMetaData(generatedKey,imageSize)));
        res.setStatus(HttpServletResponse.SC_CREATED);
    }

    private static BasicDataSource setupDataSource() {
        BasicDataSource dataSource = new BasicDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://albumdb.c3hm6sf3alr0.us-west-2.rds.amazonaws.com:3306/album_info");
        dataSource.setUsername("root");
        dataSource.setPassword("password");

        // Optionally, you can configure additional properties, such as the initial size and max total connections
        dataSource.setInitialSize(5); // Set the initial number of connections
        dataSource.setMaxTotal(18);   // Set the maximum number of connections

        return dataSource;
    }

    private byte[] getByteArrayFromPart(Part part) throws IOException {
    InputStream inputStream  = part.getInputStream();
    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

    byte[] buffer = new byte[1024];
    int bytesRead;
    while ((bytesRead = inputStream.read(buffer)) != -1) {
        byteArrayOutputStream.write(buffer, 0, bytesRead);
    }

    return byteArrayOutputStream.toByteArray();

    }
}
