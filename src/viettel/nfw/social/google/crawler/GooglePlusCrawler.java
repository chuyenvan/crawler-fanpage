package viettel.nfw.social.google.crawler;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.CookieManager;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.nigma.engine.util.Funcs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.google.core.CommonParser;
import viettel.nfw.social.google.core.HttpRequest;
import viettel.nfw.social.google.core.PersonParser;
import viettel.nfw.social.google.entity.CrawledResult;
import viettel.nfw.social.google.utils.GooglePlusError;
import viettel.nfw.social.google.utils.GooglePlusMessage;
import viettel.nfw.social.google.utils.GooglePlusURL;
import viettel.nfw.social.utils.HttpResponseInfo;
import vn.itim.detector.LanguageDetector;
import vn.viettel.social.utils.consts.Html;
import vn.viettel.social.utils.consts.SCommon;

/**
 * Google Plus Crawler: login to Google Plus and crawl Google Plus profile URL.
 *
 * @author duongth5
 * @version 1.0
 * @since 1.0
 */
public class GooglePlusCrawler {

    /**
     * Logger for GooglePlusCrawler class
     */
    private static final Logger LOG = LoggerFactory.getLogger(GooglePlusCrawler.class);

    private static final String USER_AGENT_CHROME = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.111 Safari/537.36";

    /**
     * HTTP request
     */
    private final HttpRequest http;
    /**
     * Google account username
     */
    private final String loginAccGoogleUsername;
    /**
     * Google account password
     */
    private final String loginAccGooglePassword;
    /**
     * Proxy setting
     */
    private final Proxy proxy;
    /**
     * Cookie Manager for a session
     */
    private final CookieManager cookieManager;
    /**
     * Is login or not
     */
    private boolean isLogin;
    /**
     * Crawled Result: status, out-links.
     */
    private CrawledResult crawledResult;
    /**
     * Google Plus Error code
     */
    private GooglePlusError gpError;
    /**
     * User Agent for this account
     */
    private String userAgent;

    private LanguageDetector languageDetector;

    public LanguageDetector getLanguageDetector() {
        return languageDetector;
    }

    public void setLanguageDetector(LanguageDetector languageDetector) {
        this.languageDetector = languageDetector;
    }

    public GooglePlusCrawler(String loginAccGoogleUsername, String loginAccGooglePassword, Proxy proxy, CookieManager cookieManager) {
        this.loginAccGoogleUsername = loginAccGoogleUsername;
        this.loginAccGooglePassword = loginAccGooglePassword;
        this.proxy = proxy;
        this.isLogin = false;
        this.http = new HttpRequest(cookieManager);
        setCrawledResult(new CrawledResult());
        this.cookieManager = cookieManager;
        // set default user agent
        this.userAgent = USER_AGENT_CHROME;
        this.gpError = null;
    }

    public GooglePlusCrawler(String loginAccGoogleUsername, String loginAccGooglePassword, Proxy proxy, CookieManager cookieManager, String userAgent) {
        this.loginAccGoogleUsername = loginAccGoogleUsername;
        this.loginAccGooglePassword = loginAccGooglePassword;
        this.proxy = proxy;
        this.cookieManager = cookieManager;
        this.userAgent = userAgent;
        this.http = new HttpRequest(cookieManager, userAgent);
        this.isLogin = false;
        setCrawledResult(new CrawledResult());
        this.gpError = null;
    }

    public GooglePlusError login() {

        // reset cookie handler
        try {
            cookieManager.put(new URI("https://accounts.google.com/"), new HashMap<String, List<String>>());
            cookieManager.put(new URI("https://plus.google.com/"), new HashMap<String, List<String>>());

            long startTime = System.currentTimeMillis();

            // Login to Google Plus and keep sessions
            HttpResponseInfo responseGet = http.get(GooglePlusURL.LOGIN_AUTH_URL, HttpRequest.SOCIAL_TYPE_GOOGLE_PLUS, proxy);
            String postParams = getGooglePlusFormParams(responseGet.getBody(), loginAccGoogleUsername, loginAccGooglePassword);

            Funcs.sleep(2000);
            // Construct above post's content and then send a POST request for authentication
            HttpResponseInfo responsePost = http.post(GooglePlusURL.LOGIN_AUTH_URL, postParams, HttpRequest.SOCIAL_TYPE_GOOGLE_PLUS, null, null, proxy);

            if (responsePost.getStatus() == 302) {
                String newUrl = responsePost.getHeaders().get("Location").get(0);
                LOG.info(GooglePlusMessage.LOGIN_REDIRECT_URL, newUrl);

                // Check redirect URL
                // ***if it likes:
                // https://accounts.google.com/CheckCookie?checkedDomains=youtube&checkConnection=youtube:327:1&pstMsg=1
                // &chtml=LoginDoneHtml&continue=https://accounts.google.com/ManageAccount&gidl=CAA
                // --> login success
                URL redirectUrl = new URL(newUrl);
                Map<String, List<String>> query_pairs = Parser.splitQuery(redirectUrl);

                if (redirectUrl.getHost().equals(GooglePlusURL.HOST_ACCOUNTS_GOOGLE)
                        && redirectUrl.getPath().equals(GooglePlusURL.PATH_CHECK_COOKIE)
                        && query_pairs.get("continue").get(0).equals(GooglePlusURL.MANAGE_ACCOUNT)) {
                    // success login
                    this.isLogin = true;
                    LOG.info(GooglePlusMessage.LOGIN_SUCCESS);
                    gpError = GooglePlusError.LOGIN_SUCCESS;
                } else {
                    // login failed or account has been suspended
                    LOG.warn(GooglePlusMessage.LOGIN_FAILED);
                    gpError = GooglePlusError.LOGIN_FAILED;
                }
            } else {
                LOG.warn(GooglePlusMessage.LOGIN_POST_AUTHEN_NOT_RETURN_302);
                gpError = GooglePlusError.LOGIN_POST_AUTHEN_NOT_RETURN_302;
            }
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            LOG.info(GooglePlusMessage.PROCESS_LOGIN_TOTAL_TIME, totalTime);
        } catch (IOException | URISyntaxException e) {
            LOG.error(e.getMessage(), e);
            gpError = GooglePlusError.LOGIN_FAILED;
        }

        return gpError;
    }

