package viettel.nfw.social.twitter.crawler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
import viettel.nfw.social.common.Account;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.common.ConfigurationChangeListner;
import viettel.nfw.social.utils.Constant;
import vn.itim.detector.LanguageDetector;

/**
 *
 * @author duongth5
 */
public class RunTwitterCrawler {

    private static final Logger LOG = LoggerFactory.getLogger(RunTwitterCrawler.class);

    public static ConcurrentHashMap<String, String> activeAccounts = new ConcurrentHashMap<>();
    private static final BlockingQueue<Account> accountQueue = new ArrayBlockingQueue<>(2000);
    private static Proxy proxy = null;

    public static void main(String[] args) {
        try {

            LanguageDetector languageDetector = new LanguageDetector();

            // Thread watch configuration
            ConfigurationChangeListner listner = new ConfigurationChangeListner(Constant.TWITTER_CRAWLER_CONF_FILE_PATH);
            new Thread(listner).start();

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
                    int proxyPort = Integer.valueOf(proxyPortStr);
                    proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHostname, proxyPort));
                }

            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }

            // Thread query accounts
            QueryAccountsImpl queryAccountsImpl = new QueryAccountsImpl();
            new Thread(queryAccountsImpl).start();

            // Thread take account from queue
            TakeAccountsImpl takeImpl = new TakeAccountsImpl(languageDetector);
            new Thread(takeImpl).start();
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static class TakeAccountsImpl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(TakeAccountsImpl.class);
        private final LanguageDetector languageDetector;

        public TakeAccountsImpl(LanguageDetector languageDetector) {
            this.languageDetector = languageDetector;
        }

        @Override
        public void run() {
            Thread.currentThread().setName("TakeAccountsImpl");

            while (true) {
                try {
                    Thread.sleep(4 * 1000);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                LOG.info("Queue size {}", accountQueue.size());
                Account nextAcc = accountQueue.poll();
                if (nextAcc == null) {
                    // LOG.info("Queue is empty");
                    try {
                        Thread.sleep(60 * 1000);
                    } catch (InterruptedException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                } else {
                    LOG.info("Start account {}", nextAcc.getUsername());
                    TwJob job = new TwJob(nextAcc.getUsername(), nextAcc.getPassword(), nextAcc.getUserAgent());
                    job.setLanguageDetector(languageDetector);
                    new Thread(job).start();
                }
            }
        }
    }

    private static class QueryAccountsImpl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(QueryAccountsImpl.class);

        @Override
        public void run() {
            Thread.currentThread().setName("QuerryServiceImpl");
            String filename = "input/twitter-accounts.txt";

            List<Account> twitterAccs = new ArrayList<>();
            try {
                try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String temp = line.trim();
                        String[] parts = StringUtils.split(temp, "\t");
                        Account acc = new Account();
                        acc.setUsername(parts[0]);
                        acc.setPassword(parts[1]);
                        acc.setUserAgent(parts[2]);
                        twitterAccs.add(acc);
                    }
                }
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            for (Account twitterAcc : twitterAccs) {
                String username = twitterAcc.getUsername();
                if (activeAccounts.containsKey(username)) {
                    continue;
                }
                accountQueue.add(twitterAcc);
                LOG.info("Add to queue {}::{}", twitterAcc.getUsername(), twitterAcc.getPassword());
                activeAccounts.put(twitterAcc.getUsername(), twitterAcc.getPassword());
            }
        }

    }

}
