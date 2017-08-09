package viettel.nfw.social.facebook.core;

import viettel.nfw.social.facebook.htmlobj.FacebookCommentForm;
import viettel.nfw.social.facebook.htmlobj.FacebookFriendHoverCard;
import viettel.nfw.social.facebook.htmlobj.FacebookMyBookmarkMenu;
import viettel.nfw.social.facebook.htmlobj.FacebookHeaderBar;
import viettel.nfw.social.facebook.htmlobj.FacebookMyProfile;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.htmlobj.FacebookMyStatusForm;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.urlbuilder.UrlBuilder;

/**
 *
 * @author Duong
 */
public class Parser {

    private static final Logger LOG = LoggerFactory.getLogger(Parser.class);

    public static void getAllForms(URI uri, String rawHtml) {
        Document doc = Jsoup.parse(rawHtml);
        Elements formTags = doc.getElementsByTag("form");
        for (Element formTag : formTags) {
            System.out.println(formTag.toString());
        }
    }

    public static Map<URI, String> getAllLinksOfCurrentHtml(URI uri, String rawHtml) {
        Map<URI, String> mapUris = new HashMap<>();
        Document doc = Jsoup.parse(rawHtml);
        Element objectContainerTag = doc.getElementById("objects_container");
        if (objectContainerTag != null) {
            Elements aTags = objectContainerTag.getElementsByTag("a");
            for (Element aTag : aTags) {
                String text = aTag.text();
                String href = aTag.attr("href");
                URI hrefUri = uri.resolve(href);
                mapUris.put(hrefUri, text);
            }
        }
        return mapUris;
    }

    public static URI getLogOutUriInCurrentHtml(URI uri, String rawHtml) {
        URI logoutUri = null;
        Document doc = Jsoup.parse(rawHtml);
        Elements aTags = doc.getElementsByTag("a");
        for (Element aTag : aTags) {
            String text = aTag.text();
            String href = aTag.attr("href");
            if (StringUtils.isNotEmpty(text)) {
                URI hrefUri = uri.resolve(href);
                String hrefPath = hrefUri.getPath();
                if (StringUtils.contains(text, "Log Out")
                        && StringUtils.contains(hrefPath, "/logout.php")) {
                    logoutUri = hrefUri;
                    break;
                }
            }
        }
        return logoutUri;
    }

    public static FacebookFriendHoverCard parseFriendHoverCard(URI uri, String rawHtml) {
        FacebookFriendHoverCard fbFriendHoverCard = new FacebookFriendHoverCard();
        Document doc = Jsoup.parse(rawHtml);
        Element objectContainerTag = doc.getElementById("objects_container");
        if (objectContainerTag != null) {

            // find a div has attribute title
            Elements titleDivs = objectContainerTag.select("div[title]");
            if (!titleDivs.isEmpty()) {
                Element titleDiv = titleDivs.get(0);
                String title = titleDiv.attr("title");
                fbFriendHoverCard.friendTitle = title;
            }

            // find short desciptions about this friends
            Elements liTags = objectContainerTag.getElementsByTag("li");
            StringBuilder sb = new StringBuilder();
            for (Element liTag : liTags) {
                String text = StringUtils.replace(liTag.text(), "\u00a0", "").trim();
                if (StringUtils.isEmpty(text)) {
                    // ignore this
                } else {
                    sb.append(text).append(" . ");
                }
            }
            String description = sb.toString();
            fbFriendHoverCard.friendDescription = description;

            // find all links
            Map<String, URI> mapUris = new HashMap<>();
            Elements aTags = objectContainerTag.getElementsByTag("a");
            for (Element aTag : aTags) {
                String text = StringUtils.replace(aTag.text(), "\u00a0", "").trim();
                String href = aTag.attr("href");
                if (!StringUtils.isEmpty(text)) {
                    URI hrefUri = uri.resolve(href);
                    mapUris.put(text, hrefUri);
                }
            }
            fbFriendHoverCard.mapUris = mapUris;
        }
        return fbFriendHoverCard;
    }

