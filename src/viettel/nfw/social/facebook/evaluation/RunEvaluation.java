package viettel.nfw.social.facebook.evaluation;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.common.ConfigurationChangeListner;
import viettel.nfw.social.common.QueryFakeAccount;
import viettel.nfw.social.service.WebServer;

/**
 *
 * @author duongth5
 */
public class RunEvaluation {

    private static final Logger LOG = LoggerFactory.getLogger(RunEvaluation.class);
    private static final String CONF_FILE_PATH = "conf/app-evaluation.properties";
    public static final String USER_AGENT_DF = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:33.0) Gecko/20100101 Firefox/33.0";
    public static String proxyString = null;
    private static final int MAX_CAPACITY = 5000000;

    public static ConcurrentHashMap<String, String> activeAccounts = new ConcurrentHashMap<>();
    private static final BlockingQueue<Account> accQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
    public static BlockingQueue<String> urlQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
    private static Proxy proxy = null;

    public static void main(String[] args) {
        try {
            // Thread watch configuration
            ConfigurationChangeListner listner = new ConfigurationChangeListner(CONF_FILE_PATH);
            new Thread(listner).start();
            LOG.info("Started ConfigurationChangeListner");

            try {
                Thread.sleep(3 * 1000);
            } catch (InterruptedException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            // setup proxy
            try {
                String proxyMode = ApplicationConfiguration.getInstance().getConfiguration("proxy");
                if (StringUtils.equals(proxyMode, "true")) {
                    String proxyHostname = ApplicationConfiguration.getInstance().getConfiguration("proxy.hostname");
                    String proxyPortStr = ApplicationConfiguration.getInstance().getConfiguration("proxy.port");
                    proxyString = proxyHostname + ":" + proxyPortStr;
                    int proxyPort = Integer.valueOf(proxyPortStr);
                    proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHostname, proxyPort));
                    LOG.info("Proxy enable: TRUE");
                    LOG.info("Proxy {}::{}", proxyHostname, proxyPortStr);
                } else {
                    LOG.info("Proxy enable: FALSE");
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }

            // start rest service
            int port = 1155;
            WebServer restServer = new WebServer(port);
            new Thread(restServer).start();

            // Thread query accounts
            QueryAccountsImpl queryImpl = new QueryAccountsImpl();
            new Thread(queryImpl).start();

            // Thread take account from queue
            TakeAccountsImpl takeImpl = new TakeAccountsImpl();
            new Thread(takeImpl).start();

        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static class TakeAccountsImpl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(TakeAccountsImpl.class);

        @Override
        public void run() {
            Thread.currentThread().setName("TakeAccountsImpl");

            while (true) {
                try {
                    Thread.sleep(4 * 1000);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                LOG.info("Queue size {}", accQueue.size());
                Account nextAcc = accQueue.poll();
                if (nextAcc == null) {
                    LOG.info("Queue is empty");
                    try {
                        Thread.sleep(15 * 1000);
                    } catch (InterruptedException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                } else {
                    LOG.info("Start account {}", nextAcc.getUsername());
                    String fbMode = ApplicationConfiguration.getInstance().getConfiguration("facebook.mode");
                    if (StringUtils.equals(fbMode, "web")) {
                        EvaluateProfilesByWebImpl evaluationWeb = new EvaluateProfilesByWebImpl(nextAcc, proxy);
                        new Thread(evaluationWeb).start();
                    } else if (StringUtils.equals(fbMode, "mobile")) {
                        EvaluateProfilesImpl evaluation = new EvaluateProfilesImpl(nextAcc, proxy);
                        new Thread(evaluation).start();
                    }
                }
            }
        }

    }

    private static class QueryAccountsImpl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(QueryAccountsImpl.class);

        @Override
        public void run() {
            Thread.currentThread().setName("QueryAccountsImpl");
            while (true) {

                List<Account> fbAccounts = new ArrayList<>();
                List<String> lockedAccounts = new ArrayList<>();

                try {
                    // query accounts by ip and type
                    String accountKind = ApplicationConfiguration.getInstance().getConfiguration("master.account.kind");
                    fbAccounts = QueryFakeAccount.getByIp(accountKind, "m.facebook.com");

                    // query list locked accounts
                    lockedAccounts = ServiceOutlinks.getLockedAccounts();
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                for (Account fbAccount : fbAccounts) {
                    String username = fbAccount.getUsername();
                    if (lockedAccounts.contains(username)) {
                        LOG.info("LOCKED {}", username);
                        continue;
                    }
                    if (activeAccounts.containsKey(username)) {
                        LOG.info("Account {} is active ...", username);
                        continue;
                    }
                    accQueue.add(fbAccount);
                    activeAccounts.put(fbAccount.getUsername(), fbAccount.getPassword());
                }

                try {
                    LOG.info("Sleep for 1 hour");
                    Thread.sleep(60 * 60 * 1000);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }

    }

}
