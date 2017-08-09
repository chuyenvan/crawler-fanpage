package viettel.nfw.social.facebook.nologin;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class WorkerThread implements Runnable {

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
        long endTime = startTime + RunNoLogin.phantomjsTimeToLive;

        while (true) {
            try {
                long currentTime = System.currentTimeMillis();
                if (currentTime > endTime) {
                    stopDriver(driver);
                    Funcs.sleep(2000);
                    // re-init driver
                    driver = startDriver(proxyString, userAgent);
                    startTime = System.currentTimeMillis();
                    endTime = startTime + RunNoLogin.phantomjsTimeToLive;
                    Funcs.sleep(2000);
                } else {
                    Funcs.sleep(500);
                }

                String nextUrl = RunNoLogin.toCrawlUrl.poll();
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
                        endTime = startTime + RunNoLogin.phantomjsTimeToLive;
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
                        // FileUtils.write(new File("storage/security/file_" + String.valueOf(System.currentTimeMillis())), driver.getPageSource());
                        String rewriteUrl = rewriteHostWebToMobile(nextUrl);
                        if (StringUtils.isNotEmpty(rewriteUrl)) {
                            ServiceOutlinks.addOutlinkPriority(rewriteUrl, true);
                        }
                        stopDriver(driver);
                        Thread.sleep(2000);

                        // re-init driver
                        driver = startDriver(proxyString, userAgent);
                        startTime = System.currentTimeMillis();
                        endTime = startTime + RunNoLogin.phantomjsTimeToLive;
                        Thread.sleep(2000);
                        continue;
                    }

                    // check redirect to home page -> send current crawl link to priority queue, clear all cookies
                    if (StringUtils.contains(pageTitle, "Page Not Found")
                            || StringUtils.contains(pageTitle, "Welcome to Facebook")
                            || StringUtils.contains(pageTitle, "Chào mừng bạn đến với Facebook")) {
                        try {
                            LOG.warn("Redirect to homepage - {}", nextUrl);
                            String rewriteUrl = rewriteHostWebToMobile(nextUrl);
                            if (StringUtils.isNotEmpty(rewriteUrl)) {
                                ServiceOutlinks.addOutlinkPriority(rewriteUrl, true);
                            }
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
                            String rewriteUrl = rewriteHostWebToMobile(nextUrl);
                            if (StringUtils.isNotEmpty(rewriteUrl)) {
                                ServiceOutlinks.addOutlinkPriority(rewriteUrl, true);
                            }
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                        continue;
                    }

                    String currentUrl = driver.getCurrentUrl();
                    currentUrl = normProfileUrl(currentUrl);
                    LOG.info("Start crawl: {}", currentUrl);

                    // query all url
                    Set<String> urls = new HashSet<>();
                    List<WebElement> aTags = driver.findElementsByTagName("a");
                    for (WebElement aTag : aTags) {
                        try {
                            String url = aTag.getAttribute("href");
                            if (StringUtils.isNotEmpty(url)) {
                                if (!StringUtils.startsWith(url, currentUrl)) {
                                    urls.add(url);
                                }
                            }
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    }

                    for (String url : urls) {
                        RunNoLogin.toSendOutlinks.add(url);
                    }

                    try {
                        if (profileType.equals(ProfileType.PAGE)) {
                            String pageSource = driver.getPageSource();
                            // add raw HTML to parse and push to bigdata
                            RunNoLogin.crawledDataQueue.add(pageSource);
                            // write to file
                            String filename = String.format(RunNoLogin.FORMAT_DATA_FILENAME,
                                    String.valueOf(currentUrl.hashCode()),
                                    String.valueOf(System.currentTimeMillis()));
                            String fileStorage = RunNoLogin.DIR_STORAGE + filename;
                            FileUtils.write(new File(fileStorage), pageSource);
                            LOG.info("DONE Write to file: {}", currentUrl);

                            // change host to mobile
                            String mobileUrl = rewriteHostWebToMobile(currentUrl);
                            if (StringUtils.isNotEmpty(mobileUrl)) {
                                ServiceOutlinks.addCrawledUrl(RunNoLogin.USERNAME_DF, mobileUrl);
                            }
                        }
                        RunNoLogin.crawledUrlsQueue.add(currentUrl);
                        LOG.info("DONE crawl: {}", currentUrl);
                    } catch (IOException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }

                    Thread.sleep(1000);
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    private static String rewriteHostWebToMobile(String url) {
        String mobileUrl = "";
        if (StringUtils.contains(url, "//www.facebook.com/")) {
            mobileUrl = StringUtils.replace(url, "//www.facebook.com/", "//m.facebook.com/");
        } else if (StringUtils.contains(url, "//vi-vn.facebook.com/")) {
            mobileUrl = StringUtils.replace(url, "//vi-vn.facebook.com/", "//m.facebook.com/");
        }
        return mobileUrl;
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

    private static final String[] UNEXPECTED_PATH = new String[]{
        "/photo.php", "/r.php", "/policies", "/pages/create/", "/l.php", "/video.php", "/login.php",
        "/permalink.php", "/profile.php", "/facebook", "/"

    };

    public static FacebookUrlType filterUrl(String url, String currentProfileUrl) {
        FacebookUrlType urlType = FacebookUrlType.UNDEFINED;

        try {
            if (StringUtils.isNotEmpty(currentProfileUrl)) {
                if (StringUtils.startsWith(url, currentProfileUrl)) {
                    return urlType;
                }
            }

            URI uri = new URI(url);
            String host = uri.getHost();
            String path = uri.getPath();
            String fragment = uri.getFragment();

            if (StringUtils.equalsIgnoreCase(host, "l.facebook.com")) {
                return FacebookUrlType.FB_OUTSIDE_LINK;
            } else if (StringUtils.equalsIgnoreCase(host, "www.facebook.com")
                    || StringUtils.equalsIgnoreCase(host, "vi-vn.facebook.com")) {

                Pattern patternProfilePath = Pattern.compile("^(/)([0-9a-zA-Z\\.]+)$");
                Matcher matcherProfilePath = patternProfilePath.matcher(path);
                boolean matchesProfilePath = matcherProfilePath.matches();
                if (matchesProfilePath
                        || StringUtils.startsWith(path, "/people/")
                        || StringUtils.startsWith(path, "/pages/")) {
                    for (String unExpect : UNEXPECTED_PATH) {
                        if (StringUtils.equalsIgnoreCase(path, unExpect)) {
                            return urlType;
                        }
                    }
                    return FacebookUrlType.PROFILE;
                }
            } else if (StringUtils.equalsIgnoreCase(host, "developers.facebook.com")
                    || StringUtils.equalsIgnoreCase(host, "messenger.com")) {
                return FacebookUrlType.BY_FB;
            } else {
                LOG.info("Foreign {}", url);
                return FacebookUrlType.PROFILE_FOREIGN;
            }

        } catch (URISyntaxException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        return urlType;
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
