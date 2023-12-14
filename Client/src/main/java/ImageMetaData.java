public class ImageMetaData {
    String albumID;
    long imageSize;

    public ImageMetaData(){}

    public ImageMetaData(String albumID, long imageSize) {
        this.albumID = albumID;
        this.imageSize = imageSize;
    }

    public String getAlbumID() {
        return albumID;
    }

    public void setAlbumID(String albumID) {
        this.albumID = albumID;
    }

    public long getImageSize() {
        return imageSize;
    }

    public void setImageSize(long imageSize) {
        this.imageSize = imageSize;
    }

    @Override
    public String toString() {
        return "ImageMetaData{" +
                "albumID='" + albumID + '\'' +
                ", imageSize=" + imageSize +
                '}';
    }
}

