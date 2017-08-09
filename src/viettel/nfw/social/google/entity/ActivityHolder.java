package viettel.nfw.social.google.entity;

import java.util.List;
import viettel.nfw.social.model.googleplus.Activity;
import viettel.nfw.social.model.googleplus.Comment;

/**
 *
 * @author duongth5
 */
public class ActivityHolder {

    private Activity activityInfo;

    private List<Comment> comments;

    public ActivityHolder() {
    }

    public Activity getActivityInfo() {
        return activityInfo;
    }

    public void setActivityInfo(Activity activityInfo) {
        this.activityInfo = activityInfo;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

}
