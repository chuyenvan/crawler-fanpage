package viettel.nfw.social.facebook.updatenews.old;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.CookieManager;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.gfw.vn.producer.producer.MessageInfo;
import viettel.gfw.vn.producer.producer.ProducerORMWeb;
import viettel.gfw.vn.social.FaceBookProto;
import viettel.gfw.vn.social.FaceBookProtoTrans;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.facebook.core.FacebookAction;
import static viettel.nfw.social.facebook.core.FacebookAction.crawl;
import viettel.nfw.social.facebook.core.FacebookMessage;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.reviewdata.ParsingUtils;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.social.fb.test.FbUrlToHtml;

/**
 *
 * @author duongth5
 */
public class RunUpdateNewsOld {

    private static final Logger LOG = LoggerFactory.getLogger(RunUpdateNewsOld.class);

    private static final int MAX_CAPACITY = 3000000;
    private static final Proxy proxy = null;
    public static BlockingQueue<String> urlsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
    public static BlockingQueue<FacebookObject> facebookObjectQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);

    public static final String DIR_PARSED = "result/reviewdata/parsed/";
    public static final String DIR_SENSITIVE = "result/reviewdata/sensitive/";
    public static final String FORMAT_PARSED_FILENAME = "parsed_files_%s.txt";
    public static final String FORMAT_SENSITIVE_FILENAME = "sensitive_profile_%s.txt";

    public static void main(String[] args) {

        ManagePageGroupUrls manageImple = new ManagePageGroupUrls();
        new Thread(manageImple).start();

        // Thread send facebook object to bigdata
        SendObjectToBGImpl sendObjectToBGImpl = new SendObjectToBGImpl();
        Thread sendObjectToBGThread = new Thread(sendObjectToBGImpl);
        sendObjectToBGThread.start();

        List<Account> accounts = loadAccounts();
        List<CrawlerNews> crawlerRunnables = new ArrayList<>();

        // start loop account
        for (Account account : accounts) {

            boolean isLoginOK = true;
            String username = account.getUsername();

            // do login
            CookieManager cookieManager = new CookieManager();
            FacebookAction crawler = new FacebookAction(account, cookieManager, proxy);

            LOG.info("Account {} start login", username);
            AccountStatus accStatus = crawler.login();
            if (!accStatus.equals(AccountStatus.LOGIN_OK)) {
                LOG.info("Account {} login failed", username);
                isLoginOK = false;
            }

            // check login
            if (isLoginOK) {
                LOG.info("Account {} login OK", username);
                CrawlerNews crawlerNewsImpl = new CrawlerNews(account, crawler);
                crawlerRunnables.add(crawlerNewsImpl);
                Thread crawlerThread = new Thread(crawlerNewsImpl);
                crawlerThread.start();
            }
        }

        // put url to crawl
        while (true) {

            String toCrawlUrl = urlsQueue.poll();
            if (StringUtils.isEmpty(toCrawlUrl)) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            } else {
                int threadsSize = crawlerRunnables.size();
                int hash = Math.abs(toCrawlUrl.hashCode());
                int mod = hash % threadsSize;
                crawlerRunnables.get(mod).toCrawlUrls.add(toCrawlUrl);
            }
        }

    }

    private static List<Account> loadAccounts() {
        List<Account> fbAccounts = new ArrayList<>();

        String filename = "input/facebook-accounts-updatenews.txt";
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
        return fbAccounts;
    }

    private static class SendObjectToBGImpl implements Runnable {

        private static final ProducerORMWeb producer = new ProducerORMWeb("orm_web");

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

    private static class CrawlerNews implements Runnable {

        private final Account account;
        private final FacebookAction fbAction;
        public BlockingQueue<String> toCrawlUrls = new ArrayBlockingQueue<>(MAX_CAPACITY);

        public CrawlerNews(Account account, FacebookAction fbAction) {
            this.account = account;
            this.fbAction = fbAction;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(account.getUsername());
            while (true) {
                String nextUrl = toCrawlUrls.poll();
                if (StringUtils.isEmpty(nextUrl)) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                } else {
                    try {
                        // do job Crawl
                        LOG.info("Start Crawl url {}", nextUrl);
                        trackingProfile(nextUrl);

                        Random random = new Random();
                        long delay = random.nextInt(5000) + 1000;
                        try {
                            Thread.sleep(delay);
                        } catch (InterruptedException ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                    }
                }
            }
        }

        public AccountStatus trackingProfile(String profileUrl) {

            AccountStatus retStatus = AccountStatus.ACTIVE;
            try {
                List<FbUrlToHtml> crawledData = new ArrayList<>();
                LOG.info(FacebookMessage.CRAWL_PROFILE_START, profileUrl);

                // crawl profile URL
                String response = crawl(profileUrl, fbAction.getHttp(), fbAction.getProxy());
                LOG.info("url: {} -- response: {}", profileUrl, response);
                AccountStatus responseKOT = Parser.verifyResponseHtml(profileUrl, response, true);
                Document profileHTMLDoc = Jsoup.parse(response);
                if (!responseKOT.equals(AccountStatus.ACTIVE)) {
                    LOG.warn("URL FAILED - {}", profileUrl);
                    retStatus = responseKOT;
                    return retStatus;
                }
                crawledData.add(new FbUrlToHtml(profileUrl, response, System.currentTimeMillis()));

                Map<String, String> foundProfileUrls = new HashMap<>();
                foundProfileUrls.putAll(Parser.findProfileUrls(profileUrl, response));

                List<String> timelineUrls = new ArrayList<>();
                timelineUrls.addAll(Parser.getUrls(profileUrl, response, null, "Timeline", 0));

                Set<String> fullStoryUrls = new HashSet<>();
                fullStoryUrls.addAll(Parser.getUrls(profileUrl, response, null, "Full Story", 0));
                if (fullStoryUrls.size() < 1) {
                    fullStoryUrls.addAll(Parser.getUrls(profileUrl, response, null, "Comment", 1));
                }

                if (!timelineUrls.isEmpty()) {
                    int count = 0;
                    while (timelineUrls.size() > 0) {
                        String timelineUrl = timelineUrls.remove(0);
                        LOG.info("timelineUrl - {}", timelineUrl);

                        if (!StringUtils.isEmpty(timelineUrl)) {
                            String tlResponse = crawl(timelineUrl, fbAction.getHttp(), fbAction.getProxy());
                            AccountStatus tlResponseKOT = Parser.verifyResponseHtml(timelineUrl, tlResponse, true);
                            if (!tlResponseKOT.equals(AccountStatus.ACTIVE)) {
                                writeCrawledData2(account.getUsername(), profileUrl, crawledData);

                                retStatus = responseKOT;
                                return retStatus;
                            }
                            crawledData.add(new FbUrlToHtml(timelineUrl, tlResponse, System.currentTimeMillis()));
                            foundProfileUrls.putAll(Parser.findProfileUrls(timelineUrl, tlResponse));

                            Set<String> tlFullStoryUrls = new HashSet<>();
                            tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Full Story", 0));
                            if (tlFullStoryUrls.size() < 1) {
                                tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Comment", 1));
                            }

                            if (!tlFullStoryUrls.isEmpty()) {
                                for (String tlFullStoryUrl : tlFullStoryUrls) {
                                    String fsResponse = crawl(tlFullStoryUrl, fbAction.getHttp(), fbAction.getProxy());
                                    AccountStatus fsResponseKOT = Parser.verifyResponseHtml(tlFullStoryUrl, fsResponse, true);
                                    if (!fsResponseKOT.equals(AccountStatus.ACTIVE)) {
                                        writeCrawledData2(account.getUsername(), profileUrl, crawledData);

                                        retStatus = fsResponseKOT;
                                        return retStatus;
                                    }
                                    crawledData.add(new FbUrlToHtml(tlFullStoryUrl, fsResponse, System.currentTimeMillis()));
                                    foundProfileUrls.putAll(Parser.findProfileUrls(tlFullStoryUrl, fsResponse));
                                }
                            }

                            if (count < 5) {
                                List<String> seeMoreTimelineUrls = new ArrayList<>();
                                seeMoreTimelineUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Show more", 0));
                                if (!seeMoreTimelineUrls.isEmpty()) {
                                    LOG.info("Show more size {}", seeMoreTimelineUrls.size());
                                    timelineUrls.add(seeMoreTimelineUrls.get(0));
                                    count++;
                                }
                            }
                        }
                    }
                } else {
                    List<String> showMoreUrls = new ArrayList<>();
                    showMoreUrls.addAll(Parser.getUrls(profileUrl, response, null, "Show More", 0));
                    int count = 0;
                    while (showMoreUrls.size() > 0) {
                        String timelineUrl = showMoreUrls.remove(0);
                        LOG.info("timelineUrl - {}", timelineUrl);

                        if (!StringUtils.isEmpty(timelineUrl)) {
                            String tlResponse = crawl(timelineUrl, fbAction.getHttp(), fbAction.getProxy());
                            AccountStatus tlResponseKOT = Parser.verifyResponseHtml(timelineUrl, tlResponse, true);
                            if (!tlResponseKOT.equals(AccountStatus.ACTIVE)) {
                                writeCrawledData2(account.getUsername(), profileUrl, crawledData);

                                retStatus = responseKOT;
                                return retStatus;
                            }
                            crawledData.add(new FbUrlToHtml(timelineUrl, tlResponse, System.currentTimeMillis()));
                            foundProfileUrls.putAll(Parser.findProfileUrls(timelineUrl, tlResponse));

                            Set<String> tlFullStoryUrls = new HashSet<>();
                            tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Full Story", 0));
                            if (tlFullStoryUrls.size() < 1) {
                                tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Comment", 1));
                            }
                            fullStoryUrls.addAll(tlFullStoryUrls);

                            if (count < 5) {
                                List<String> seeMoreTimelineUrls = new ArrayList<>();
                                seeMoreTimelineUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Show More", 0));
                                if (!seeMoreTimelineUrls.isEmpty()) {
                                    LOG.info("Show more size {}", seeMoreTimelineUrls.size());
                                    showMoreUrls.add(seeMoreTimelineUrls.get(0));
                                    count++;
                                }
                            }
                        }
                    }
                }

                if (!fullStoryUrls.isEmpty()) {
                    for (String fullStoryUrl : fullStoryUrls) {
                        String fsResponse = crawl(fullStoryUrl, fbAction.getHttp(), fbAction.getProxy());
                        AccountStatus fsResponseKOT = Parser.verifyResponseHtml(fullStoryUrl, fsResponse, true);
                        if (!fsResponseKOT.equals(AccountStatus.ACTIVE)) {
                            writeCrawledData2(account.getUsername(), profileUrl, crawledData);

                            retStatus = fsResponseKOT;
                            return retStatus;
                        }
                        crawledData.add(new FbUrlToHtml(fullStoryUrl, fsResponse, System.currentTimeMillis()));
                        foundProfileUrls.putAll(Parser.findProfileUrls(fullStoryUrl, fsResponse));
                    }
                }

                // collect profile URLs and crawled data
                writeCrawledData2(account.getUsername(), profileUrl, crawledData);
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
                retStatus = AccountStatus.ERROR_UNKNOWN;
            }

            return retStatus;
        }

        private static void writeCrawledData2(String username, String profileUrl, List<FbUrlToHtml> crawledData) {
            try {
                if (!crawledData.isEmpty()) {

                    LOG.info("crawledData size {} - profile {}", crawledData.size(), profileUrl);

                    FacebookObject fbObj = ParsingUtils.fromHtmltoFacebookObject(crawledData);
                    if (fbObj != null) {
                        LOG.info("JSON - {}", JSON.encode(fbObj));
                        facebookObjectQueue.add(fbObj);
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }

    }
}