    public void crawl(String profileUrl, int showMorePost) {

        if (!isLogin) {
            LOG.warn(gpError.toString());
            returnCrawledResult(gpError, profileUrl);
        } else {
            try {
                LOG.info(GooglePlusMessage.CRAWL_PROFILE_START, profileUrl);
                long startTime = System.currentTimeMillis();

                // process profile page
                String crawlJob = GooglePlusCrawlerJob.crawl(profileUrl, http, proxy);
                if (StringUtils.isEmpty(crawlJob)) {
                    LOG.debug("response NULL");
                    return;
                }
                Document profileDoc = Jsoup.parse(crawlJob);

                // extract profile ID from URL
                int indexOfLastSplash = profileUrl.lastIndexOf("/");
                String profileId = profileUrl.substring(indexOfLastSplash + 1);
                // detect profile type
                String objectType = CommonParser.identifyProfileType(profileId, profileDoc);
                LOG.debug("profile type is : " + objectType);
                if (StringUtils.isNotEmpty(objectType)) {
                    PersonParser personParser = new PersonParser(loginAccGoogleUsername, profileUrl, profileId, objectType, startTime, http, proxy);
                    personParser.setLanguageDetector(languageDetector);
                    CrawledResult result = personParser.parse(showMorePost);
                    setCrawledResult(result);
                } else {
                    // not detect this profile
                    gpError = GooglePlusError.CANNOT_DECTECT_PROFILE;
                    returnCrawledResult(gpError, profileUrl);
                }
            } catch (IOException | InterruptedException e) {
                gpError = GooglePlusError.CRAWL_PROFILE_FAILED;
                LOG.error(e.getMessage(), e);
                returnCrawledResult(gpError, profileUrl);
            }
        }
    }

    public CrawledResult getCrawledResult() {
        return crawledResult;
    }

    private void setCrawledResult(CrawledResult crawledResult) {
        this.crawledResult = crawledResult;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    /**
     * Return crawler result
     *
     * @param error error type
     * @param profileUrl profile URL
     */
    private void returnCrawledResult(GooglePlusError error, String profileUrl) {
        CrawledResult result = new CrawledResult();
        result.setErrorCode(error.getCode());
        result.setErrorDescription(error.getDescription() + " - " + profileUrl);
        result.setAccountCrawl(loginAccGoogleUsername);
        setCrawledResult(result);
    }

    /**
     * Get Google Plus Login Form
     *
     * @param html Raw HTML
     * @param username Google account username
     * @param password Google account password
     * @return String parameters
     */
    public static String getGooglePlusFormParams(String html, String username, String password) {

        String ret = "";
        LOG.debug("Extracting form's data ...");
        Document doc = Jsoup.parse(html);
        // Google form id
        Element loginform = doc.getElementById("gaia_loginform");
        if (loginform == null) {
            return ret;
        }
        Elements inputElements = loginform.getElementsByTag(Html.Tag.INPUT);
        List<String> paramList = new ArrayList<>();
        for (Element inputElement : inputElements) {
            String key = inputElement.attr(Html.Attribute.NAME);
            String value = inputElement.attr(Html.Attribute.VALUE);
            switch (key) {
                case "Email":
                    value = username;
                    break;
                case "Passwd":
                    value = password;
                    break;
            }
            try {
                paramList.add(key + "=" + URLEncoder.encode(value, SCommon.CHARSET_UTF_8));
            } catch (UnsupportedEncodingException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
        // Build parameters list
        StringBuilder result = new StringBuilder();
        for (String param : paramList) {
            if (result.length() == 0) {
                result.append(param);
            } else {
                result.append("&").append(param);
            }
        }
        ret = result.toString();
        LOG.debug(ret);
        return ret;
    }

}
