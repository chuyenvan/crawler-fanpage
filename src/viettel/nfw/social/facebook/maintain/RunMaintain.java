package viettel.nfw.social.facebook.maintain;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.common.ConfigurationChangeListner;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.utils.TCrawler;

/**
 *
 * @author duongth5
 */
public class RunMaintain {

    private static final Logger LOG = LoggerFactory.getLogger(RunMaintain.class);

    private static final String CONF_FILE_PATH = "conf/app-maintain-account.properties";
    private static final String URL_MASTER_LOCK = "http://192.168.6.81:1125/lock/";
    private static final String URL_MASTER_ACCOUNT = "http://192.168.6.81:1125/account/";
    private static final String DATE_FORMAT_DEFAULT = "EEE MMM dd HH:mm:ss zzz yyyy";

    public static final boolean TEST_MODE = false;
    private static final String ACCOUNT_TYPE_FACEBOOK = "m.facebook.com";

    public static ConcurrentHashMap<String, String> activeAccounts = new ConcurrentHashMap<>();
    private static final BlockingQueue<Account> queue = new ArrayBlockingQueue<>(2000);
    private static Proxy proxy = null;

    public static void main(String[] args) {

        try {
            // Thread watch configuration
            ConfigurationChangeListner listner = new ConfigurationChangeListner(CONF_FILE_PATH);
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

            // Thread querry service
            try {

                String querySvcMode = ApplicationConfiguration.getInstance().getConfiguration("service.query.mode");
                if (StringUtils.equals(querySvcMode, "RESTRICTIONS")) {
                    QueryService2Impl querySvc2 = new QueryService2Impl();
                    new Thread(querySvc2).start();
                } else {
                    // normal case
                    QueryServiceImpl querySvc = new QueryServiceImpl();
                    new Thread(querySvc).start();
                }

            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }

            // Thread take account from queue
            while (true) {
                try {
                    Thread.sleep(4 * 1000);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                LOG.info("Queue size {}", queue.size());
                Account nextAcc = queue.poll();
                if (nextAcc == null) {
                    LOG.info("Queue is empty");
                    try {
                        Thread.sleep(15 * 1000);
                    } catch (InterruptedException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                } else {
                    LOG.info("Start account {}", nextAcc.getUsername());
                    MaintainAccountImpl maintain = new MaintainAccountImpl(nextAcc, proxy);
                    new Thread(maintain).start();
                }
            }

        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static class QueryServiceImpl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(QueryServiceImpl.class);

        @Override
        public void run() {
            Thread.currentThread().setName("QuerryServiceImpl");
            while (true) {
                List<Account> fbAccounts = new ArrayList<>();
                List<String> lockedAccounts = new ArrayList<>();
                List<String> vipAccounts = new ArrayList<>();

                try {
                    // query list accounts by IP
                    String confServerIp = ApplicationConfiguration.getInstance().getConfiguration("server.ip");
                    if (StringUtils.isNotEmpty(confServerIp)) {
                        String ret = ServiceOutlinks.getAccount(confServerIp);
                        String[] rows = StringUtils.split(ret, "\n");
                        int size = rows.length;
                        if (size > 0) {
                            for (int i = 0; i < size; i++) {
                                String[] parts = StringUtils.split(rows[i], "|");
                                String type = parts[0].trim();
                                String username = parts[1].trim();
                                String password = parts[2].trim();
                                String useragent = parts[3].trim();
                                String serverIp = parts[4].trim();
                                String addTime = parts[5].trim();

                                if (StringUtils.equalsIgnoreCase(type, ACCOUNT_TYPE_FACEBOOK)) {
                                    Account acc = new Account();
                                    acc.setUsername(username);
                                    acc.setPassword(password);
                                    acc.setUserAgent(useragent);
                                    acc.setAddedTime(Long.valueOf(addTime));
                                    fbAccounts.add(acc);
                                }
                            }
                        }
                    }

                    // query list locked accounts
                    lockedAccounts = ServiceOutlinks.getLockedAccounts();

                    // query list VIP accounts
                    String confVipAccs = ApplicationConfiguration.getInstance().getConfiguration("fb.account.vip");
                    String[] parts = StringUtils.split(confVipAccs.trim(), ",");
                    vipAccounts.addAll(Arrays.asList(parts));

                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                for (Account fbAccount : fbAccounts) {
                    String username = fbAccount.getUsername();
                    if (lockedAccounts.contains(username)) {
                        LOG.info("LOCKED {}", username);
                        continue;
                    }
                    if (vipAccounts.contains(username)) {
                        LOG.info("VIP {}", username);
                        continue;
                    }
                    if (activeAccounts.containsKey(username)) {
                        LOG.info("Account {} is active ...", username);
                        continue;
                    }
                    queue.add(fbAccount);
                    activeAccounts.put(fbAccount.getUsername(), fbAccount.getPassword());
                }

                try {
                    Thread.sleep(10 * 1000);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private static class QueryService2Impl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(QueryService2Impl.class);

        @Override
        public void run() {
            Thread.currentThread().setName("QueryService2Impl");
            while (true) {
                List<Account> fbAccounts = new ArrayList<>();
                List<String> vipAccounts = new ArrayList<>();

                try {
                    fbAccounts.addAll(getRestrictAccount());

                    // query list VIP accounts
                    String confVipAccs = ApplicationConfiguration.getInstance().getConfiguration("fb.account.vip");
                    String[] parts = StringUtils.split(confVipAccs.trim(), ",");
                    vipAccounts.addAll(Arrays.asList(parts));
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                for (Account fbAccount : fbAccounts) {
                    String username = fbAccount.getUsername();
                    if (vipAccounts.contains(username)) {
                        LOG.info("VIP {}", username);
                        continue;
                    }
                    if (activeAccounts.containsKey(username)) {
                        LOG.info("Account {} is active ...", username);
                        continue;
                    }
                    queue.add(fbAccount);
                    activeAccounts.put(fbAccount.getUsername(), fbAccount.getPassword());
                }

                try {
                    Thread.sleep(60 * 60 * 1000);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }

        private static List<Account> getRestrictAccount() {

            List<Account> returnAccounts = new ArrayList<>();
            try {
                String response = TCrawler.getContentFromUrl(URL_MASTER_LOCK);

                List<Account> fullAccounts = new ArrayList<>();
                fullAccounts.addAll(getFullAccounts());

                Document doc = Jsoup.parse(response);
                Elements trs = doc.getElementsByTag("tr");
                for (Element tr : trs) {
                    Elements tds = tr.getElementsByTag("td");
                    String account = tds.get(0).text();
                    String timeStr = tds.get(1).text();
                    String message = tds.get(2).text();

                    if (StringUtils.startsWith(message, "RESTRICTIONS")) {
                        for (Account fullAccount : fullAccounts) {
                            if (StringUtils.equals(account, fullAccount.getUsername())) {
                                returnAccounts.add(fullAccount);
                                break;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
            return returnAccounts;
        }

        private static List<Account> getFullAccounts() {

            String response = TCrawler.getContentFromUrl(URL_MASTER_ACCOUNT);

            LOG.info(response);
            List<Account> accounts = new ArrayList<>();

            Document doc = Jsoup.parse(response);
            Elements trs = doc.getElementsByTag("tr");

            for (Element tr : trs) {
                Elements tds = tr.getElementsByTag("td");
                String type = tds.get(0).text();
                String username = tds.get(1).text();
                String password = tds.get(2).text();
                String useragent = tds.get(3).text();
                String addTimeStr = tds.get(5).text();
                long addedTime = 0;

                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_DEFAULT);
                    Date addedDate = sdf.parse(addTimeStr);
                    addedTime = addedDate.getTime();
                } catch (ParseException ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                if (addedTime != 0) {
                    Account acc = new Account();
                    acc.setAccType(type);
                    acc.setUsername(username);
                    acc.setPassword(password);
                    acc.setUserAgent(useragent);
                    acc.setAddedTime(addedTime);
                    accounts.add(acc);
                }
            }
            return accounts;
        }
    }
}
