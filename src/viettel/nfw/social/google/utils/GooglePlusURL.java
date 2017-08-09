package viettel.nfw.social.google.utils;

public class GooglePlusURL {

    public static final String LOGIN_AUTH_URL = "https://accounts.google.com/ServiceLoginAuth";
    public static final String MANAGE_ACCOUNT = "https://accounts.google.com/ManageAccount";
    public static final String HOST_ACCOUNTS_GOOGLE = "accounts.google.com";
    public static final String PATH_CHECK_COOKIE = "/CheckCookie";
    public static final String BASE_URL = "https://plus.google.com";
    public static final String MOBILE_PATH = "/app/basic/";

    // built-in format
    public static final String FORMAT_PROFILE = BASE_URL + MOBILE_PATH + "%s";

    public static final String FORMAT_ABOUT = BASE_URL + MOBILE_PATH + "%s/about";

    public static final String FORMAT_POSTS = BASE_URL + MOBILE_PATH + "%s/posts";

    public static final String FORMAT_STREAM = BASE_URL + MOBILE_PATH + "stream/%s";

    public static final String FORMAT_STREAM_ACTIVITES = BASE_URL + MOBILE_PATH + "stream/%s/activities";

    public static final String FORMAT_COMMENT = BASE_URL + MOBILE_PATH + "comment/%s";

}
