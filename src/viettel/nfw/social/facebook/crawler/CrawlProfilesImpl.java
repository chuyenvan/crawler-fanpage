package viettel.nfw.social.facebook.crawler;

import java.net.CookieManager;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.FacebookAction;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.utils.DateUtils;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class CrawlProfilesImpl implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(CrawlProfilesImpl.class);

    private static final String FB_MOBILE_HOST = "m.facebook.com";

    private final Account account;
    private final Proxy proxy;
    private boolean isBan;
    private boolean isLogOut;
    private boolean isRestrict;

    public CrawlProfilesImpl(Account account, Proxy proxy) {
        this.account = account;
        this.proxy = proxy;
        isBan = false;
        isLogOut = false;
        isRestrict = false;
    }

    @Override
    public void run() {
        Thread.currentThread().setName(account.getUsername());

        doCrawler();
    }

    private void doCrawler() {
        String username = account.getUsername();

        CookieManager cookieManager = new CookieManager();
        FacebookAction crawler = new FacebookAction(account, cookieManager, proxy);
        long startTime = System.currentTimeMillis();

        // do login
        LOG.info("Account {} start login", username);
        AccountStatus accStatus = crawler.login();
        if (!accStatus.equals(AccountStatus.LOGIN_OK)) {
            isBan = true;
            doActionWhenBan(username, accStatus);
            return;
        }

        // do surf home
        AccountStatus surfMyHomeStatus = crawler.surfMyHome(crawler.getHomeUrl());
        if (surfMyHomeStatus.equals(AccountStatus.KICKOUT_LEVEL_2)) {
            isBan = true;
            doActionWhenBan(username, surfMyHomeStatus);
            return;
        }

        long midnightTime = DateUtils.getMidnight();
        long nextTimeSurfMyHome = startTime + randNextTimeSurfHome();
        List<String> needCrawlUrls = new ArrayList<>();
        int countNotViewProfile = 0;
        // do crawl profile
        while (true) {

            if (countNotViewProfile > 20) {
                isRestrict = true;
                doActionWhenRestrict(username);
                return;
            }

            // try 5 time to get urls
            for (int i = 0; i < 5; i++) {
                String queryUrl = ServiceOutlinks.getNextUrl(FB_MOBILE_HOST, username);
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
                try {
                    String crawlUrl = needCrawlUrls.remove(0);
                    if (StringUtils.isNotEmpty(crawlUrl)) {
                        LOG.info("Account {} crawling {} ...", username, crawlUrl);
                        AccountStatus crawledStatus = crawler.crawl(crawlUrl);
                        LOG.info("Url {} - {}", crawlUrl, crawledStatus.toString());
                        if (crawledStatus.equals(AccountStatus.KICKOUT_LEVEL_2)) {
                            isBan = true;
                            doActionWhenBan(username, crawledStatus);
                            return;
                        } else if (crawledStatus.equals(AccountStatus.KICKOUT_LEVEL_1)) {
                            LOG.info("Account {} cannot view this {}", username, crawlUrl);
                            countNotViewProfile++;
                        } else if (crawledStatus.equals(AccountStatus.KICKOUT_UNKNOWN)) {
                            LOG.info("Account {} is KICKOUT_UNKNOWN");
                            countNotViewProfile = 0;
                        } else {
                            // Account Active
                            countNotViewProfile = 0;
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                // do surf my home again
                try {
                    long nowTime = System.currentTimeMillis();
                    if (nowTime < nextTimeSurfMyHome) {
                        try {
                            // do nothing
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                        continue;
                    }
                    String homeUrl = crawler.getHomeUrl();
                    if (StringUtils.isEmpty(homeUrl)) {
                        homeUrl = "https://m.facebook.com";
                    }
                    AccountStatus surfMyHomeAgainStatus = crawler.surfMyHome(homeUrl);
                    if (surfMyHomeAgainStatus.equals(AccountStatus.KICKOUT_LEVEL_2)) {
                        isBan = true;
                        doActionWhenBan(username, surfMyHomeAgainStatus);
                        return;
                    }
                    Thread.sleep(1000);
                    nextTimeSurfMyHome += randNextTimeSurfHome();
                    LOG.info("nextTimeSurfMyHome {}", nextTimeSurfMyHome);
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                // sleep for midnight
                try {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime > midnightTime) {
                        LOG.info("Pass midnight");
                        Thread.sleep(10 * 60 * 1000);
                        midnightTime = DateUtils.getMidnight();
                        LOG.info("New midnight: {}", new Date(midnightTime).toString());
                        // sleep 4 hours
                        LOG.info("Sleep night 4 hours");
                        Thread.sleep(4 * 60 * 60 * 1000);
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }

            } else {
                LOG.warn("Account {} empty outlinks to crawl!", username);
                // sleep time between 2 profiles
                try {
                    Thread.sleep(60 * 60 * 1000);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                try {
                    // re query my profile
                    String homeUrl = crawler.getHomeUrl();
                    if (StringUtils.isEmpty(homeUrl)) {
                        homeUrl = "https://m.facebook.com";
                    }
                    AccountStatus surfMyHomeAgainStatus = crawler.surfMyHome(homeUrl);
                    if (surfMyHomeAgainStatus.equals(AccountStatus.KICKOUT_LEVEL_2)) {
                        isBan = true;
                        doActionWhenBan(username, surfMyHomeAgainStatus);
                        return;
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private void doActionWhenBan(String username, AccountStatus accStatus) {
        // send error to master
        ServiceOutlinks.sendError(username, accStatus.toString());
        // send lock account to master
        ServiceOutlinks.addLockedAccount(username, accStatus.toString());
        // remove account from active list
        RunFacebookCrawler.activeAccounts.remove(username);
        LOG.info("BAN - Remove account from active list: {}", username);
    }

    private void doActionWhenRestrict(String username) {
        String message = "RESTRICTIONS - Account is restricted from seeing some profile.";
        // send error to master
        ServiceOutlinks.sendError(username, message);
        // send lock account to master
        ServiceOutlinks.addLockedAccount(username, message);
        // remove account from active list
        RunFacebookCrawler.activeAccounts.remove(username);
        LOG.info("RESTRICT - Remove account from active list: {}", username);
    }

    private void doCrawlerOld() {
        int countTurn = 0;
        while (true) {
            long startTime = System.currentTimeMillis();
            long expectEndTime = startTime + randCrawlTime();
            LOG.info("Account {} does turn {} - startTime: {} - expectEndTime: {}",
                    new Object[]{account.getUsername(), countTurn, new Date(startTime).toString(), new Date(expectEndTime).toString()});
            process(account, startTime, expectEndTime);
            if (isBan || isLogOut) {
                LOG.info("Account {} status BAN:{} - LOGOUT:{}. Remove from list", new Object[]{account.getUsername(), isBan, isLogOut});
                try {
                    // remove account from active list
                    RunFacebookCrawler.activeAccounts.remove(account.getUsername());
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
                    new Object[]{account.getUsername(), sleepTimeStr, expectReStartTime.toString()});
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
    private void process(Account account, long startTime, long expectEndTime) {

        String username = account.getUsername();
        String password = account.getPassword();
        String userAgent = account.getUserAgent();

        List<String> needCrawlUrls = new ArrayList<>();
        CookieManager cookieManager = new CookieManager();
        FacebookAction crawler = new FacebookAction(account, cookieManager, null);
        LOG.info("Account {} start login", username);
        AccountStatus accStatus = crawler.login();
        if (accStatus == null) {
            isBan = true;
            ServiceOutlinks.sendError(username, "LOGIN return error NULL");
            // send lock account to master
            ServiceOutlinks.addLockedAccount(username, "LOGIN return error NULL");
            return;
        }
        // check if login failed
        int errorCode = accStatus.getCode();
        if (errorCode != AccountStatus.LOGIN_OK.getCode()) {
            isBan = true;
            ServiceOutlinks.sendError(username, accStatus.toString());
            // send lock account to master
            ServiceOutlinks.addLockedAccount(username, accStatus.toString());
            return;
        }

        AccountStatus surfMyHomeStatus = crawler.surfMyHome(crawler.getHomeUrl());
//        if (surfMyHomeStatus.equals(AccountStatus.KICKOUT_LEVEL_2)
//                || surfMyHomeStatus.equals(AccountStatus.KICKOUT_UNKNOWN)) {
        if (surfMyHomeStatus.equals(AccountStatus.KICKOUT_LEVEL_2)) {
            isBan = true;
            ServiceOutlinks.sendError(username, surfMyHomeStatus.toString());
            // send lock account to master
            ServiceOutlinks.addLockedAccount(username, surfMyHomeStatus.toString());
            return;
        }

        // if login ok, continue this rest
        int count = 0;
        long nextTimeSurfMyHome = startTime + randNextTimeSurfHome();
        while (true) {
            // try 5 time to get urls
            for (int i = 0; i < 5; i++) {
                String queryUrl = ServiceOutlinks.getNextUrl(FB_MOBILE_HOST, username);
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
                String crawlUrl = "";
                try {
                    crawlUrl = needCrawlUrls.remove(0);
                    if (StringUtils.isNotEmpty(crawlUrl)) {
                        LOG.info("Account {} crawling {} ...", username, crawlUrl);
                        AccountStatus crawledStatus = crawler.crawl(crawlUrl);
                        LOG.info("Url {} - {}", crawlUrl, crawledStatus.toString());
                        if (crawledStatus.equals(AccountStatus.KICKOUT_LEVEL_2)) {
                            isBan = true;
                            ServiceOutlinks.sendError(username, crawledStatus.toString());
                            // send lock account to master
                            ServiceOutlinks.addLockedAccount(username, crawledStatus.toString());
                            return;
                        } else if (crawledStatus.equals(AccountStatus.KICKOUT_LEVEL_1)) {
                            LOG.info("Account {} cannot view this {}", username, crawlUrl);
                        }
                        count++;
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                // check end crawl time to logout
                long nowTime = System.currentTimeMillis();
                if (nowTime > expectEndTime) {
                    // should logout
                    LOG.info("Total profile crawled {}", count);
                    LOG.info("Account {} start log out here {}", username, crawlUrl);
                    // crawler.logout();
                    isLogOut = true;
                    break;
                }
                if ((nowTime > nextTimeSurfMyHome)
                        && (nowTime < (nextTimeSurfMyHome + (5 * 60 * 1000)))) {
                    // do surf my home again
                    try {
                        LOG.info("DO SURF MY HOME AGAIN");
                        String homeUrl = crawler.getHomeUrl();
                        if (StringUtils.isEmpty(homeUrl)) {
                            homeUrl = "https://m.facebook.com";
                        }
                        AccountStatus surfMyHomeAgainStatus = crawler.surfMyHome(homeUrl);
                        if (surfMyHomeAgainStatus.equals(AccountStatus.KICKOUT_LEVEL_2)) {
                            isBan = true;
                            ServiceOutlinks.sendError(username, surfMyHomeAgainStatus.toString());
                            // send lock account to master
                            ServiceOutlinks.addLockedAccount(username, surfMyHomeAgainStatus.toString());
                            return;
                        }
                        Thread.sleep(5 * 1000);
                        nextTimeSurfMyHome += randNextTimeSurfHome();
                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
            } else {
                LOG.warn("Account {} empty outlinks to crawl!", username);
                // sleep time between 2 profiles
                try {
                    Thread.sleep(60 * 60 * 1000);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                try {
                    // re query my profile
                    AccountStatus surfMyHomeAgainStatus = crawler.surfMyHome("https://m.facebook.com");
                    if (surfMyHomeAgainStatus.equals(AccountStatus.KICKOUT_LEVEL_2)) {
                        isBan = true;
                        ServiceOutlinks.sendError(username, surfMyHomeAgainStatus.toString());
                        // send lock account to master
                        ServiceOutlinks.addLockedAccount(username, surfMyHomeAgainStatus.toString());
                        return;
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private static int randCrawlTime() {
        // int[] values = Funcs.getMinMaxFromProperty("fb.crawltime.hour", 30, 50);
        int minTime = 80 * 60 * 60 * 1000;
        int maxTime = 100 * 60 * 60 * 1000;
        int ret = Funcs.randInt(minTime, maxTime);
        return ret;
    }

    private static int randSleepTimeBetweenLogins() {
        // int[] values = Funcs.getMinMaxFromProperty("fb.sleep.login.hour", 6, 8);
        int minTime = 6 * 60 * 60 * 1000;
        int maxTime = 8 * 60 * 60 * 1000;
        int ret = Funcs.randInt(minTime, maxTime);
        return ret;
    }

    private static int randSleepTimeBetweenProfiles() {
        // int[] values = Funcs.getMinMaxFromProperty("fb.sleep.profiles.seconds", 5, 10);
        int minTime = 5 * 60 * 1000;// 8 * 1000;
        int maxTime = 7 * 60 * 1000;// 15 * 1000;
        int ret = Funcs.randInt(minTime, maxTime);
        return ret;
    }

    private static int randNextTimeSurfHome() {
        int minTime = 3 * 60 * 60 * 1000; // 3 hours in milli
        int maxTime = 4 * 60 * 60 * 1000; // 4 hours in milli
        int ret = Funcs.randInt(minTime, maxTime);
        return ret;
    }
}
