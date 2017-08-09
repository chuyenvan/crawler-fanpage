package viettel.nfw.social.facebook.trackingnewdomains;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.CookieManager;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.FacebookAction;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.maintain.RunMaintain;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class RunTrackingDomains {

    private static final Logger LOG = LoggerFactory.getLogger(RunTrackingDomains.class);

    public static void main(String[] args) {

        SocialControllerReporter reporter = SocialControllerReporter.getDefault();
        new Thread(reporter).start();

        List<String> urls = new ArrayList<>();
        String filename = "data2/tracking/list.txt";
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    urls.add(temp);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static class TrackingDomainImpl implements Runnable {

        private final Account account;
        private final List<String> urls;
        private final Proxy proxy;

        public TrackingDomainImpl(Account account, List<String> urls, Proxy proxy) {
            this.account = account;
            this.urls = urls;
            this.proxy = proxy;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(account.getUsername());
            CookieManager cookieManager = new CookieManager();
            FacebookAction fbAction = new FacebookAction(account, cookieManager, proxy);
            boolean isLogin = false;
            while (true) {
                // wake up and DO your JOB
                if (!isLogin) {
                    AccountStatus accStatus = fbAction.login();
                    if (accStatus.equals(AccountStatus.LOGIN_OK)) {
                        isLogin = true;
                    } else {
                        // send lock account to master
                        ServiceOutlinks.addLockedAccount(account.getUsername(), accStatus.toString());
                        // remove account from active list
                        RunMaintain.activeAccounts.remove(account.getUsername());
                        LOG.warn("Account locked {}. BREAK!", account.getUsername());
                        break;
                    }
                }

                for (String url : urls) {

                }

                Funcs.sleep(60 * 60 * 1000);
            }
        }

    }
}
