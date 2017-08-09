package viettel.nfw.social.facebook.updatenews.prototype.planner;

/**
 *
 * @author duongth5
 */
public class RemoteDownloaderProperties {

    public String host;
    public int port;

    public RemoteDownloaderProperties(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        return "host=" + host + ", port=" + port;
    }

}
