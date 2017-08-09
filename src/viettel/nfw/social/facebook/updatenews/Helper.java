package viettel.nfw.social.facebook.updatenews;

import org.apache.commons.lang3.StringUtils;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectType;
import viettel.nfw.social.facebook.updatenews.graph.entities.SocialType;

/**
 *
 * @author duongth5
 */
public class Helper {

    public static SocialType detectSocialType(String socialTypeStr) {
        SocialType socialType = SocialType.UNDEFINED;
        if (StringUtils.equalsIgnoreCase(socialTypeStr, "facebook")) {
            socialType = SocialType.FACEBOOK;
        } else if (StringUtils.equalsIgnoreCase(socialTypeStr, "google")) {
            socialType = SocialType.GOOGLE;
        } else if (StringUtils.equalsIgnoreCase(socialTypeStr, "twitter")) {
            socialType = SocialType.TWITTER;
        }
        return socialType;
    }

    public static ObjectType detectObjectType(String objectTypeStr) {
        ObjectType objectType = ObjectType.UNDEFINED;
        if (StringUtils.equalsIgnoreCase(objectTypeStr, "group")) {
            objectType = ObjectType.GROUP;
        } else if (StringUtils.equalsIgnoreCase(objectTypeStr, "page")) {
            objectType = ObjectType.PAGE;
        } else if (StringUtils.equalsIgnoreCase(objectTypeStr, "unknown")) {
            objectType = ObjectType.UNKNOWN;
        }
        return objectType;
    }

    // TODO improve this later
    public static long convertTime(String timeLoopStr) {
        long timeInMillis = 0;

        if (StringUtils.equalsIgnoreCase(timeLoopStr, "0h")
                || StringUtils.equalsIgnoreCase(timeLoopStr, "0m")
                || StringUtils.equalsIgnoreCase(timeLoopStr, "0s")
                || StringUtils.equalsIgnoreCase(timeLoopStr, "0")
                || StringUtils.equalsIgnoreCase(timeLoopStr, "oh")) {
            return timeInMillis;
        }

        if (StringUtils.contains(timeLoopStr, "s")) {
            // in seconds
            String secondStr = StringUtils.replace(timeLoopStr, "s", "");
            int num = Integer.parseInt(secondStr);
            timeInMillis = num * 1000;
        } else if (StringUtils.contains(timeLoopStr, "m")) {
            // in minutes
            String minutesStr = StringUtils.replace(timeLoopStr, "m", "");
            int num = Integer.parseInt(minutesStr);
            timeInMillis = num * 60 * 1000;
        } else if (StringUtils.contains(timeLoopStr, "h")) {
            // in hours
            String hourStr = StringUtils.replace(timeLoopStr, "h", "");
            int num = Integer.parseInt(hourStr);
            timeInMillis = num * 60 * 60 * 1000;
        }
        return timeInMillis;
    }
}
