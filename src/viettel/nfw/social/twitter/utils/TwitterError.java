package viettel.nfw.social.twitter.utils;

/**
 *
 * @author duongth5
 */
public enum TwitterError {

    CRAWL_PROFILE_OK(3000, TwitterMessage.CRAWL_PROFILE_OK),
    LOGIN_FAILED(3001, TwitterMessage.LOGIN_FAILED),
    LOGIN_POST_AUTHEN_NOT_RETURN_302(3002, TwitterMessage.LOGIN_POST_AUTHEN_NOT_RETURN_302),
    LOGIN_CANNOT_GET_AUTHEN_TOKEN(3003, TwitterMessage.LOGIN_CANNOT_GET_AUTHEN_TOKEN),
    CRAWL_PROFILE_FAILED(3004, TwitterMessage.CRAWL_PROFILE_FAILED),
    LOGIN_OK(3300, TwitterMessage.LOGIN_SUCCESS);

    private final int code;
    private final String description;

    private TwitterError(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public int getCode() {
        return code;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Enum#toString()
     */
    @Override
    public String toString() {
        return code + ": " + description;
    }
}
