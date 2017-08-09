package viettel.nfw.social.facebook.crawler;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.gfw.vn.producer.producer.MessageInfo;
import viettel.gfw.vn.producer.producer.ProducerORMWeb;
import viettel.gfw.vn.social.FaceBookProto;
import viettel.gfw.vn.social.FaceBookProtoTrans;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.common.ConfigurationChangeListner;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.utils.Constant;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class RunFacebookCrawler {

    private static final Logger LOG = LoggerFactory.getLogger(RunFacebookCrawler.class);

    public static final boolean TEST_MODE = false;
    private static final String ACCOUNT_TYPE_FACEBOOK = "m.facebook.com";

    public static ConcurrentHashMap<String, String> activeAccounts = new ConcurrentHashMap<>();
    private static final BlockingQueue<Account> accountQueue = new ArrayBlockingQueue<>(2000);
    public static BlockingQueue<FacebookObject> facebookObjectQueue = new ArrayBlockingQueue<>(2000000);

    private static Proxy proxy = null;

    public static void main(String[] args) {

        try {
            // Thread watch configuration
            ConfigurationChangeListner listner = new ConfigurationChangeListner(Constant.FACEBOOK_CRAWLER_CONF_FILE_PATH);
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
            // QueryServiceImpl querySvs = new QueryServiceImpl();
            // new Thread(querySvs).start();
            QueryVIPAccounts queryVIPAcc = new QueryVIPAccounts();
            new Thread(queryVIPAcc).start();

            // Thread send facebook object to bigdata
            SendObjectToBGImpl sendObjectToBGImpl = new SendObjectToBGImpl();
            new Thread(sendObjectToBGImpl).start();

            // Thread take account from queue
            TakeAccountsImpl takeImpl = new TakeAccountsImpl();
            new Thread(takeImpl).start();

        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static class SendObjectToBGImpl implements Runnable {

        private static ProducerORMWeb producer = new ProducerORMWeb("orm_web");

        @Override
        public void run() {
            Thread.currentThread().setName("SendObjectToBGImpl");

            Funcs.sleep(2000);
            while (true) {
                try {
                    FacebookObject fbObj = facebookObjectQueue.poll();
                    if (fbObj == null) {
                        Thread.sleep(1000);
                    } else {
                        MessageInfo message = new MessageInfo();
                        FaceBookProto.FBSocialObject fbSocialObject = FaceBookProtoTrans.facebookObjectTo(fbObj);
                        message.setDataSocial(fbSocialObject, MessageInfo.MESSAGE_TYPE_SOCIAL_FACEOOK);
                        producer.sendMessageORMWeb(message);
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
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
                    CrawlProfilesImpl job = new CrawlProfilesImpl(nextAcc, proxy);
                    new Thread(job).start();
                }
            }
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
//                        LOG.info("LOCKED {}", username);
                        continue;
                    }
//                    if (vipAccounts.contains(username)) {
//                        LOG.info("VIP {}", username);
//                        continue;
//                    }
                    if (activeAccounts.containsKey(username)) {
//                        LOG.info("Account {} is active ...", username);
                        continue;
                    }
                    accountQueue.add(fbAccount);
                    LOG.info("Add to queue {}::{}", fbAccount.getUsername(), fbAccount.getPassword());
                    activeAccounts.put(fbAccount.getUsername(), fbAccount.getPassword());
                }

                try {
                    Thread.sleep(5 * 60 * 1000);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private static class QueryVIPAccounts implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(QueryVIPAccounts.class);

        @Override
        public void run() {
            Thread.currentThread().setName("QueryVIPAccounts");
            String filename = "input/facebook-accounts-vip.txt";

            List<Account> fbAccounts = new ArrayList<>();
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
                        fbAccounts.add(acc);
                    }
                }
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            for (Account fbAccount : fbAccounts) {
                String username = fbAccount.getUsername();
                if (activeAccounts.containsKey(username)) {
                    continue;
                }
                accountQueue.add(fbAccount);
                LOG.info("Add to queue {}::{}", fbAccount.getUsername(), fbAccount.getPassword());
                activeAccounts.put(fbAccount.getUsername(), fbAccount.getPassword());
            }
        }

    }
}
