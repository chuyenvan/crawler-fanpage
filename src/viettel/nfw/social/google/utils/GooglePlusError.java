package viettel.nfw.social.google.utils;

public enum GooglePlusError {

    CRAWL_PROFILE_OK(2000, GooglePlusMessage.CRAWL_PROFILE_OK),
    LOGIN_FAILED(2001, GooglePlusMessage.LOGIN_FAILED),
    LOGIN_POST_AUTHEN_NOT_RETURN_302(2002, GooglePlusMessage.LOGIN_POST_AUTHEN_NOT_RETURN_302),
    CANNOT_DECTECT_PROFILE(2003, GooglePlusMessage.CANNOT_DETECT_PROFILE),
    CRAWL_PROFILE_FAILED(2004, GooglePlusMessage.CRAWL_PROFILE_FAILED),
    PARSER_ERROR_CURRENT_PROFILE_ID_EMPTY(2005, GooglePlusMessage.PARSER_ERROR_CURRENT_PROFILE_ID_EMPTY),
    LOGIN_SUCCESS(2200, GooglePlusMessage.LOGIN_SUCCESS);

    private final int code;
    private final String description;

    private GooglePlusError(int code, String description) {
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
