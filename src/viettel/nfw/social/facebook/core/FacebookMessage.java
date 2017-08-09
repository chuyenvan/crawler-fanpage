package viettel.nfw.social.facebook.core;

/**
 * Facebook Message Constants
 *
 * @author duongth5
 * @version 1.0
 * @since 1.0
 */
public class FacebookMessage {

    public static final String LOGIN_SUCCESS = "Facebook Login - Success";
    public static final String LOGIN_FAILED = "Facebook Login - Failed";
    public static final String ACCOUNT_LOCK = "Facebook Login - 100 percent that account have been lock";
    public static final String LOGIN_PHONE_ARQUIRE = "Facebook Login - Account have not verify mobile number";
    public static final String LOGIN_REDIRECT_URL = "Facebook Login - Redirect to {}";
    public static final String LOGIN_POST_AUTHEN_NOT_RETURN_302 = "Post request to authentication is not return 302 code";
    public static final String PROCESS_LOGIN_TOTAL_TIME = "Process login to facebook in {} ms";

    public static final String CRAWL_PROFILE_START = "Starting crawl profile: {}";
    public static final String CRAWL_PROFILE_OK = "Crawl profile ok";
    public static final String CRAWL_PROFILE_FAILED = "Error crawling this profile";
    public static final String CANNOT_DETECT_PROFILE = "Cannot detect type of this profile";

    public static final String PARSER_ERROR_CURRENT_PROFILE_ID_EMPTY = "Parse Error: Id of current crawled profile is empty";

    public static final String PROCESS_PARSER_TOTAL_TIME = "Done parse profile {}. URL's crawled: {} in {} ms(about {})";

    public static final String PARSER_ERROR_QUERY_RULE = "Parse Error: problem with query rule {} for {}";
}
