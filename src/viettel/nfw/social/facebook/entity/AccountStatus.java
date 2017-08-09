package viettel.nfw.social.facebook.entity;

/**
 *
 * @author duongth5
 */
public enum AccountStatus {

    LOGIN_OK(1000, "Login OK"),
    LOGIN_FAILED_UNKNOWN(1001, "Login Failed: unknown"),
    LOGIN_FAILED_POST_PARAM_NULL(1002, "Login Failed: post params is null"),
    LOGIN_FAILED_NOT_RETURN_302(1003, "Login Failed: not return 302"),
    LOGIN_FAILED_NOT_VERIFY_MOBILE(1004, "Login Failed: account have not verified mobile yet"),
    LOGIN_FAILED_VERIFY_IDENTITY(1005, "Login Failed: please go to web and complete some security check, this account may be still alive"),
    LOGIN_FAILED_ACCOUNT_LOCK(1006, "Login Failed: this account has been locked by PHOTO ID or FRIENDS CHECK"),
    LOGIN_FAILED_ACCOUNT_LOCK_UNKNOWN(1007, "Login Failed: account locked, UNNORMAL CASE, check log for details"),
    ACTIVE(2000, "Account still active"),
    KICKOUT_LEVEL_1(2001, "Kickout level 1: Cannot view url, redirect to home"),
    KICKOUT_LEVEL_2(2002, "Kickout level 2: Cannot view url, Facebook force to logout"),
    KICKOUT_UNKNOWN(2003, "Unknown kickout"),
    DO_JOB_OK(3000, "Do job OK"),
    DO_JOB_FAILED(3001, "Do job FAILED"),
    ERROR_UNKNOWN(4001, "Unknown what the heck is going on?!");

    private final int code;
    private final String description;

    private AccountStatus(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code + ": " + description;
    }

}
