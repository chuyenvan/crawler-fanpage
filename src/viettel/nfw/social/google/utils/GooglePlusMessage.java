package viettel.nfw.social.google.utils;

public class GooglePlusMessage {

    public static final String LOGIN_SUCCESS = "Google Login - Success";
    public static final String LOGIN_FAILED = "Google Login - Failed OR account have been locked";
    public static final String LOGIN_REDIRECT_URL = "Google Plus Login - Redirect to {}";
    public static final String LOGIN_POST_AUTHEN_NOT_RETURN_302 = "Post request to authentication is not return 302 code";
    public static final String PROCESS_LOGIN_TOTAL_TIME = "Process login to Google in {} ms";

    public static final String CRAWL_PROFILE_START = "Starting crawl profile: {}";
    public static final String CRAWL_PROFILE_OK = "Crawl profile ok";
    public static final String CRAWL_PROFILE_FAILED = "Error crawling this profile";
    public static final String CANNOT_DETECT_PROFILE = "Cannot detect type of this profile";

    public static final String PARSER_ERROR_CURRENT_PROFILE_ID_EMPTY = "Parse Error: Id of current crawled profile is empty";

    public static final String PROCESS_PARSER_TOTAL_TIME = "Done parse profile. URL's crawled: {} in {} ms";

    public static final String PARSER_ERROR_QUERY_RULE = "Parse Error: problem with query rule {} for {}";
}
