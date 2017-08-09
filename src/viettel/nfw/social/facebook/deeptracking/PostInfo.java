package viettel.nfw.social.facebook.deeptracking;

/**
 * This class for JSON Object Encode/ Decoder so Don't final its fields
 *
 * @author chuyennd
 */
public class PostInfo {

    public String url;
    public long time;
    public String content;
    public long totalComment;
    public long totalLike;

    public PostInfo() {
    }

    public PostInfo(String url, long time, String content, long totalComment, long totalLike) {
        this.url = url;
        this.time = time;
        this.content = content;
        this.totalComment = totalComment;
        this.totalLike = totalLike;
    }

    @Override
    public String toString() {
        return url + " " + time + " " + content + " " + totalComment + " " + totalLike;
    }

}
