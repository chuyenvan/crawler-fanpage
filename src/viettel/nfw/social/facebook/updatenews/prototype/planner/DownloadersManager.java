package viettel.nfw.social.facebook.updatenews.prototype.planner;

import io.netty.channel.nio.NioEventLoopGroup;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class DownloadersManager {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadersManager.class);

    private final Set<RemoteDownloader> downloaders;
    private final NioEventLoopGroup eventLoopGroup;

    public static DownloadersManager createWithDownloadersProperties(
        Collection<RemoteDownloaderProperties> downloaderProperties) {
        Map<RemoteDownloaderProperties, RemoteDownloader> crawlerMap = new HashMap<>();
        NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup(Math.max(1, crawlerMap.size() / 2));
        for (RemoteDownloaderProperties properties : downloaderProperties) {
            RemoteDownloader rd = new PersistentRemoteDownloader(
                properties.host, properties.port, eventLoopGroup
            );
            LOG.info("Remote downloader configured: {}", properties);
            crawlerMap.put(properties, rd);
        }
        if (crawlerMap.isEmpty()) {
            throw new RuntimeException("Empty list of crawlers.");
        }
        return new DownloadersManager(crawlerMap, eventLoopGroup);
    }

    DownloadersManager(
        Map<RemoteDownloaderProperties, RemoteDownloader> dlProperties,
        NioEventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        this.downloaders = new HashSet<>(dlProperties.values());
    }

    public void checkCrawlers() {
        for (RemoteDownloader dl : downloaders) {
            try {
                dl.check();
            } catch (Exception e) {
                LOG.error("{} while checking {}", e, dl);
                throw e;
            }
        }
    }

    public void shutdownConnections() {
        LOG.debug("Shutdown event loop");
        try {
            eventLoopGroup.shutdownGracefully().sync();
        } catch (InterruptedException e) {
            LOG.error(e.toString());
            throw new RuntimeException(e);
        }
    }
}
