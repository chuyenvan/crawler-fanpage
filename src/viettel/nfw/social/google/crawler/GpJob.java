package viettel.nfw.social.google.crawler;

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
import viettel.nfw.social.model.googleplus.GooglePlusObject;
import vn.itim.detector.LanguageDetector;
import vn.itim.engine.util.FileUtils;
import viettel.nfw.social.google.utils.GooglePlusError;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class GpJob implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(GpJob.class);

    private static final String GP_MOBILE_HOST = "plus.google.com";

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

    public GpJob(String username, String password, String useragent) {
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
                    // Run.getActiveAccounts().remove(username);
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
        GooglePlusCrawler crawler = new GooglePlusCrawler(username, password, null, cookieManager, userAgent);
        crawler.setLanguageDetector(languageDetector);
        LOG.info("Account {} start login", username);
        GooglePlusError gpError = crawler.login();
        if (gpError == null) {
            isBan = true;
            ServiceOutlinks.sendError(username, "GOOGLE LOGIN return error NULL");
            return;
        }
        // check if login failed
        int errorCode = gpError.getCode();
        if (!isLoginOK(errorCode)) {
            isBan = true;
            ServiceOutlinks.sendError(username, gpError.toString());
            return;
        }
        // if login ok, continue this rest
        int count = 0;
        while (true) {
            // try 5 time to get urls
            for (int i = 0; i < 5; i++) {
                String queryUrl = ServiceOutlinks.getNextUrl(GP_MOBILE_HOST, username);
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
                crawler.crawl(crawlUrl, 0);
                count++;

                CrawledResult cr = crawler.getCrawledResult();
                GooglePlusObject retobject = (GooglePlusObject) cr.getCrawledProfile();
                try {
                    if (retobject != null) {
                        LOG.info("JSON - {}", JSON.encode(retobject));

                        int indexOfLastSplash = crawlUrl.lastIndexOf("/");
                        String gpId = crawlUrl.substring(indexOfLastSplash + 1);
                        if (StringUtils.isEmpty(gpId)) {
                            gpId = "df" + String.valueOf(System.currentTimeMillis());
                        }
                        String filename = "storage/google/" + gpId + "_" + String.valueOf(System.currentTimeMillis()) + ".gpo";
                        FileUtils.writeObject2File(new File(filename), retobject, false);
                    }
                } catch (IOException | JSONException ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                try {
                    // get out links
                    if (!cr.getFoundProfileUrls().isEmpty()) {
                        Set<String> outlinks = cr.getFoundProfileUrls();
                        LOG.info("Outlinks size {}", outlinks.size());
                        // send outlinks to service
                        for (String outlink : outlinks) {
                            boolean retAdd = ServiceOutlinks.addOutLink(username, outlink, outlink);
                            LOG.info("Add {} - {}", outlink, retAdd);
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
                    Thread.sleep(60 * 1000);
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
        return !(code == 2001 || code == 2002);
    }
}
