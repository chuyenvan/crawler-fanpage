package viettel.nfw.social.facebook.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.utils.TCrawler;

/**
 *
 * @author duongth5
 */
public class ManualAccountManagement {

    private static final Logger LOG = LoggerFactory.getLogger(ManualAccountManagement.class);

    public static void main(String[] args) {
//        doDelete();
//        doPushAccountsToMaster();
//        doPushAccountFromChuyenNDToMaster();
//        doFilterAccountFromChuyenND();
//        doPushAccountFromDuongTHToMaster();
//        doPushAccountFromDuongTHToMaster2();
//        doGenList();
//        doShuffleListAccounts();
//        doReadUrlFailed();
//        doPushCrawledProfileToMaster();
//        doCollectSensitiveProfile();
//        doFilterSensitiveProfile();
//        doFilterSensitiveProfile2();
        dosomething();
    }

    private static void dosomething() {
        String fileFull = "D:\\git\\fb-actions\\data2\\sensitive\\bai2.txt";
        String fileSen = "D:\\git\\fb-actions\\data2\\sensitive\\sensitive_profile_1433818583324.txt";

        List<String> fullURLs = new ArrayList<>();
        List<String> senURLs = new ArrayList<>();

        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(fileFull)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    fullURLs.add(temp);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(fileSen)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = StringUtils.split(temp, "\t");
                    senURLs.add(parts[0]);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (String fullURL : fullURLs) {
            if (senURLs.contains(fullURL)) {
                System.out.println("true");
            } else {
                System.out.println("false");
            }
        }
    }

