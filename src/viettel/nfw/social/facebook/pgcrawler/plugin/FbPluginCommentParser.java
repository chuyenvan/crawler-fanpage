package viettel.nfw.social.facebook.pgcrawler.plugin;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.nigma.engine.web.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import vn.viettel.social.utils.HttpResponseInfo;
import vn.viettel.social.utils.Utils;
import vn.viettel.social.utils.urlbuilder.UrlBuilder;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import viettel.nfw.social.model.plugin.PluginObject;
import viettel.nfw.social.model.plugin.PluginType;
import viettel.nfw.social.model.plugin.object.FacebookPluginComment;

/**
 * Facebook Plug-in Comments Parser
 *
 * @author duongth5
 * @version 1.0
 * @since 1.0
 */
public class FbPluginCommentParser {

    /**
     * Logger for FbPluginCommentParser class
     */
    private static final Logger LOG = LoggerFactory.getLogger(FbPluginCommentParser.class);

    /**
     * List of host that we already knew app_id for comments plug-in
     */
    private static final String WHITE_LIST_FILE_PATH = "/data/social-plugins/whitelist.txt";

    /**
     * List of host that we already knew that there is no app_id for comments plug-in
     */
    private static final String BLACK_LIST_FILE_PATH = "/data/social-plugins/blacklist.txt";
    private static final Set<String> blackSites = new HashSet<>();
    private static final Map<String, String> whiteSites = new HashMap<>();

    static {
        // readBlackSites
        try {
            try (BufferedReader blackSiteReader = new BufferedReader(new InputStreamReader(
                    Object.class.getResourceAsStream(BLACK_LIST_FILE_PATH)))) {
                String line;
                while ((line = blackSiteReader.readLine()) != null) {
                    blackSites.add(line.trim().toLowerCase());
                }
            }
        } catch (IOException ex) {
            LOG.error("Failed to read blacklist.txt file", ex);
        }
        try {
            try (BufferedReader whiteSiteReader = new BufferedReader(new InputStreamReader(
                    Object.class.getResourceAsStream(WHITE_LIST_FILE_PATH)))) {
                String line;
                while ((line = whiteSiteReader.readLine()) != null) {
                    String[] parts = line.split("|");
                    String hostname = parts[0].trim().toLowerCase();
                    String _appId = parts[1];
                    whiteSites.put(hostname, _appId);
                }
            }
        } catch (IOException ex) {
            LOG.error("Failed to read whitesite.txt file", ex);
        }
    }

