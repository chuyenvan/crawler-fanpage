package viettel.nfw.social.facebook.deeptracking;

import java.net.CookieManager;
import java.net.Proxy;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.FacebookAction;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.evaluation.RunEvaluation;

/**
 *
 * @author duongth5
 */
public class DeepTrackingProfilesImpl implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(DeepTrackingProfilesImpl.class);

    private final Account account;
    private final Proxy proxy;
    private boolean isBan;
    private boolean isLogOut;

    public DeepTrackingProfilesImpl(Account account, Proxy proxy) {
        this.account = account;
        this.proxy = proxy;
        isBan = false;
        isLogOut = false;
    }

    @Override
    public void run() {
        Thread.currentThread().setName(account.getUsername());
        doJob();
    }

    private void doJob() {
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

        // do crawl profile
        boolean doSwitch = true;
        while (true) {

            try {
                String urlFrom;
                String crawlUrl;

                if (doSwitch) {
                    crawlUrl = RunDeepTracking.facebookUrlsQueue.poll();
                    urlFrom = "chuyennd2";
                    doSwitch = false;
                } else {
                    crawlUrl = RunDeepTracking.facebookTrungNT3UrlsQueue.poll();
                    urlFrom = "trungnt3";
                    doSwitch = true;
                }

                if (StringUtils.isNotEmpty(crawlUrl)) {
                    LOG.info("Account {} crawling {} ...", username, crawlUrl);
                    AccountStatus crawledStatus = crawler.trackingProfile(crawlUrl, urlFrom);
                    LOG.info("Url {} - {}", crawlUrl, crawledStatus.toString());
                    if (crawledStatus.equals(AccountStatus.KICKOUT_LEVEL_2)) {
                        isBan = true;
                        doActionWhenBan(username, crawledStatus);
                        return;
                    } else if (crawledStatus.equals(AccountStatus.KICKOUT_LEVEL_1)) {
                        LOG.info("Account {} cannot view this {}", username, crawlUrl);
                    } else if (crawledStatus.equals(AccountStatus.KICKOUT_UNKNOWN)) {
                        LOG.info("Account {} is KICKOUT_UNKNOWN");
                    }

                    Thread.sleep(60 * 1000);
                } else {
                    Thread.sleep(1000);
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    private void doActionWhenBan(String username, AccountStatus accStatus) {
        // send error to master
        ServiceOutlinks.sendError(username, accStatus.toString());
        // send lock account to master
        ServiceOutlinks.addLockedAccount(username, accStatus.toString());
        // remove account from active list
        RunEvaluation.activeAccounts.remove(username);
        LOG.info("BAN - Remove account from active list: {}", username);
    }

}
