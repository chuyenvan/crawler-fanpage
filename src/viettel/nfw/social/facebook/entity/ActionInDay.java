package viettel.nfw.social.facebook.entity;

import java.util.Date;

/**
 *
 * @author duongth5
 */
public class ActionInDay {

    public Activity activity;
    public long startTime;

    public ActionInDay() {
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(new Date(startTime).toString());
        sb.append(" - ");
        sb.append(activity.name());
        return sb.toString();
    }

}
