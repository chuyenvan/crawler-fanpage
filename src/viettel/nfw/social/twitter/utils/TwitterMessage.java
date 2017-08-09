package viettel.nfw.social.twitter.utils;

/**
 *
 * @author duongth5
 */
public class TwitterMessage {

    public static final String LOGIN_SUCCESS = "Twitter Login - Success";
    public static final String LOGIN_FAILED = "Twitter Login - Failed OR account have been locked";
    public static final String LOGIN_REDIRECT_URL = "Twitter Login - Redirect to {}";
    public static final String LOGIN_POST_AUTHEN_NOT_RETURN_302 = "Post request to authentication is not return 302 code";
    public static final String LOGIN_CANNOT_GET_AUTHEN_TOKEN = "Cannot get authenticity token";

    public static final String PROCESS_LOGIN_TOTAL_TIME = "Process login to Twitter in {} ms";
    public static final String PROCESS_PARSER_TOTAL_TIME = "Done parse profile. URL's crawled in {} ms";

    public static final String CRAWL_PROFILE_OK = "Crawl profile ok";
    public static final String CRAWL_PROFILE_FAILED = "Error crawling this profile";
}
