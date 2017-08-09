package viettel.nfw.social.facebook.updatenews.prototype.planner;

/**
 *
 * @author duongth5
 */
public interface RemoteDownloader {

    public boolean isAlive();

    boolean sendRequest(String value);

    boolean check();

    void disconnect();
}
