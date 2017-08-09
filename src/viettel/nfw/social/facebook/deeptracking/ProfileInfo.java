package viettel.nfw.social.facebook.deeptracking;

import java.util.List;

/**
 * This class for JSON Object Encode/ Decoder so Don't final its fields
 *
 * @author chuyennd
 */
public class ProfileInfo {

    public String profile;
    public List<PostInfo> listPost;
    public long timeCrawled;

    public ProfileInfo() {
    }

    public ProfileInfo(String profile, List<PostInfo> listPost, long timeCrawled) {
        this.profile = profile;
        this.listPost = listPost;
        this.timeCrawled = timeCrawled;
    }

    @Override
    public String toString() {
        return profile + " " + listPost + " " + timeCrawled;
    }

}
