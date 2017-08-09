package viettel.nfw.social.facebook.deeptracking;

import java.util.List;

/**
 *
 * @author duongth5
 */
public class ObjectDataSendCrawler {

    public String type;
    public List<String> listProfile;

    public ObjectDataSendCrawler() {
    }

    public ObjectDataSendCrawler(String type, List<String> listProfile) {
        this.type = type;
        this.listProfile = listProfile;
    }

}
