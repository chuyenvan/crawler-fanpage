package viettel.nfw.social.google.crawler;

import java.net.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.google.core.HttpRequest;
import viettel.nfw.social.utils.HttpResponseInfo;

/**
 * Google Plus Crawl Job for get response from URL
 *
 * @author duongth5
 * @version 1.0
 * @since 1.0
 */
public class GooglePlusCrawlerJob {

    /**
     * Logger for GooglePlusCrawlerJob Class
     */
    private static final Logger LOG = LoggerFactory.getLogger(GooglePlusCrawlerJob.class);
    /**
     * Maximum time for retry connection
     */
    private static final int NUM_RETRY_CONNECTION = 5;

    /**
     * Crawl single URL
     *
     * @param url URL to crawl
     * @param http HTTP Request
     * @param proxy Proxy setting
     * @return Response body in String
     */
    public static String crawl(String url, HttpRequest http, Proxy proxy) {
        String result = "";
        try {
            int sleepTime = 2 * 1000;
            LOG.info("random sleep {}", sleepTime);
            Thread.sleep(sleepTime);
        } catch (InterruptedException ex) {
            LOG.error(ex.getMessage(), ex);
            return result;
        }
        for (int i = 0; i < NUM_RETRY_CONNECTION; i++) {
            try {
                HttpResponseInfo response = http.get(url, HttpRequest.SOCIAL_TYPE_GOOGLE_PLUS, proxy);
                result = response.getBody();
                break;
            } catch (Exception ex) {
                LOG.error("Error connecting to URL", ex);
                if (i == NUM_RETRY_CONNECTION - 1) {
                    return result;
                }
            }
        }
        return result;
    }
}
