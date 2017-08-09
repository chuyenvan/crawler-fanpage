package viettel.nfw.social.twitter.utils;

public class TwitterURL {

    public static final String BASE_URL = "https://mobile.twitter.com/";
    public static final String SESSION_URL = "https://mobile.twitter.com/sessions";
    public static final String SESSION_NEW_URL = "https://mobile.twitter.com/session/new";
    public static final String API_PROFILE = "https://mobile.twitter.com/api/profile";
    public static final String API_USER_TIMELINE = "https://mobile.twitter.com/api/user_timeline";
    public static final String API_STATUS_ACTIVITY = "https://mobile.twitter.com/api/status_activity";
    public static final String API_USERS = "https://mobile.twitter.com/api/users";
    public static final String API_FOLLOWINGS = "https://mobile.twitter.com/api/friends";
    public static final String HOST_MOBILE = "mobile.twitter.com";

    // built-in format
    /**
     * Referer profile url of user
     *
     * baseUrl + / + screenName
     */
    public static final String FORMAT_PROFILE = BASE_URL + "%s";

    /**
     * Referer url for get all tweets of this profile
     *
     * profileUrl + /tweets
     */
    public static final String FORMAT_TWEETS = "%s/tweets";

    /**
     * Referer url for get details of status
     *
     * profileUrl + /status/ + statusId
     */
    public static final String FORMAT_STATUS_ACTIVITY = "%s/status/%s";

    /**
     * Referer url for get people who retweet this status
     *
     * profileUrl + /status/ + statusId + /retweets
     */
    public static final String FORMAT_STATUS_ACTIVITY_RETWEETS = "%s/status/%s/retweets";

    /**
     * Referer url for get people who favorite this status
     *
     * profileUrl + /status/ + statusId + /favorites
     */
    public static final String FORMAT_STATUS_ACTIVITY_FAVORITES = "%s/status/%s/favorites";

    /**
     * Referer url for get people who followings
     *
     * profileUrl + /status/ + statusId + /favorites
     */
    public static final String FORMAT_FOLLOWINGS = "%s/followings";
}
