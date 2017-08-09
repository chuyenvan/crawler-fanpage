package viettel.nfw.social.facebook.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.utils.AsyncFileWriter;
import viettel.nfw.social.utils.CustomPartition;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class AnalysisCrawledProfiles {

    private static final Logger LOG = LoggerFactory.getLogger(AnalysisCrawledProfiles.class);

    private static final int MAX_CAPACITY = 5000000;
    public static final String USER_AGENT_DF = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:33.0) Gecko/20100101 Firefox/33.0";
    public static String proxyString = null;
    public static int numberPhantomjsDriver = 25; // default is 5 phantomjs drivers
    public static long phantomjsTimeToLive = 4 * 60 * 60 * 1000; // default is 4 hours
    private static final String PREFIX_CRAWLER_THREAD = "Crawler_";

    public static BlockingQueue<String> toCrawlUrl = new ArrayBlockingQueue<>(MAX_CAPACITY);
    public static BlockingQueue<String> crawledUrlsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);

    public static void main(String[] args) {

        filtedCrawledProfiles();
//        splitCrawledProfile();
//        loadUrlToCrawl();
//        Funcs.sleep(5000);
//        WriteCrawledUrlsImpl writerImple = new WriteCrawledUrlsImpl();
//        new Thread(writerImple).start();
//        doJob(proxyString);
    }

    private static void loadUrlToCrawl() {
        String filename = "unclearProfiles.txt";
        Set<String> rows = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    rows.add(line);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (String row : rows) {
            toCrawlUrl.add(row);
        }
    }

    private static void doJob(String proxyString) {
        for (int i = 0; i < numberPhantomjsDriver; i++) {
            String threadName = PREFIX_CRAWLER_THREAD + i;
            WorkerThread worker = new WorkerThread(proxyString, USER_AGENT_DF, threadName);
            new Thread(worker).start();
        }
    }

    private static class WriteCrawledUrlsImpl implements Runnable {

        public static AsyncFileWriter afwCrawledUrls;

        @Override
        public void run() {
            Thread.currentThread().setName("WriteCrawledUrlsImpl");
            try {
                afwCrawledUrls = new AsyncFileWriter(new File("result.txt"));
                afwCrawledUrls.open();
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            while (true) {
                try {
                    String url = crawledUrlsQueue.poll();
                    if (StringUtils.isEmpty(url)) {
                        Thread.sleep(1000);
                    } else {
                        afwCrawledUrls.append(url + "\n");
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private static class WorkerThread implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(WorkerThread.class);

        private final String proxyString;
        private final String userAgent;
        private final String threadName;

        private static final int MAX_RETRY_GET = 2;

        private static enum ProfileType {

            PAGE, USER, UNKNOWN, REQUIRE_LOGIN
        }

        public static enum FacebookUrlType {

            UNDEFINED, BY_FB, PROFILE, FB_OUTSIDE_LINK, PROFILE_FOREIGN
        }

        public WorkerThread(String proxyString, String userAgent, String threadName) {
            this.proxyString = proxyString;
            this.userAgent = userAgent;
            this.threadName = threadName;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(threadName);
            doJob(proxyString, userAgent);
        }

        private static PhantomJSDriver startDriver(String proxyString, String userAgent) {
            DesiredCapabilities cap = DesiredCapabilities.phantomjs();
            if (proxyString != null) {
                org.openqa.selenium.Proxy p = new org.openqa.selenium.Proxy();
                p.setHttpProxy(proxyString).setFtpProxy(proxyString).setSslProxy(proxyString);
                cap.setCapability(CapabilityType.PROXY, p);
            }
            cap.setJavascriptEnabled(true);
            cap.setCapability("phantomjs.page.settings.userAgent", userAgent);

            PhantomJSDriver driver = new PhantomJSDriver(cap);
            try {
                driver.manage().deleteAllCookies();
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
            driver.manage().timeouts().pageLoadTimeout(1, TimeUnit.MINUTES);
            driver.manage().timeouts().setScriptTimeout(40, TimeUnit.SECONDS);
            driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

            // set language for Facebook
            try {
                try {
                    driver.get("https://www.facebook.com/");
                } catch (TimeoutException ex) {
                    driver.navigate().refresh();
                }

                List<WebElement> languageATags = driver.findElements(By.xpath("//a[@title]"));
                for (WebElement languageATag : languageATags) {
                    String language = languageATag.getAttribute("title");
                    if (StringUtils.isEmpty(language)) {
                        language = languageATag.getText();
                    }
                    if (StringUtils.contains(language, "English (US)")) {
                        // By click this link, language will set to English in cookies
                        languageATag.click();
                        LOG.info("[startDriver] DONE set Facebook to English");
                        break;
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }

            return driver;
        }

        private static void stopDriver(PhantomJSDriver driver) {
            try {
                driver.quit();
            } catch (Exception ex) {
                LOG.error("Error in Stop Driver. Maybe, it's already dead!", ex);
            }
        }

        private static void doJob(String proxyString, String userAgent) {

            PhantomJSDriver driver = startDriver(proxyString, userAgent);
            long startTime = System.currentTimeMillis();
            long endTime = startTime + phantomjsTimeToLive;

            while (true) {
                try {
                    long currentTime = System.currentTimeMillis();
                    if (currentTime > endTime) {
                        stopDriver(driver);
                        Funcs.sleep(2000);
                        // re-init driver
                        driver = startDriver(proxyString, userAgent);
                        startTime = System.currentTimeMillis();
                        endTime = startTime + phantomjsTimeToLive;
                        Funcs.sleep(2000);
                    } else {
                        Funcs.sleep(500);
                    }

                    String nextUrl = toCrawlUrl.poll();
                    if (StringUtils.isEmpty(nextUrl)) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    } else {
                        LOG.info("Get link crawl: {}", nextUrl);
                        try {
                            driver.get(nextUrl);
                        } catch (TimeoutException ex) {
                            driver.navigate().refresh();
                        } catch (UnreachableBrowserException ex) {
                            stopDriver(driver);
                            Thread.sleep(2000);

                            // re-init driver
                            driver = startDriver(proxyString, userAgent);
                            startTime = System.currentTimeMillis();
                            endTime = startTime + phantomjsTimeToLive;
                            Thread.sleep(2000);
                            continue;
                        }

                        Thread.sleep(600);

                        String pageTitle = driver.getTitle();
                        LOG.info("Title {}", pageTitle);

                        // check redirect to security check page -> send current crawl link to priority queue, quit driver, init new once
                        if (StringUtils.contains(pageTitle, "Yêu cầu kiểm tra bảo mật")
                                || StringUtils.contains(pageTitle, "Security Check Required")) {
                            LOG.warn("Security Check - {}", nextUrl);
                            String result = "SECURITY_CHECK" + "\t" + nextUrl + "\t" + nextUrl + "\t" + pageTitle;
                            crawledUrlsQueue.add(result);

                            stopDriver(driver);
                            Thread.sleep(2000);

                            // re-init driver
                            driver = startDriver(proxyString, userAgent);
                            startTime = System.currentTimeMillis();
                            endTime = startTime + phantomjsTimeToLive;
                            Thread.sleep(2000);
                            continue;
                        }

                        // check redirect to home page -> send current crawl link to priority queue, clear all cookies
                        if (StringUtils.contains(pageTitle, "Page Not Found")
                                || StringUtils.contains(pageTitle, "Welcome to Facebook")
                                || StringUtils.contains(pageTitle, "Chào mừng bạn đến với Facebook")) {
                            try {
                                LOG.warn("Redirect to homepage - {}", nextUrl);
                                String result = "NOT_FOUND" + "\t" + nextUrl + "\t" + nextUrl + "\t" + pageTitle;
                                crawledUrlsQueue.add(result);
                            } catch (Exception ex) {
                                LOG.error(ex.getMessage(), ex);
                            }
                            continue;
                        }

                        ProfileType profileType = identifyProfileType(driver);
                        LOG.info("Type {}", profileType);
                        if (profileType.equals(ProfileType.REQUIRE_LOGIN)) {
                            try {
                                LOG.warn("Require Login - {}", nextUrl);
                                String result = profileType + "\t" + nextUrl + "\t" + nextUrl + "\t" + pageTitle;
                                crawledUrlsQueue.add(result);
                            } catch (Exception ex) {
                                LOG.error(ex.getMessage(), ex);
                            }
                            continue;
                        }

                        String currentUrl = driver.getCurrentUrl();
                        currentUrl = normProfileUrl(currentUrl);
                        LOG.info("Start crawl: {}", currentUrl);

                        String result = profileType + "\t" + nextUrl + "\t" + currentUrl + "\t" + pageTitle;
                        crawledUrlsQueue.add(result);

                        Thread.sleep(1000);
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }

        private static ProfileType identifyProfileType(PhantomJSDriver driver) {

            String currentUrl = driver.getCurrentUrl();
            if (StringUtils.contains(currentUrl, "/login.php?")) {
                try {
                    URI uri = new URI(currentUrl);
                    Map<String, List<String>> params = Parser.splitQuery(uri);
                    if (MapUtils.isNotEmpty(params)) {
                        if (params.containsKey("next")) {
                            return ProfileType.REQUIRE_LOGIN;
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }

            ProfileType profileType = ProfileType.UNKNOWN;
            List<WebElement> itemTypeDivs = driver.findElements(By.xpath("//div[@id='content']/div[@itemtype]"));
            if (!itemTypeDivs.isEmpty()) {
                String itemTypeAttr = itemTypeDivs.get(0).getAttribute("itemtype");
                LOG.info("FOUND: {}", itemTypeAttr);
                if (StringUtils.contains(itemTypeAttr, "http://schema.org/Organization")
                        || StringUtils.contains(itemTypeAttr, "http://schema.org/Restaurant")) {
                    profileType = ProfileType.PAGE;
                } else if (StringUtils.contains(itemTypeAttr, "http://schema.org/Person")) {
                    profileType = ProfileType.USER;
                }
            } else {
                boolean isOk = false;
                List<WebElement> fbProfileCoverDivs = driver.findElements(By.id("fbProfileCover"));
                if (!fbProfileCoverDivs.isEmpty()) {
                    WebElement fbProfileCoverDiv = fbProfileCoverDivs.get(0);
                    List<WebElement> aTags = fbProfileCoverDiv.findElements(By.tagName("a"));
                    for (WebElement aTag : aTags) {
                        String href = aTag.getAttribute("href");
                        String text = aTag.getText();
                        if (StringUtils.contains(href, "/likes?")
                                && (StringUtils.equalsIgnoreCase(text, "Likes") || StringUtils.equalsIgnoreCase(text, "Thích"))) {
                            profileType = ProfileType.PAGE;
                            isOk = true;
                            break;
                        }
                    }
                }

                if (!isOk) {
                    List<WebElement> hasIdDivs = driver.findElements(By.xpath("//div[@id]"));
                    if (!hasIdDivs.isEmpty()) {
                        for (WebElement hasIdDiv : hasIdDivs) {
                            String id = hasIdDiv.getAttribute("id");
                            if (StringUtils.startsWithIgnoreCase(id, "PagePostsPagelet")) {
                                profileType = ProfileType.PAGE;
                                isOk = true;
                                break;
                            }
                        }
                    }
                }

                if (!isOk) {
                    LOG.warn("NOT FOUND - {}", currentUrl);
                }
            }

            return profileType;
        }

        public static String normProfileUrl(String url) {
            String normUrl = url;
            try {
                URI baseUri = new URI(url);
                String path = baseUri.getPath();

                URI resolveUrl = baseUri.resolve(path);
                normUrl = resolveUrl.toString();

            } catch (URISyntaxException ex) {
                LOG.error(ex.getMessage(), ex);
            }
            return normUrl;
        }

    }

    private static void filtedCrawledProfiles() {
        String filename = "data2/update-news/crawled_20150623.profiles";
        Set<String> rows = new HashSet<>();

        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (StringUtils.startsWithIgnoreCase(line, "p:https://m.facebook.com/")) {
                        rows.add(line);
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        Set<String> groups = new HashSet<>();
        Set<String> pages = new HashSet<>();
        Set<String> unclearProfiles = new HashSet<>();
        for (String row : rows) {
            String[] parts = StringUtils.split(row.trim(), "|");
            String firstPos = parts[0];
            String profileUrl = StringUtils.substring(firstPos, 2);
            if (StringUtils.contains(profileUrl, "/pages/")) {
                pages.add(profileUrl);
            } else if (StringUtils.contains(profileUrl, "/groups/")) {
                groups.add(profileUrl);
            } else {
                unclearProfiles.add(profileUrl);
            }
        }

        try {
            FileUtils.write(new File("data2/update-news/news/pages.txt"), pages);
            FileUtils.write(new File("data2/update-news/news/groups.txt"), groups);
            FileUtils.write(new File("data2/update-news/news/unclearProfiles.txt"), unclearProfiles);
        } catch (FileNotFoundException ex) {
            LOG.error(ex.getMessage(), ex);
        }

    }

    private static void splitCrawledProfile() {
        String allFilename = "unclearProfiles.txt";
        Set<String> rows = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(allFilename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    rows.add(line);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        String filename = "result_13.txt";
        Set<String> crawleds = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = StringUtils.split(line, "\t");
                    crawleds.add(parts[1]);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        List<String> uncrawleds = new ArrayList<>();
        for (String row : rows) {
            if (crawleds.contains(row)) {
                continue;
            }
            uncrawleds.add(row);
        }
        System.out.println(uncrawleds.size());

        List<List<String>> partition = CustomPartition.partition(uncrawleds, 960000);

        int i = 0;
        for (List<String> item : partition) {
            try {
                FileUtils.write(new File("unclearProfiles_" + i + ".txt"), item);
                i++;
            } catch (FileNotFoundException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }

    }
}
