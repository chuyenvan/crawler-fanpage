package viettel.nfw.social.facebook.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.utils.TCrawler;

/**
 *
 * @author duongth5
 */
public class UnlockAccounts {

    private static final Logger LOG = LoggerFactory.getLogger(UnlockAccounts.class);
    private static final long TIME_TO_UNLOCK = 24 * 60 * 60 * 1000;
    private static final String URL_MASTER_LOCK = "http://192.168.6.81:1125/lock/?action=show";
    private static final String DATE_FORMAT_DEFAULT = "EEE MMM dd HH:mm:ss zzz yyyy";
    public static final String URL_MASTER_ACCOUNT = "http://192.168.6.81:1125/account/";

    public static void main(String[] args) {

//        doUnlock();
//        getErrorAccount();
//        doUnlock2();
//        doGetVIPAcc();
//        doCheckPetAccs();
        doUpdateAccounts();
    }

    private static void doUnlock() {
        while (true) {
            try {
                crawlMasterLockPage();

                LOG.info("sleep");
                long timeToSleep = 2 * 60 * 60 * 1000; // 2 hours
                Thread.sleep(timeToSleep);
            } catch (InterruptedException ex) {
                LOG.error(ex.getMessage());
            }
        }
    }

    private static void crawlMasterLockPage() {

        try {
            String response = TCrawler.getContentFromUrl(URL_MASTER_LOCK);

            LOG.info(response);
            List<LockAcc> lockAccs = new ArrayList<>();

            Document doc = Jsoup.parse(response);
            Elements trs = doc.getElementsByTag("tr");

            for (Element tr : trs) {
                Elements tds = tr.getElementsByTag("td");
                String account = tds.get(0).text();
                String timeStr = tds.get(1).text();
                String message = tds.get(2).text();

                if (StringUtils.contains(message, "RESTRICTIONS")) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_DEFAULT);
                        Date date = sdf.parse(timeStr);
                        long time = date.getTime();
                        long currentTime = System.currentTimeMillis();
                        long diff = currentTime - time;

                        if (diff > TIME_TO_UNLOCK) {
                            // LOG.info("{}\t{}\t{}", account, timeStr, message);
                            LockAcc lockAcc = new LockAcc();
                            lockAcc.account = account;
                            lockAcc.time = time;
                            lockAcc.message = message;
                            lockAccs.add(lockAcc);
                        }

                    } catch (ParseException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
            }

            if (!lockAccs.isEmpty()) {
                for (LockAcc lockAcc : lockAccs) {
                    String account = lockAcc.account;
                    sendUnlock(account);
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

    }

    private static void doUnlock2() {
        List<String> accs = new ArrayList<>();
        String filename = "data2/recheck/listResolved";
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = StringUtils.split(temp, "\t");
                    accs.add(parts[0]);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (String acc : accs) {
            sendUnlock(acc);
        }
    }

    private static void sendUnlock(String account) {
        try {
            String unlockUrl = "http://192.168.6.81:1125/lock/?action=delete&accountName=" + URLEncoder.encode(account, "UTF-8");
            String response = TCrawler.getContentFromUrl(unlockUrl);
            LOG.info("response unlock {}", account);
        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static class LockAcc {

        public String account;
        public String password;
        public long time;
        public String message;

        @Override
        public String toString() {
            return account + "\t" + new Date(time).toString() + "\t" + message;
        }

    }

    private static void getErrorAccount() {
        try {
            String response = TCrawler.getContentFromUrl(URL_MASTER_LOCK);

            List<Account> fullAccounts = new ArrayList<>();
            fullAccounts.addAll(getFullAccounts());
            List<LockAcc> lockAccs = new ArrayList<>();

            Document doc = Jsoup.parse(response);
            Elements trs = doc.getElementsByTag("tr");

            for (Element tr : trs) {
                Elements tds = tr.getElementsByTag("td");
                String account = tds.get(0).text();
                String timeStr = tds.get(1).text();
                String message = tds.get(2).text();

                if (StringUtils.startsWith(message, "1005:")) {
                    String password = "";
                    for (Account fullAccount : fullAccounts) {
                        if (StringUtils.equals(account, fullAccount.getUsername())) {
                            password = fullAccount.getPassword();
                            break;
                        }
                    }
                    if (!StringUtils.isEmpty(password)) {
                        System.out.println(account + "\t" + password);
                    }
                }
            }

        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
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

            Account acc = new Account();
            acc.setAccType(type);
            acc.setUsername(username);
            acc.setPassword(password);
            accounts.add(acc);
        }
        return accounts;
    }

    private static void doGetVIPAcc() {
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
            String ip = tds.get(4).text();

            if (StringUtils.equals(type, "m.facebook.com") && StringUtils.equals(ip, "82")) {
                System.out.println(username + "\t" + password);
            }
        }

    }

    private static void doCheckPetAccs() {
        Set<String> petAccs = new HashSet<>();
        String filename = "data2/chuyennd2/50acc-pet.txt";
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    petAccs.add(temp);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        List<Account> accs = getFullAccounts();

        for (String petAcc : petAccs) {
            boolean isExisted = false;
            for (Account acc : accs) {
                if (petAcc.equals(acc.getUsername())) {
                    isExisted = true;
                    break;
                }
            }
            if (isExisted) {
                System.out.println(petAcc);
            }
        }
    }

    private static void doUpdateAccounts() {

        String requireServerIp = "84";
        String response = TCrawler.getContentFromUrl(URL_MASTER_ACCOUNT);

        Document doc = Jsoup.parse(response);
        Elements trs = doc.getElementsByTag("tr");

        for (Element tr : trs) {
            Elements tds = tr.getElementsByTag("td");
            String type = tds.get(0).text();
            String username = tds.get(1).text();
            String password = tds.get(2).text();
            String useragent = tds.get(3).text();
            String serverIp = tds.get(4).text();
            String addTimeStr = tds.get(5).text();

            if (StringUtils.equals(serverIp, requireServerIp)
                    && StringUtils.equals(type, "plus.google.com")) {
                String temp = type + "|" + username + "|" + password + "|" + useragent + "|" + serverIp + "|" + addTimeStr;
                System.out.println(temp);
                try {
                    // delete
                    TCrawler.getContentFromUrl("http://192.168.6.81:1125/account/?action=delete&accountName=" + URLEncoder.encode(username, "UTF-8") + "&accountType=plus.google.com");
                    Thread.sleep(1000);
                    // then re-add
                    ServiceOutlinks.pushAccountToMaster("plus.google.com", username, password, useragent, "82");
                    Thread.sleep(1000);
                } catch (InterruptedException | UnsupportedEncodingException ex) {
                    LOG.error(ex.getMessage(), ex);
                }

            }
        }

    }
}
