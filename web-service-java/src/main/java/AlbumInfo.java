import java.util.Objects;

public class AlbumInfo {

    private String artist;
    private String title;
    private String year;

    public AlbumInfo() {}

    public AlbumInfo(String artist, String title, String year) {
        this.artist = artist;
        this.title = title;
        this.year = year;
    }


    @Override
    public String toString() {
        return "AlbumInfo{" +
                "artist='" + artist + '\'' +
                ", title='" + title + '\'' +
                ", year='" + year + '\'' +
                '}';
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getYear() {
        return year;
    }

    public void setYear(String year) {
        this.year = year;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlbumInfo)) return false;
        AlbumInfo albumInfo = (AlbumInfo) o;
        return Objects.equals(artist, albumInfo.artist) && Objects.equals(title, albumInfo.title) && Objects.equals(year, albumInfo.year);
    }

    @Override
    public int hashCode() {
        return Objects.hash(artist, title, year);
    }
}