    public static FacebookHeaderBar getHeaderBarOfCurrentHtml(URI uri, String rawHtml) {
        FacebookHeaderBar fbHearder = new FacebookHeaderBar();
        fbHearder.sourceUri = uri;
        Map<String, URI> mapUris = new HashMap<>();
        Document doc = Jsoup.parse(rawHtml);
        // find header bar in current HTML
        // Home · Profile · Find Friends · Messages · Notifications · Chat(8) · Menu
        Element headerDivTag = doc.getElementById("header");
        if (headerDivTag != null) {
            Elements aTags = headerDivTag.getElementsByTag("a");
            for (Element aTag : aTags) {
                boolean isOK = false;
                // JSOUP map &nbsp; to U+00A0
                String text = StringUtils.replace(aTag.text(), "\u00a0", "").trim();
                String href = aTag.attr("href");
                Elements children = aTag.children();
                if (children.isEmpty()) {
                    if (!StringUtils.isEmpty(text)) {
                        isOK = true;
                    }
                } else {
                    Element imgChild = children.get(0);
                    String tagName = imgChild.tagName();
                    if (StringUtils.equalsIgnoreCase(tagName, "img")) {
                        text = "Facebook Logo";
                        isOK = true;
                    }
                }
                if (isOK) {
                    // process this tag
                    URI hrefUri = uri.resolve(href);
                    mapUris.put(text, hrefUri);
                }
            }
            fbHearder.mapUris = mapUris;

            // find search form in header bar
            Elements formTags = headerDivTag.getElementsByTag("form");
            for (Element formTag : formTags) {
                String action = formTag.attr("action");
                if (StringUtils.contains(action, "/search/")) {
                    fbHearder.searchForm = formTag;
                }
            }
        }
        return fbHearder;
    }

    public static FacebookMyStatusForm getStatusForm(URI uri, String rawHtml) {
        FacebookMyStatusForm myStatusForm = new FacebookMyStatusForm();
        Document doc = Jsoup.parse(rawHtml);
        Element objectsContainer = doc.getElementById("objects_container");
        if (objectsContainer != null) {
            Elements formTags = objectsContainer.getElementsByTag("form");
            for (Element formTag : formTags) {
                String action = formTag.attr("action");
                String method = formTag.attr("method");
                if (StringUtils.contains(action, "/composer/mbasic/")) {
                    myStatusForm.sourceUri = uri;
                    myStatusForm.statusForm = formTag;
                    myStatusForm.postStatusUri = uri.resolve(action);
                    myStatusForm.method = method;
                    break;
                }
            }
        }
        return myStatusForm;
    }

