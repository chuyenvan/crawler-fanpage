package viettel.nfw.automanacc.object;

import java.io.Serializable;
import java.util.List;

/**
 *
 * @author chuyennd
 */
public class ObjectPost implements Serializable {

    private String titlePost;
    private String urlPost;
    private int countPosted;
    private List<String> listAccUsedPost;
    private long timeCreatedPost;

    public String getTitlePost() {
        return titlePost;
    }

    public String getUrlPost() {
        return urlPost;
    }

    public int getCountPosted() {
        return countPosted;
    }

    public List<String> getListAccUsedPost() {
        return listAccUsedPost;
    }

    public long getTimeCreatedPost() {
        return timeCreatedPost;
    }

    public void setTitlePost(String titlePost) {
        this.titlePost = titlePost;
    }

    public void setUrlPost(String urlPost) {
        this.urlPost = urlPost;
    }

    public void setCountPosted(int countPosted) {
        this.countPosted = countPosted;
    }

    public void setListAccUsedPost(List<String> listAccUsedPost) {
        this.listAccUsedPost = listAccUsedPost;
    }

    public void setTimeCreatedPost(long timeCreatedPost) {
        this.timeCreatedPost = timeCreatedPost;
    }

}