    private static void doFilterSensitiveProfile2() {
        String filenameExisted = "data2/sensitive/detected-util-27-4.txt";
        String filenameTotal = "data2/sensitive/new/urls-sensitive-total.txt";

        Set<String> existedUrls = new HashSet<>();
        Set<ABC> newUrls = new HashSet<>();

        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filenameExisted)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    existedUrls.add(temp);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filenameTotal)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = StringUtils.split(temp, "\t");
                    String url = parts[0];
                    String score = parts[1];
                    newUrls.add(new ABC(url, score));
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        Set<ABC> okUrls = new HashSet<>();
        for (ABC newUrl : newUrls) {
            String url = newUrl.url;
            if (existedUrls.contains(url)) {
                System.out.println(url + " -> Existed!!!");
            } else {
                okUrls.add(newUrl);
            }
        }

        System.out.println("@#@#@#@#@#@#@#@#");

        for (ABC okUrl : okUrls) {
            System.out.println(okUrl.url + "\t" + okUrl.score);
        }

    }

    private static void doFilterSensitiveProfile() {
        String filenameOld = "data2/sensitive/compare/list-old.txt";
        String filenameNew = "data2/sensitive/compare/list-new.txt";

        List<ABC> oldUrls = new ArrayList<>();
        List<ABC> newUrls = new ArrayList<>();

        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filenameOld)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = StringUtils.split(temp, "\t");
                    String url = parts[0];
                    String score = parts[1];
                    oldUrls.add(new ABC(url, score));
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filenameNew)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = StringUtils.split(temp, "\t");
                    String url = parts[0];
                    String score = parts[1];
                    newUrls.add(new ABC(url, score));
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (ABC newUrl : newUrls) {
            boolean isOK = true;
            String newUrlStr = newUrl.url;
            for (ABC oldUrl : oldUrls) {
                String oldUrlStr = oldUrl.url;
                if (StringUtils.equals(oldUrlStr, newUrlStr)) {
                    isOK = false;
                    break;
                }
            }
            if (isOK) {
                System.out.println(newUrl.url + "\t" + newUrl.score);
            }
        }

    }

    private static class ABC {

        public String url;
        public String score;

        public ABC(String url, String score) {
            this.url = url;
            this.score = score;
        }

    }

    private static void doCollectSensitiveProfile() {
        String filename = "data2/sensitive/sensitive27-4.txt";
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = StringUtils.split(temp, "\t");

                    String norm = Parser.normalizeProfileUrl(new URI(parts[0]));
                    String[] parts2 = StringUtils.split(norm, "|");

                    String url = parts2[1];
                    String score = parts[1];

                    System.out.println(url + "\t" + score);

                }
            } catch (URISyntaxException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static void doPushCrawledProfileToMaster() {
        // Set<String> urls = new HashSet<>();
        Map<String, String> crawledUrls = new HashMap<>();
        String filename = "data2/crawled/crawled83.txt";
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = StringUtils.split(temp, "\t");

                    String norm = Parser.normalizeProfileUrl(new URI(parts[0]));
                    String[] parts2 = StringUtils.split(norm, "|");

                    String url = parts2[1];
                    String time = parts[1];
                    crawledUrls.put(url, time);

                }
            } catch (URISyntaxException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        LOG.info("size {}", crawledUrls.size());

        ThreadPoolExecutor excutor = new ThreadPoolExecutor(40, 40, 1, TimeUnit.DAYS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(1000));
        for (Map.Entry<String, String> entrySet : crawledUrls.entrySet()) {
            final String key = entrySet.getKey();
            final String value = entrySet.getValue();

            try {
                excutor.execute(new Runnable() {

                    @Override
                    public void run() {
                        ServiceOutlinks.addCrawledUrl("DEFAULT", key, value);
                        LOG.info("{} - {}", key, value);
                    }
                });
            } catch (RejectedExecutionException ex) {
                while (excutor.getQueue().size() > 100) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex1) {
                        LOG.error(ex1.getMessage(), ex1);
                    }
                }
            }
        }
    }

    private static void doReadUrlFailed() {
        Set<String> urls = new HashSet<>();
        String filename = "data2/urlfailed/urlfailed.txt";
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String norm = Parser.normalizeProfileUrl(new URI(temp));
                    String[] parts = StringUtils.split(norm, "|");
                    urls.add(parts[1]);
                }
            } catch (URISyntaxException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        LOG.info("size {}", urls.size());
//        for (String url : urls) {
//            System.out.println(url);
//        }

        ThreadPoolExecutor excutor = new ThreadPoolExecutor(40, 40, 1, TimeUnit.DAYS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(1000));
        for (final String url : urls) {
            try {
                excutor.execute(new Runnable() {

                    @Override
                    public void run() {
                        boolean ret = ServiceOutlinks.addOutLink("DEFAULT", url, url);
                        LOG.info("{} - {}", ret, url);
                    }
                });
            } catch (RejectedExecutionException ex) {
                while (excutor.getQueue().size() > 100) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex1) {
                        LOG.error(ex1.getMessage(), ex1);
                    }
                }
            }
        }
    }

    private static void doDelete() {
        String filename = "data2/fb-delete.txt";
        String type = "m.facebook.com";

        Set<String> deleteAccounts = readListWillDelete(filename);
        for (String deleteAccount : deleteAccounts) {
            ServiceOutlinks.deleteAccount(deleteAccount, type);
        }
    }

    private static Set<String> readListWillDelete(String filename) {
        Set<String> listAccountName = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = temp.split("\t");
                    listAccountName.add(parts[0]);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return listAccountName;
    }

    private static void doPushAccountsToMaster() {
        String filename = "data2/fb-acc-push.txt";
        String type = "m.facebook.com";
        String ip = "82";

        List<Account> accounts = readAccounts(filename, true);

        for (Account account : accounts) {

            ServiceOutlinks.pushAccountToMaster(type, account.getUsername(), account.getPassword(), account.getUserAgent(), ip);
        }
    }

    private static void doPushAccountFromChuyenNDToMaster() {
        String filename = "data2/chuyennd2/listActive";
        String filenameUA = "data2/useragent.txt";
        String type = "m.facebook.com";
        String ip;

        List<Account> accounts = readAccounts(filename, false);
        List<Account> addedAccounts = getFullAccounts();

        List<Account> toAddAccounts = new ArrayList<>();

        for (Account account : accounts) {
            boolean isAdded = false;
            for (Account addedAccount : addedAccounts) {
                if (StringUtils.equalsIgnoreCase(account.getUsername(), addedAccount.getUsername())) {
                    isAdded = true;
                    break;
                }
            }
            if (isAdded) {

            } else {
                toAddAccounts.add(account);
            }
        }

        List<String> useragents = readUserAgents(filenameUA);
        Random rand = new Random();
        int count = 0;
        for (Account toAddAccount : toAddAccounts) {
            count++;
            System.out.println(toAddAccount.getUsername() + "\t" + toAddAccount.getPassword());
            if (count < 11) {
                // not add
                System.out.println("NOT ADD");
            } else {
                if (count < 21) {
                    ip = "81";
                } else if (count >= 21 && count < 41) {
                    ip = "82";
                } else {
                    ip = "83";
                }
                int pos = rand.nextInt(useragents.size());
                String userAgent = useragents.get(pos);
                ServiceOutlinks.pushAccountToMaster(type, toAddAccount.getUsername(), toAddAccount.getPassword(), userAgent, ip);
            }
        }

//        List<String> useragents = readUserAgents(filenameUA);
//        Random rand = new Random();
//        int count = 0;
//        for (Account account : accounts) {
//            if (count < 20) {
//                ip = "81";
//            } else if (count >= 20 && count < 45) {
//                ip = "82";
//            } else {
//                ip = "83";
//            }
//            int pos = rand.nextInt(useragents.size());
//            String userAgent = useragents.get(pos);
//            ServiceOutlinks.pushAccountToMaster(type, account.getUsername(), account.getPassword(), userAgent, ip);
//            count++;
//        }
//        String filename = "data2/chuyennd2/duongth_uncheck.txt";
//        List<Account> accounts = readAccounts2(filename, false);
//        for (Account account : accounts) {
//            if (account.getPassword().equalsIgnoreCase("error")) {
//                continue;
//            }
//            System.out.println(account.getUsername() + "\t" + account.getPassword());
//        }
    }

    private static List<Account> getFullAccounts() {

        String response = TCrawler.getContentFromUrl(UnlockAccounts.URL_MASTER_ACCOUNT);
        // LOG.info(response);
        List<Account> accounts = new ArrayList<>();

        Document doc = Jsoup.parse(response);
        Elements trs = doc.getElementsByTag("tr");

        for (Element tr : trs) {
            Elements tds = tr.getElementsByTag("td");
            String type = tds.get(0).text();
            String username = tds.get(1).text();
            String password = tds.get(2).text();

            if (StringUtils.equalsIgnoreCase(type, "m.facebook.com")) {
                Account acc = new Account();
                acc.setAccType(type);
                acc.setUsername(username);
                acc.setPassword(password);
                accounts.add(acc);
            }
        }
        return accounts;
    }

    private static void doFilterAccountFromChuyenND() {
        // String file20 = "data2/chuyennd2/file-20.txt";
        String file100 = "data2/chuyennd2/file-full.txt";
        String fileFull = "data2/chuyennd2/listActive";

        // List<Account> accounts20 = readAccounts(file20, false);
        List<Account> accounts100 = readAccounts(file100, false);
        List<Account> accountsFull = readAccounts(fileFull, false);

        for (Account accountsFull1 : accountsFull) {
            boolean isContinue = true;
            String username = accountsFull1.getUsername();

//            for (Account accounts201 : accounts20) {
//                String un20 = accounts201.getUsername();
//                if (StringUtils.equals(un20, username)) {
//                    isContinue = false;
//                    break;
//                }
//            }
            for (Account accounts1001 : accounts100) {
                String un100 = accounts1001.getUsername();
                if (StringUtils.equals(un100, username)) {
                    isContinue = false;
                    break;
                }
            }

            if (isContinue) {
                System.out.println(accountsFull1.getUsername() + "\t" + accountsFull1.getPassword());
            }
        }
    }

    private static void doPushAccountFromDuongTHToMaster2() {
        String filename = "data2/duongth5/test.txt";
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = temp.split("\t");
                    String type = parts[0];
                    String username = parts[1];
                    String password = parts[2];
                    String userAgent = parts[3];
                    String ip = parts[4];

                    String format = "%s\t%s\t%s\t%s\t%s";
                    System.out.println(String.format(format, type, username, password, userAgent, ip));
                    ServiceOutlinks.pushAccountToMaster(type, username, password, userAgent, ip);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static void doPushAccountFromDuongTHToMaster() {
        String filename = "data2/Account2015-duongth5-done.txt";
        String filenameUA = "data2/useragent.txt";
        String type = "m.facebook.com";
        String ip = "83";

        List<Account> accounts = readAccounts2(filename, false);
        List<String> useragents = readUserAgents(filenameUA);
        Random rand = new Random();
        for (Account account : accounts) {
            int pos = rand.nextInt(useragents.size());
            String userAgent = useragents.get(pos);
            String format = "%s\t%s\t%s\t%s\t%s";
            System.out.println(String.format(format, type, account.getUsername(), account.getPassword(), userAgent, ip));
            ServiceOutlinks.pushAccountToMaster(type, account.getUsername(), account.getPassword(), userAgent, ip);
        }
    }

    private static List<Account> readAccounts2(String filename, boolean hasUserAgent) {
        List<Account> accounts = new ArrayList<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = temp.split("\t");
                    Account acc = new Account();
                    acc.setUsername(parts[4]);
                    acc.setPassword(parts[6]);
                    if (hasUserAgent) {
                        acc.setUserAgent(parts[2]);
                    }
                    accounts.add(acc);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return accounts;
    }

    private static List<Account> readAccounts(String filename, boolean hasUserAgent) {
        List<Account> accounts = new ArrayList<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = temp.split("\t");
                    Account acc = new Account();
                    acc.setUsername(parts[0]);
                    acc.setPassword(parts[1]);
                    if (hasUserAgent) {
                        acc.setUserAgent(parts[2]);
                    }
                    accounts.add(acc);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return accounts;
    }

    private static List<String> readUserAgents(String filename) {
        List<String> useragents = new ArrayList<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    useragents.add(temp);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return useragents;
    }

    private static void doGenList() {
        String filename5K = "data2/accounts/full-5k.txt";
        String filename9K = "data2/accounts/full-9k.txt";
        String filenameCreated = "data2/accounts/full-accs-created.txt";
        String filenameNotCreate = "data2/accounts/full-accs-not-create.txt";

        List<FakeAccount> list5Ks = readFakeAccounts(filename5K);
        List<FakeAccount> list9Ks = readFakeAccounts(filename9K);
        List<FakeAccount> listCreateds = readFakeAccounts(filenameCreated);
        List<FakeAccount> listNotCreateds = readFakeAccounts(filenameNotCreate);

        List<FakeAccount> listGen = new ArrayList<>();
        List<FakeAccount> listReCheck = new ArrayList<>();

        for (FakeAccount list9K : list9Ks) {
            int isExisted = 0;
            String mobile = list9K.mobile;

            for (FakeAccount list5K : list5Ks) {
                if (StringUtils.equals(mobile, list5K.mobile)) {
                    isExisted++;
                    break;
                }
            }

            for (FakeAccount listCreated : listCreateds) {
                if (StringUtils.equals(mobile, listCreated.mobile)) {
                    isExisted++;
                    break;
                }
            }
            if (isExisted == 0) {
                listGen.add(list9K);
            } else if (isExisted > 1) {
                listReCheck.add(list9K);
            }

        }

        for (FakeAccount fakeUser : listGen) {
            String format = "%s\t%s\t%s\t%s\t%s";
            System.out.println(String.format(format, fakeUser.lastName, fakeUser.firstName, fakeUser.address, fakeUser.dob, fakeUser.mobile));
        }

        System.out.println("####################################");
        System.out.println("");

        if (listReCheck.isEmpty()) {
            System.out.println("List recheck empty");
        } else {
            for (FakeAccount fakeUser : listReCheck) {
                String format = "%s\t%s\t%s\t%s\t%s";
                System.out.println(String.format(format, fakeUser.lastName, fakeUser.firstName, fakeUser.address, fakeUser.dob, fakeUser.mobile));
            }
        }
    }

    private static void doShuffleListAccounts() {
        String filename = "data2/accounts/full-the-rest-ordered.txt";
        List<FakeAccount> listTheRests = readFakeAccounts(filename);
        Collections.shuffle(listTheRests);
        for (FakeAccount fakeUser : listTheRests) {
            String format = "%s\t%s\t%s\t%s\t%s";
            System.out.println(String.format(format, fakeUser.lastName, fakeUser.firstName, fakeUser.address, fakeUser.dob, fakeUser.mobile));
        }
    }

    private static List<FakeAccount> readFakeAccounts(String filename) {
        List<FakeAccount> fas = new ArrayList<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    String[] parts = StringUtils.split(temp, "\t");
                    FakeAccount fa = new FakeAccount();
                    fa.lastName = parts[0];
                    fa.firstName = parts[1];
                    fa.address = parts[2];
                    fa.dob = parts[3];
                    fa.mobile = parts[4];
                    fas.add(fa);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return fas;
    }

    private static class FakeAccount {

        public String lastName;
        public String firstName;
        public String address;
        public String dob;
        public String mobile;
    }
}