    /**
     * Get Facebook application ID and original article URL from raw HTML
     *
     * @param articleUrl
     * @param rawResponseHtml
     * @return Facebook application ID and original article URL
     */
    public static Pair<String, String> getAppIdAndOriginal(String articleUrl, String rawResponseHtml) {
        Pair<String, String> res;
        String appId = null;
        String originArticleUrl = null;
        boolean willParseRawHtml = true;
        if (StringUtils.isEmpty(articleUrl)) {
            return null;
        }
        if (StringUtils.isEmpty(rawResponseHtml)) {
            return null;
        }
        URL inputUrl;
        try {
            inputUrl = new URL(articleUrl);
        } catch (MalformedURLException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
        String host = inputUrl.getHost().toLowerCase();
        if (blackSites.contains(host)) {
            return null;
        }
        if (whiteSites.containsKey(host)) {
            willParseRawHtml = false;
            appId = whiteSites.get(host);
        }
        // search for fb app_id in raw html
        Document rawHtml = Jsoup.parse(rawResponseHtml);
        if (willParseRawHtml) {
            Elements metalinks = rawHtml.select("meta[property=fb:app_id]");
            if (!metalinks.isEmpty()) {
                appId = metalinks.get(0).attr("content");
            } else {
                // try another way
            }
        }
        // search for fb data-href in raw html
        Elements divComments = rawHtml.select("div.fb-comments");
        if (!divComments.isEmpty()) {
            originArticleUrl = divComments.get(0).attr("data-href");
        } else {
            // try another way
        }
        if (StringUtils.isEmpty(appId)) {
            return null;
        }
        if (StringUtils.isEmpty(originArticleUrl)) {
            // not found origin
            originArticleUrl = articleUrl;
        }
        res = new Pair<>(appId, originArticleUrl);
        return res;
    }

    /**
     * Process crawl plug-in comments
     *
     * @param articleUrl
     * @param appId
     * @param originalArticleUrl
     * @param proxy
     * @return
     */
    public static PluginObject process(String articleUrl, String appId, String originalArticleUrl, Proxy proxy) {

        PluginObject fpObj = new PluginObject();

        String requestUrl = generateRequestUrl(appId, originalArticleUrl, "10");
        HttpResponseInfo responseGet = Utils.singleGet(requestUrl, proxy);
        Document responseDoc = Jsoup.parse(responseGet.getBody());
        Elements showMoreDivs = responseDoc.select("div.fbFeedbackTopLevelPager[id^=pager]");
        if (!showMoreDivs.isEmpty()) {
            int totalNumbers = 0;
            // if show more existed, find the number then make a request
            for (Element showMoreDiv : showMoreDivs) {
                Elements showMoreAs = showMoreDiv.select("a[href]");
                boolean foundNumber = false;
                if (!showMoreAs.isEmpty()) {
                    for (Element showMoreA : showMoreAs) {
                        String text = showMoreA.ownText();
                        String regexEnglish = "^(View )([0-9]+)( more)$";
                        String regexVietnamese = "^(Xem thêm )([0-9]+)( bài viết)$";
                        Pattern patternEnglish = Pattern.compile(regexEnglish);
                        Pattern patternVietnamese = Pattern.compile(regexVietnamese);
                        Matcher matcherEnglish = patternEnglish.matcher(text);
                        Matcher matcherVietnamese = patternVietnamese.matcher(text);

                        String number = "";
                        try {
                            if (matcherEnglish.matches()) {
                                number = matcherEnglish.group(2).trim();
                                int n = Integer.valueOf(number);
                                totalNumbers = 10 + n;
                                LOG.info("totalNumbers {}", totalNumbers);
                                foundNumber = true;
                                break;
                            } else if (matcherVietnamese.matches()) {
                                number = matcherVietnamese.group(2).trim();
                                int n = Integer.valueOf(number);
                                totalNumbers = 10 + n;
                                LOG.info("totalNumbers {}", totalNumbers);
                                foundNumber = true;
                                break;
                            }
                        } catch (NumberFormatException ex) {
                            LOG.error("Cannot parse string to int: {}. origin text {}", number, text);
                            LOG.error(ex.getMessage(), ex);
                        }
                    }
                    if (foundNumber) {
                        break;
                    }
                }
            }
            if (totalNumbers > 0) {
                // resend to get more comments
                String newRequestUrl = generateRequestUrl(appId, originalArticleUrl, String.valueOf(totalNumbers));
                HttpResponseInfo newResponseGet = Utils.singleGet(newRequestUrl, proxy);
                Document newResponseDoc = Jsoup.parse(newResponseGet.getBody());
                fpObj = parseComments(articleUrl, newResponseDoc);
            }
        } else {
            // else parse all ready html
            fpObj = parseComments(articleUrl, responseDoc);
        }

        return fpObj;
    }

    private static String generateRequestUrl(String appId, String originalArticleUrl, String numposts) {
        UrlBuilder requestUrl = UrlBuilder.empty()
                .withScheme("https")
                .withHost("www.facebook.com")
                .withPath("/plugins/comments.php")
                .addParameter("api_key", appId)
                .addParameter("href", originalArticleUrl)
                .addParameter("numposts", numposts);
        return requestUrl.toString();
    }

    /**
     * Parse detail comment content and information
     *
     * @param articleUrl
     * @param responseDoc
     * @param comm
     */
    private static PluginObject parseComments(String articleUrl, Document responseDoc) {

        List<FacebookPluginComment> comments = new ArrayList<>();
        Elements listFeedbackPosts = responseDoc.select("ul > li[id]");
        for (Element feedbackPost : listFeedbackPosts) {
            String commentId = feedbackPost.attr("id");
            Elements postContainers = feedbackPost.select("div.postContainer");
            if (!postContainers.isEmpty()) {
                Element postContainer = postContainers.get(0);
                Elements profileNameAs = postContainer.select("a.profileName");
                Elements postContentDivs = postContainer.select("div.postContent > div.postText");
                Elements postStatElems = postContainers.select("div.postContent > div.stat_elem");
                if (!profileNameAs.isEmpty() && !postContentDivs.isEmpty() && !postStatElems.isEmpty()) {
                    String hrefProfile = profileNameAs.get(0).attr("href");
                    String nameProfile = profileNameAs.get(0).text();
                    String content = postContentDivs.get(0).text();
                    String dateInSeconds = postStatElems.get(0).select("abbr").get(0).attr("data-utime");
                    String dateInSecondsStr = postStatElems.get(0).select("abbr").get(0).attr("title");

                    LOG.info("Article {} has comment id={} - hrefProfile={} - nameProfile={} - time={} - content={}",
                            new Object[]{articleUrl, commentId, hrefProfile, nameProfile, dateInSeconds, content});
                    LOG.info(dateInSecondsStr);

                    FacebookPluginComment comment = new FacebookPluginComment();
                    comment.setId(commentId);
                    comment.setContent(content);
                    comment.setProfileFullname(nameProfile);
                    comment.setProfileUrl(hrefProfile);
                    long tmp = Long.valueOf(dateInSeconds) * 1000L;
                    comment.setPublishedTime(new Date(tmp));

                    comments.add(comment);
                }
            }
        }

        PluginObject fpObj = new PluginObject();
        fpObj.setType(PluginType.FB_PLUGIN);
        fpObj.setCrawledUrl(articleUrl);
        fpObj.setCrawledTime(new Date());
        fpObj.setComments(comments);

        return fpObj;
    }

    public static void main(String[] args) {

        if (args.length > 0) {
            String articleUrl = args[0];
            String TEST_PATH = args[1];
            String rawResponseHtml = "";
            try {
                StringBuilder sb;
                try (BufferedReader testReader = new BufferedReader(new FileReader(TEST_PATH))) {
                    sb = new StringBuilder();
                    String line = testReader.readLine();
                    while (line != null) {
                        sb.append(line);
                        sb.append("\n");
                        line = testReader.readLine();
                    }
                }
                rawResponseHtml = sb.toString();
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            Pair<String, String> ret = FbPluginCommentParser.getAppIdAndOriginal(articleUrl, rawResponseHtml);
            if (ret != null) {
                String appId = ret.first;
                String originalArticleUrl = ret.second;
                FbPluginCommentParser.process(articleUrl, appId, originalArticleUrl, null);
            }
        } else {
            testInLocal();
        }
    }

    /**
     * FOR TEST in local machine
     */
    private static void testInLocal() {
        String PROXY_HOSTNAME = "192.168.4.13";
        int PROXY_PORT = 3128;
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOSTNAME, PROXY_PORT));

        String articleUrl = "http://motthegioi.vn/xa-hoi/vu-cong-phuong-ai-dung-sau-lung-chuyen-dong-24h-123367.html";
        String TEST_PATH = "E:\\motthegioi_cd_24h.txt";

        // String articleUrl = "http://kinhdoanh.vnexpress.net/tin-tuc/hang-hoa/gia-xang-giam-320-dong-xuong-muc-day-gan-4-nam-3117094.html";
        // String TEST_PATH = "E:\\vnexpress.txt";
        // String articleUrl = "http://19941703.tumblr.com/";
        // String TEST_PATH = "E:\\tumblr.txt";
        String rawResponseHtml = "";
        try {
            StringBuilder sb;
            try (BufferedReader testReader = new BufferedReader(new FileReader(TEST_PATH))) {
                sb = new StringBuilder();
                String line = testReader.readLine();
                while (line != null) {
                    sb.append(line);
                    sb.append("\n");
                    line = testReader.readLine();
                }
            }
            rawResponseHtml = sb.toString();
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        Pair<String, String> ret = FbPluginCommentParser.getAppIdAndOriginal(articleUrl, rawResponseHtml);
        if (ret != null) {
            String appId = ret.first;
            String originalArticleUrl = ret.second;
            FbPluginCommentParser.process(articleUrl, appId, originalArticleUrl, proxy);
        }
    }

}
