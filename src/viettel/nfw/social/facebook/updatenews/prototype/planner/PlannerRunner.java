package viettel.nfw.social.facebook.updatenews.prototype.planner;

import java.util.ArrayList;
import java.util.List;

/**
 * Planner Client
 *
 * @author duongth5
 */
public class PlannerRunner {

    public static void main(String[] args) {
        List<RemoteDownloaderProperties> dlProperties = new ArrayList<>();
        dlProperties.add(new RemoteDownloaderProperties("127.0.0.1", 1125));
        dlProperties.add(new RemoteDownloaderProperties("127.0.0.1", 1126));
        dlProperties.add(new RemoteDownloaderProperties("127.0.0.1", 1127));

        DownloadersManager downloadersManager = DownloadersManager.createWithDownloadersProperties(dlProperties);
        downloadersManager.checkCrawlers();
    }
}
