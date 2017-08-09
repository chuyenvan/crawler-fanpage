package viettel.nfw.social.twitter.crawler;

import java.io.File;
import java.io.IOException;
import java.net.CookieManager;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import net.arnx.jsonic.JSON;
import net.arnx.jsonic.JSONException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.google.entity.CrawledResult;
import viettel.nfw.social.model.twitter.TwitterObject;
import viettel.nfw.social.twitter.core.TwitterCrawler;
import viettel.nfw.social.twitter.utils.TwitterError;
import viettel.nfw.social.utils.Funcs;
import vn.itim.detector.LanguageDetector;
import vn.itim.engine.util.FileUtils;

/**
 *
 * @author duongth5
 */
public class TwJob implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(TwJob.class);

    private static final String TW_MOBILE_HOST = "mobile.twitter.com";

    private final String username;
    private final String password;
    private final String useragent;
    private boolean isBan;
    private LanguageDetector languageDetector;

    public LanguageDetector getLanguageDetector() {
        return languageDetector;
    }

    public void setLanguageDetector(LanguageDetector languageDetector) {
        this.languageDetector = languageDetector;
    }

    public TwJob(String username, String password, String useragent) {
        this.username = username;
        this.password = password;
        this.useragent = useragent;
        isBan = false;
    }

    @Override
    public void run() {
        Thread.currentThread().setName("Thread-" + username);
        int countTurn = 0;
        while (true) {
            long startTime = System.currentTimeMillis();
            long expectEndTime = startTime + randCrawlTime();
            LOG.info("Account {} does turn {} - startTime: {} - expectEndTime: {}",
                    new Object[]{username, countTurn, new Date(startTime).toString(), new Date(expectEndTime).toString()});
            process(username, password, useragent, startTime, expectEndTime);
            if (isBan) {
                LOG.info("Account {} is banned. Remove from list", username);
                try {
                    // RunTwitterCrawler.getActiveAccounts().remove(username);
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                break;
            }
            countTurn++;
            long sleepTime = randSleepTimeBetweenLogins();
            String sleepTimeStr = Funcs.toReadableString(sleepTime);
            Date expectReStartTime = new Date(expectEndTime + sleepTime);
            LOG.info("Account {} will sleep in {}. Will start at {}",
                    new Object[]{username, sleepTimeStr, expectReStartTime.toString()});
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    /**
     *
     * @param username
     * @param password
     * @param numberWillCrawl
     * @param userAgent
     */
    private void process(String username, String password, String userAgent, long startTime, long expectEndTime) {

        List<String> needCrawlUrls = new ArrayList<>();
        CookieManager cookieManager = new CookieManager();
        TwitterCrawler crawler = new TwitterCrawler(username, password, null, cookieManager);
        // crawler.setLanguageDetector(languageDetector);
        LOG.info("Account {} start login", username);
        TwitterError twError = crawler.login();
        if (twError == null) {
            isBan = true;
            ServiceOutlinks.sendError(username, "TWITTER LOGIN return error NULL");
            return;
        }
        // check if login failed
        int errorCode = twError.getCode();
        if (!isLoginOK(errorCode)) {
            isBan = true;
            ServiceOutlinks.sendError(username, twError.toString());
            return;
        }
        // if login ok, continue this rest
        int count = 0;
        while (true) {
            // try 5 time to get urls
            for (int i = 0; i < 5; i++) {
                String queryUrl = ServiceOutlinks.getNextUrl(TW_MOBILE_HOST, username);
                if (StringUtils.isNotEmpty(queryUrl)) {
                    needCrawlUrls.add(queryUrl);
                    break;
                }
            }

            if (needCrawlUrls.size() > 0) {
                // sleep time between 2 profiles
                try {
                    Thread.sleep(randSleepTimeBetweenProfiles());
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                // get url to crawl
                String crawlUrl = needCrawlUrls.remove(0);
                LOG.info("Account {} crawling {} ...", username, crawlUrl);
                crawler.crawl(crawlUrl);
                count++;

                CrawledResult cr = crawler.getCrawledResult();
                TwitterObject retObject = (TwitterObject) cr.getCrawledProfile();
                try {
                    if (retObject != null) {
                        LOG.info("JSON - {}", JSON.encode(retObject));

                        int indexOfLastSplash = crawlUrl.lastIndexOf("/");
                        String gpId = crawlUrl.substring(indexOfLastSplash + 1);
                        if (StringUtils.isEmpty(gpId)) {
                            gpId = "df" + String.valueOf(System.currentTimeMillis());
                        }
                        String filename = "storage/twitter/" + gpId + "_" + String.valueOf(System.currentTimeMillis()) + ".two";
                        FileUtils.writeObject2File(new File(filename), retObject, false);
                    }
                } catch (IOException | JSONException ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                try {
                    // get out links
                    if (!cr.getFoundProfileUrls().isEmpty()) {
                        Set<String> outlinks = cr.getFoundProfileUrls();
                        // send outlinks to service
                        for (String outlink : outlinks) {
                            ServiceOutlinks.addOutLink(username, outlink, outlink);
							// TODO remove it after demo
							ServiceOutlinks.addCrawledUrl(username, outlink);
                        }
                    }

                    LOG.info("CRAWLED URL - {}", crawlUrl);
                    // add crawled url to service
                    ServiceOutlinks.addCrawledUrl(username, crawlUrl);
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                // check end crawl time to logout
                long nowTime = System.currentTimeMillis();
                if (nowTime > expectEndTime) {
                    // should logout
                    LOG.info("Total profile crawled {}", count);
                    LOG.info("Account {} start log out here {}", username, crawlUrl);
                    break;
                }
            } else {
                LOG.warn("Account {} empty outlinks to crawl!", username);
                // sleep time between 2 profiles
                try {
                    Thread.sleep(10 * 60 * 1000);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private static int randCrawlTime() {
//        int[] values = Funcs.getMinMaxFromProperty("gp.crawltime.hour", 3, 5);
        int minTime = 3 * 60 * 60 * 1000;
        int maxTime = 5 * 60 * 60 * 1000;
        int ret = Funcs.randInt(minTime, maxTime);
        return ret;
    }

    private static int randSleepTimeBetweenLogins() {
//        int[] values = Funcs.getMinMaxFromProperty("gp.sleep.login.hour", 1, 3);
        int minTime = 1 * 60 * 60 * 1000;
        int maxTime = 3 * 60 * 60 * 1000;
        int ret = Funcs.randInt(minTime, maxTime);
        return ret;
    }

    private static int randSleepTimeBetweenProfiles() {
//        int[] values = Funcs.getMinMaxFromProperty("gp.sleep.profiles.seconds", 30, 60);
        int minTime = 30 * 1000;
        int maxTime = 60 * 1000;
        int ret = Funcs.randInt(minTime, maxTime);
        return ret;
    }

    private static boolean isLoginOK(int code) {
        return !(code == 3001 || code == 3002 || code == 3003 || code == 3004);
    }
}
