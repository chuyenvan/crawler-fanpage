package viettel.nfw.social.google.entity;

import java.util.List;
import viettel.nfw.social.model.googleplus.Activity;
import viettel.nfw.social.model.googleplus.Comment;

/**
 *
 * @author duongth5
 */
public class TimelineWrapper {

    private List<Activity> activities;

    private List<Comment> comments;

    public TimelineWrapper() {
    }

    public List<Activity> getActivities() {
        return activities;
    }

    public void setActivities(List<Activity> activities) {
        this.activities = activities;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }

}
