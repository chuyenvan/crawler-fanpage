package viettel.nfw.social.common;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.Constant;

/**
 *
 * @author duongth5
 */
public class QueryFakeAccount {

    private static final Logger LOG = LoggerFactory.getLogger(QueryFakeAccount.class);

    private static List<Account> getAllFakeAccounts() {

        List<Account> allFakeAccs = new ArrayList<>();

        // crawl page master account
        String response = ServiceOutlinks.getAccountPage();
        if (StringUtils.isEmpty(response)) {
            return allFakeAccs;
        }
        LOG.debug(response);

        // parse this page
        Document doc = Jsoup.parse(response);
        Elements trs = doc.getElementsByTag("tr");
        for (Element tr : trs) {
            Elements tds = tr.getElementsByTag("td");

            String type = tds.get(0).text();
            String username = tds.get(1).text();
            String password = tds.get(2).text();
            String useragent = tds.get(3).text();
            String serverIp = tds.get(4).text();
            String addedTimeStr = tds.get(5).text();

            // convert addedTime string to time in millisecond
            long addedTime = 0;
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(Constant.DATE_FORMAT_DEFAULT);
                Date addedDate = sdf.parse(addedTimeStr);
                addedTime = addedDate.getTime();
            } catch (ParseException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            if (addedTime != 0) {
                Account acc = new Account(type, username, password, useragent, serverIp, addedTime);
                allFakeAccs.add(acc);
            }
        }

        return allFakeAccs;
    }

    private static List<Account> getAllLockedAccount() {

        List<Account> returnAccounts = new ArrayList<>();
        try {
            String response = ServiceOutlinks.getLockPage();

            List<Account> fullAccounts = new ArrayList<>();
            fullAccounts.addAll(getBySocialType("m.facebook.com"));

            Document doc = Jsoup.parse(response);
            Elements trs = doc.getElementsByTag("tr");
            for (Element tr : trs) {
                Elements tds = tr.getElementsByTag("td");
                String account = tds.get(0).text();
                String timeStr = tds.get(1).text();
                String message = tds.get(2).text();

                for (Account fullAccount : fullAccounts) {
                    if (StringUtils.equals(account, fullAccount.getUsername())) {
                        returnAccounts.add(fullAccount);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return returnAccounts;
    }

    public static List<Account> getByIp(String serverIp, String socialType) {
        List<Account> results = new ArrayList<>();

        List<Account> allFakeAccs = getAllFakeAccounts();
        for (Account fakeAcc : allFakeAccs) {
            if (StringUtils.equalsIgnoreCase(serverIp, fakeAcc.getServerIp())
                    && StringUtils.equalsIgnoreCase(socialType, fakeAcc.getAccType())) {
                results.add(fakeAcc);
            }
        }

        return results;
    }

    public static List<Account> getBySocialType(String socialType) {
        List<Account> results = new ArrayList<>();

        List<Account> allFakeAccs = getAllFakeAccounts();
        for (Account fakeAcc : allFakeAccs) {
            if (StringUtils.equalsIgnoreCase(socialType, fakeAcc.getAccType())) {
                results.add(fakeAcc);
            }
        }

        return results;
    }

    public static void main(String[] args) {
//        List<Account> fullAccounts = new ArrayList<>();
//        fullAccounts.addAll(getBySocialType("m.facebook.com"));
//        
//        for (Account fullAccount : fullAccounts) {
//            String username = fullAccount.getUsername();
//            if (!StringUtils.startsWith(username, "098000")) {
//                System.out.println(fullAccount.getUsername() + "\t" + fullAccount.getPassword() + "\t" + fullAccount.getUserAgent());
//            }
//        }

        List<Account> fullAccounts = new ArrayList<>();
        fullAccounts.addAll(getBySocialType("plus.google.com"));

        for (Account fullAccount : fullAccounts) {
            System.out.println(fullAccount.getUsername() + "\t" + fullAccount.getPassword() + "\t" + fullAccount.getUserAgent());
        }
    }
}