    public static String buildParamsFromStatusForm(Element statusForm, String statusContent) {
        List<String> paramList = new ArrayList<>();

        Elements inputElements = statusForm.getElementsByTag("input");
        for (Element inputElement : inputElements) {
            String type = inputElement.attr("type");
            String name = inputElement.attr("name");
            String value = inputElement.attr("value");
            if (StringUtils.equalsIgnoreCase(type, "hidden") || StringUtils.equalsIgnoreCase(name, "view_post")) {
                try {
                    paramList.add(URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    LOG.error("Error encoding: " + ex.getMessage(), ex);
                }
            }
        }

        Elements textareaElements = statusForm.getElementsByTag("textarea");
        for (Element textareaElement : textareaElements) {
            String name = textareaElement.attr("name");
            String text = StringUtils.replace(textareaElement.text(), "\u00a0", "").trim();
            if (StringUtils.isNotEmpty(name) && StringUtils.equalsIgnoreCase(name, "xc_message")) {
                try {
                    if (StringUtils.isEmpty(text)) {
                        paramList.add(URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(statusContent, "UTF-8"));
                    } else {
                        paramList.add(URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(text, "UTF-8"));
                    }

                } catch (UnsupportedEncodingException ex) {
                    LOG.error("Error encoding: " + ex.getMessage(), ex);
                }
                break;
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
        String statusParams = result.toString();
        LOG.info("Form params {}", statusParams);
        return statusParams;
    }

    public static FacebookCommentForm getCommentForm(URI uri, String rawHtml) {
        FacebookCommentForm commentForm = new FacebookCommentForm();
        Document doc = Jsoup.parse(rawHtml);
        Element objectsContainer = doc.getElementById("objects_container");
        if (objectsContainer != null) {
            Elements formTags = objectsContainer.getElementsByTag("form");
            for (Element formTag : formTags) {
                String action = formTag.attr("action");
                String method = formTag.attr("method");
                if (StringUtils.contains(action, "/a/comment.php")) {
                    commentForm.sourceUri = uri;
                    commentForm.commentForm = formTag;
                    commentForm.commentUri = uri.resolve(action);
                    commentForm.method = method;
                    break;
                }
            }
        }
        return commentForm;
    }

    public static String buildParamsFromCommentForm(Element commentForm, String commentContent) {

        List<String> paramList = new ArrayList<>();

        Elements inputElements = commentForm.getElementsByTag("input");
        for (Element inputElement : inputElements) {
            String type = inputElement.attr("type");
            String name = inputElement.attr("name");
            String value = inputElement.attr("value");
            if (StringUtils.equalsIgnoreCase(type, "hidden")) {
                try {
                    paramList.add(URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    LOG.error("Error encoding: " + ex.getMessage(), ex);
                }
            }
            if (StringUtils.equalsIgnoreCase(name, "comment_text")) {
                try {
                    paramList.add(URLEncoder.encode(name, "UTF-8") + "=" + URLEncoder.encode(commentContent, "UTF-8"));
                } catch (UnsupportedEncodingException ex) {
                    LOG.error("Error encoding: " + ex.getMessage(), ex);
                }
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
        String statusParams = result.toString();
        LOG.info("Form params {}", statusParams);
        return statusParams;

    }

    /**
     * Get people who comment or like this post
     *
     * @param uri current URI
     * @param rawHtml current HTML: comment HTML or likes HTML
     * @return set of URI
     */
    public static Set<URI> getPeopleCommentOrLike(URI uri, String rawHtml) {
        Set<URI> commentProfiles = new HashSet<>();
        Document doc = Jsoup.parse(rawHtml);
        Elements profileATags = doc.select("div > h3 > a");
        for (Element profileATag : profileATags) {
            String href = profileATag.attr("href");
            URI hrefUri = uri.resolve(href);
            commentProfiles.add(hrefUri);
        }
        return commentProfiles;
    }

    public static FacebookMyBookmarkMenu getBookmarkMenuInHome(URI uri, String rawHtml) {
        FacebookMyBookmarkMenu fbMyBookmarkMenu = new FacebookMyBookmarkMenu();
        fbMyBookmarkMenu.sourceUri = uri;
        Map<String, URI> mapUris = new HashMap<>();
        Document doc = Jsoup.parse(rawHtml);
        // find bookmark menu at the bottom of Home Page
        Element bookmarkMenuDivTag = doc.getElementById("bookmarkmenu");
        if (bookmarkMenuDivTag != null) {
            Elements aTags = bookmarkMenuDivTag.getElementsByTag("a");
            for (Element aTag : aTags) {
                boolean isOK = false;
                // JSOUP map &nbsp; to U+00A0
                String text = StringUtils.replace(aTag.text(), "\u00a0", "").trim();
                String href = aTag.attr("href");
                Elements children = aTag.children();
                if (children.isEmpty()) {
                    if (!StringUtils.isEmpty(text)) {
                        isOK = true;
                    }
                }
                if (isOK) {
                    // process this tag
                    URI hrefUri = uri.resolve(href);
                    mapUris.put(text, hrefUri);
                }
            }
            fbMyBookmarkMenu.mapUris = mapUris;
        }
        return fbMyBookmarkMenu;
    }

    public static FacebookMyProfile parseMyProfile(URI uri, String rawHtml) {
        FacebookMyProfile fbMyProfile = new FacebookMyProfile();
        fbMyProfile.sourceUri = uri;
        Map<String, URI> mapUris = new HashMap<>();
        Document doc = Jsoup.parse(rawHtml);
        // find all links in timeline cover section
        // About · Friends · Photos · Likes · Following · Activity Log
        Element timelineCoverSectionDivTag = doc.getElementById("m-timeline-cover-section");
        if (timelineCoverSectionDivTag != null) {
            Elements aTags = timelineCoverSectionDivTag.getElementsByTag("a");
            for (Element aTag : aTags) {
                String text = StringUtils.replace(aTag.text(), "\u00a0", "").trim();
                String href = aTag.attr("href");
                if (!StringUtils.isEmpty(text)) {
                    URI hrefUri = uri.resolve(href);
                    mapUris.put(text, hrefUri);
                }
            }
            fbMyProfile.mapUris = mapUris;
        }
        // find status form in timeline body
        Element timelineBodyDivTag = doc.getElementById("timelineBody");
        if (timelineBodyDivTag != null) {
            Elements statusTags = timelineBodyDivTag.getElementsByTag("form");
            for (Element statusTag : statusTags) {
                String action = statusTag.attr("action");
                URI actionUri = uri.resolve(action);
                String actionPath = actionUri.getPath();
                if (StringUtils.contains(actionPath, "/composer/mbasic/")) {
                    fbMyProfile.statusForm = statusTag;
                }
            }
        }
        return fbMyProfile;
    }

    public static Map<String, URI> parseMyLikes(URI uri, String rawHtml) {
        Map<String, URI> mapLikedPageUris = new HashMap<>();
        Document doc = Jsoup.parse(rawHtml);
        // get all h4 tag is directly child of div tag
        Elements h4DivTags = doc.select("div > h4");
        if (!h4DivTags.isEmpty()) {
            List<Element> h4ParentDivTags = new ArrayList<>();
            // get parent of h4, of course it is div tag
            for (Element h4DivTag : h4DivTags) {
                String text = h4DivTag.text();
                if (StringUtils.equalsIgnoreCase(text, "Suggestions")) {
                    // ignore this
                } else {
                    h4ParentDivTags.add(h4DivTag.parent());
                }
            }
            // find all pages i liked
            for (Element h4ParentDivTag : h4ParentDivTags) {
                Elements aTags = h4ParentDivTag.getElementsByTag("a");
                for (Element aTag : aTags) {
                    String text = StringUtils.replace(aTag.text(), "\u00a0", "").trim();
                    String href = aTag.attr("href");
                    if (StringUtils.isEmpty(text)) {
                        URI hrefUri = uri.resolve(href);
                        String hrefPath = hrefUri.getPath();
                        if (hrefPath.contains("/pages/")
                                || StringUtils.contains(text, "Find Pages You May Like")
                                || StringUtils.contains(text, "See More")) {
                            // ignore this
                        } else {
                            mapLikedPageUris.put(text, hrefUri);
                        }
                    }
                }
            }
        }
        return mapLikedPageUris;
    }

    /**
     * Get Facebook Login Form
     *
     * @param html Raw HTML
     * @param username Facebook account username
     * @param password Facebook account password
     * @return String parameters
     */
    public static Pair<String, String> getFacebookFormParams(String html, String username, String password) {

        LOG.debug("Extracting form's data ...");
        Document doc = Jsoup.parse(html);
        // Facebook form id
        Element loginform = doc.getElementById("login_form");
        if (loginform == null) {
            return null;
        }
        String loginUrl = loginform.attr("action");
        LOG.info("Form post url {}", loginUrl);

        Elements inputElements = loginform.getElementsByTag("input");
        List<String> paramList = new ArrayList<>();
        for (Element inputElement : inputElements) {
            String key = inputElement.attr("name");
            String value = inputElement.attr("value");
            switch (key) {
                case "email":
                    value = username;
                    break;
                case "pass":
                    value = password;
                    break;
            }
            try {
                paramList.add(key + "=" + URLEncoder.encode(value, "UTF-8"));
            } catch (UnsupportedEncodingException ex) {
                LOG.error("Error encoding: " + ex.getMessage(), ex);
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
        String retParams = result.toString();
        LOG.info("Form params {}", retParams);

        Pair<String, String> ret = new Pair<>(loginUrl, retParams);

        return ret;
    }

    public static Map<String, String> findProfileUrls(String rawUrl, String rawHtml) {
        Map<String, String> profileUrls = new HashMap<>(); // key norm, value org
        Document doc = Jsoup.parse(rawHtml);
        Elements objectContainers = doc.select("div#objects_container");
        if (!objectContainers.isEmpty()) {
            Elements aTags = objectContainers.get(0).select("a[href]");
            for (Element aTag : aTags) {
                String text = aTag.text();
                String href = aTag.attr("href");
                if (StringUtils.isNotEmpty(text)) {
                    try {
                        if (StringUtils.isNotEmpty(href)) {
                            URI profileUri = new URI(rawUrl).resolve(href);
                            String result = normalizeProfileUrl(profileUri);
                            if (StringUtils.isNotEmpty(result)) {
                                String[] parts = StringUtils.split(result, "|");
                                profileUrls.put(parts[1], parts[0]);
                            }
                        }
                    } catch (URISyntaxException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
            }
        }
        return profileUrls;
    }

    public static Set<String> getUrls(String rawUrl, String rawHtml, String cssQuery, String searchText, int searchMode) {
        Set<String> stories = new HashSet<>();
        Document doc = Jsoup.parse(rawHtml);
        Elements elements;
        if (StringUtils.isEmpty(cssQuery)) {
            elements = doc.select("div#objects_container");
        } else {
            elements = doc.select(cssQuery);
        }
        if (!elements.isEmpty()) {
            Elements aTags = elements.get(0).select("a[href]");
            if (!aTags.isEmpty()) {
                for (Element aTag : aTags) {
                    String text = StringUtils.trim(aTag.text());
                    String href = aTag.attr("href");
                    if (StringUtils.isNotEmpty(text)) {
                        try {
                            URI resolved = new URI(rawUrl).resolve(href);
                            if (searchMode == 0) {
                                // find exactly
                                if (StringUtils.equalsIgnoreCase(text, searchText)) {
                                    stories.add(resolved.toString());
                                }
                            } else if (searchMode == 1) {
                                // find contains
                                if (StringUtils.contains(text, searchText)) {
                                    stories.add(resolved.toString());
                                }
                            }
                        } catch (URISyntaxException ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    }
                }
            }
        }
        return stories;
    }

    public static String normalizeProfileUrl(URI uri) {
        String result = "";
        String format = "%s|%s"; // originalUrl|normalizeUrl

        String host = uri.getHost();
        if (StringUtils.equals(host, "m.facebook.com")) {
            String path = uri.getPath();
            Map<String, List<String>> params = splitQuery(uri);
            if (path.matches("^/[0-9a-zA-Z\\.]+$")) {
                if (StringUtils.equals(path, "/story.php") || StringUtils.equals(path, "/stories.php")
                        || StringUtils.equals(path, "/photo.php") || StringUtils.equals(path, "/l.php")
                        || StringUtils.equals(path, "/home.php") || StringUtils.equals(path, "/notifications.php")
                        || StringUtils.equals(path, "/buddylist.php") || StringUtils.equals(path, "/logout.php")
                        || StringUtils.equals(path, "/findfriends.php") || StringUtils.equals(path, "/download.php")) {
                    return null;
                }
                if (StringUtils.equals(path, "/profile.php")) {
                    if (params.containsKey("id")) {
                        String orgUrl = uri.toString();
                        String normUrl = "https://m.facebook.com/profile.php?id=" + params.get("id").get(0);
                        result = String.format(format, orgUrl, normUrl);
                    }
                } else {
                    String orgUrl = uri.toString();
                    String normUrl = "https://m.facebook.com" + uri.getPath();
                    result = String.format(format, orgUrl, normUrl);
                }
            }
            if (path.matches("^/groups/[0-9a-zA-Z\\.]+$")) {
                String orgUrl = uri.toString();
                String normUrl = "https://m.facebook.com" + uri.getPath();
                result = String.format(format, orgUrl, normUrl);
            }
        }
        return result;
    }

    public static Map<String, List<String>> splitQuery(URI uri) {
        final Map<String, List<String>> query_pairs = new LinkedHashMap<>();
        String query = uri.getQuery();
        if (!StringUtils.isEmpty(query)) {
            final String[] pairs = uri.getQuery().split("&");
            for (String pair : pairs) {
                try {
                    final int idx = pair.indexOf("=");
                    final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
                    if (!query_pairs.containsKey(key)) {
                        query_pairs.put(key, new LinkedList<String>());
                    }
                    final String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8") : null;
                    query_pairs.get(key).add(value);
                } catch (UnsupportedEncodingException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
        return query_pairs;
    }

    public static AccountStatus verifyResponseHtml(String requestedUrl, String responseHtml, boolean checkDocTitle) {

        if (StringUtils.isEmpty(responseHtml)) {
            return AccountStatus.KICKOUT_UNKNOWN;
        }

        Document doc = Jsoup.parse(responseHtml);
        String docTitle = doc.title();
        // case check redirect to home
        if (checkDocTitle) {
            if (StringUtils.equalsIgnoreCase(docTitle, "Facebook")) {
                // redirect to home, cannot see this links
                return AccountStatus.KICKOUT_LEVEL_1;
            }
        }

        // case check Access Restrict BAD ID
        if (StringUtils.contains(docTitle, "Access Restricted")
                || StringUtils.contains(docTitle, "Truy cập bị hạn chế")) {
            return AccountStatus.KICKOUT_LEVEL_2;
        }

        // case Facebook kill this account session, force this account logout
        boolean foundButtonLogin = false;
        Elements divObjContainers = doc.select("div#objects_container");
        AccountStatus ret = AccountStatus.ACTIVE;
        for (Element divObjContainer : divObjContainers) {
            // find button Login with a tag
            Elements aTags = divObjContainer.getElementsByTag("a");
            for (Element aTag : aTags) {
                try {
                    String text = aTag.text();
                    String href = aTag.attr("href");
                    URI hrefUri = new URI(requestedUrl).resolve(href);
                    String path = hrefUri.getPath();
                    if (StringUtils.equalsIgnoreCase(text.toLowerCase(), "Log in")
                            || StringUtils.equalsIgnoreCase(path, "/login.php")) {
                        foundButtonLogin = true;
                        ret = AccountStatus.KICKOUT_LEVEL_2;
                        break;
                    }
                } catch (URISyntaxException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
            if (!foundButtonLogin) {
                // find button Login with input tag
                Elements inputTags = divObjContainer.getElementsByTag("input");
                for (Element inputTag : inputTags) {
                    String name = inputTag.attr("name");
                    String value = inputTag.attr("value");
                    if (StringUtils.equalsIgnoreCase(name, "login")
                            || StringUtils.equalsIgnoreCase(value, "Log in")) {
                        foundButtonLogin = true;
                        ret = AccountStatus.KICKOUT_LEVEL_2;
                        break;
                    }
                }
            }
        }
        return ret;
    }

    public static Set<URI> findAllHrefsInMainContent(String rawUrl, String rawHtml) {
        Set<URI> rawUrls = new HashSet<>();
        Elements mainElements = Jsoup.parse(rawHtml).select("div#objects_container");
        if (!mainElements.isEmpty()) {
            Elements aTags = mainElements.get(0).select("a[href]");
            for (Element aTag : aTags) {
                try {
                    String href = aTag.attr("href");
                    URI uri = new URI(rawUrl);
                    if (StringUtils.isNotEmpty(href)) {
                        URI resolved = uri.resolve(href);
                        rawUrls.add(resolved);
                    }
                } catch (URISyntaxException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
        return rawUrls;
    }

    /**
     * Extract username or user id from url. There is 3 type of profile url
     *
     * Type 1: https://m.facebook.com/nguyen.t.xinh
     *
     * Type 2: https://m.facebook.com/profile.php?id=95121327711
     *
     * Type 3: https://m.facebook.com/groups/304309249727211
     *
     * @param profileUrl
     * @return
     */
    public static Map<String, String> extractUsernameOrIdFromUrl(String profileUrl) {
        Map<String, String> map;
        map = new HashMap<>();
        String rewriteUrl = rewriteProfileURL(profileUrl);
        if (rewriteUrl != null) {
            try {
                URL url = new URL(rewriteUrl);
                if (url.getQuery() == null) {
                    if (url.getPath().startsWith("/groups/")) {
                        String groupId = StringUtils.remove(url.getPath().substring(9), "/");
                        map.put("groupId", groupId);
                    } else if (url.getPath().startsWith("/pages/")) {
                        int index = StringUtils.lastIndexOf(url.getPath(), "/");
                        String pageId = StringUtils.remove(StringUtils.substring(url.getPath(), index), "/");
                        map.put("pageId", pageId);
                    } else {
                        String username = url.getPath().substring(1);
                        map.put("username", username);
                    }
                } else {
                    Map<String, List<String>> query_pairs;
                    query_pairs = splitQuery(url);
                    if (query_pairs != null) {
                        String id = query_pairs.get("id").get(0);
                        map.put("id", id);
                    }
                }
            } catch (MalformedURLException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
        return map;
    }

    /**
     * Check URL is match with USER or PAGE or GROUP type.
     *
     * Example:
     *
     * Type 1: https://m.facebook.com/nguyen.t.xinh
     *
     * Type 2: https://m.facebook.com/profile.php?id=95121327711
     *
     * Type 3: https://m.facebook.com/groups/304309249727211
     *
     * Type 4: https://m.facebook.com/groups/buonbanmevabeonline/
     *
     * @param strUrl
     * @return rewrite profile URL
     */
    public static String rewriteProfileURL(String strUrl) {
        String rewriteUrl = "";
        if (strUrl.startsWith(FacebookURL.BASE_URL) || strUrl.startsWith(FacebookURL.WEB_BASE_URL)) {
            try {
                URL url = new URL(strUrl);
                Map<String, List<String>> query_pairs;
                query_pairs = splitQuery(url);

                if (MapUtils.isEmpty(query_pairs)) {
                    // this URL does not contain any query pairs
                    LOG.debug("rewriteProfileURL: " + strUrl);
                    rewriteUrl = strUrl;
                } else {
                    // get path of this URL
                    String path = url.getPath();

                    // pattern for check USER and PAGE url
                    String patternUserPageStr = "^(/)([0-9a-zA-Z\\.]+)$";
                    Pattern patternUserPage = Pattern.compile(patternUserPageStr);
                    Matcher matcherUserPage = patternUserPage.matcher(path);
                    boolean matchesUserPage = matcherUserPage.matches();

                    if (matchesUserPage) {
                        if (path.matches("^(/)([0-9a-zA-Z\\.]+)(\\.php)$")) {
                            if (path.matches("/profile.php")) {
                                if (query_pairs.containsKey("id")) {
                                    UrlBuilder finalURL = UrlBuilder.empty().withScheme(url.getProtocol()).withHost(url.getHost())
                                            .withPath(url.getPath()).addParameter("id", query_pairs.get("id").get(0));
                                    LOG.debug("rewriteProfileURL: " + strUrl + " --> " + finalURL.toString());
                                    rewriteUrl = finalURL.toString();
                                }
                            }
                        } else {
                            if (query_pairs.containsKey("v") || query_pairs.containsKey("id") || query_pairs.containsKey("fbid")) {
                                // do nothing
                            } else {
                                UrlBuilder finalURL = UrlBuilder.empty().withScheme(url.getProtocol()).withHost(url.getHost())
                                        .withPath(url.getPath());
                                LOG.debug("rewriteProfileURL: " + strUrl + " --> " + finalURL.toString());
                                rewriteUrl = finalURL.toString();
                            }
                        }
                    }

                    // pattern for check GROUP url
                    String patternGroupStr = "^(/)(groups)(/)([0-9a-zA-Z\\.]+)([/]?)$";
                    Pattern patternGroup = Pattern.compile(patternGroupStr);
                    Matcher matcherGroup = patternGroup.matcher(path);
                    boolean matchesGroup = matcherGroup.matches();

                    if (matchesGroup) {
                        if (query_pairs.containsKey("view") || query_pairs.containsKey("id")) {
                            // do nothing
                        } else {
                            UrlBuilder finalURL = UrlBuilder.empty().withScheme(url.getProtocol()).withHost(url.getHost())
                                    .withPath(url.getPath());
                            LOG.debug("rewriteProfileURL: " + strUrl + " --> " + finalURL.toString());
                            rewriteUrl = finalURL.toString();
                        }
                    }
                }
            } catch (MalformedURLException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
        return rewriteUrl;
    }

    /**
     * Split all query parameters in a URL
     *
     * @param url input URL
     * @return map of key-value parameters
     */
    public static Map<String, List<String>> splitQuery(URL url) {
        final Map<String, List<String>> query_pairs = new LinkedHashMap<>();
        if (!StringUtils.isEmpty(url.getQuery())) {
            final String[] pairs = url.getQuery().split("&");
            for (String pair : pairs) {
                try {
                    final int idx = pair.indexOf("=");
                    final String key = idx > 0 ? URLDecoder.decode(pair.substring(0, idx), "UTF-8") : pair;
                    if (!query_pairs.containsKey(key)) {
                        query_pairs.put(key, new LinkedList<String>());
                    }
                    final String value = idx > 0 && pair.length() > idx + 1 ? URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                            : null;
                    query_pairs.get(key).add(value);
                } catch (UnsupportedEncodingException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
        return query_pairs;
    }

}
