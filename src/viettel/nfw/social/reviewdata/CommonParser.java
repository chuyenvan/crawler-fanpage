package viettel.nfw.social.reviewdata;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.urlbuilder.UrlBuilder;

/**
 * Support parser functions.
 *
 * @author duongth5
 * @version 1.0
 * @since 1.0
 */
public class CommonParser {

    /**
     * Logger for CommonParser Class
     */
    private static final Logger LOG = LoggerFactory.getLogger(CommonParser.class);

    /**
     * Identify Profile Type by buttons or links or profileURL.
     *
     * Button Add Friend: /a/mobile/friends/profile_add_friend.php?subjectid=1152666961
     *
     * Button Follow: /a/subscribe.php?id=1152666961
     *
     * Button Block this person: /privacy/touch/block/confirm/?bid=1152666961
     *
     * Button Like: /a/profile.php?fan&id=314103605281408&origin=page_profile
     *
     * Button Unlike: /a/profile.php?unfan&id=107186012708107
     *
     * Button Join Group: /a/group/join/?group_id=290095451195693&gfid=AQA9yT8BgoDXCt-I&refid=18 actually, this is a
     * form with method post and action to the link above
     *
     * @param profileUrl
     * @param profileHTMLDoc
     * @return
     * @throws MalformedURLException
     * @throws UnsupportedEncodingException
     */
    public static Map<String, String> identifyProfileType(String profileUrl, Document profileHTMLDoc) throws MalformedURLException,
            UnsupportedEncodingException {

        Map<String, String> retInfo = new HashMap<>();

        URL gUrl = new URL(profileUrl);
        // check if URL is groups
        if (gUrl.getPath().startsWith("/groups/")) { // URL is group
            String groupId = "";
            String groupUsername = "";
            String path = gUrl.getPath();
            String regexGroupStr = "^(/)(groups)(/)([0-9a-zA-Z\\.]+)([/]?)$";
            Pattern patternGroup = Pattern.compile(regexGroupStr);
            Matcher matcherGroup = patternGroup.matcher(path);
            boolean matchesGroup = matcherGroup.matches();

            if (matchesGroup) {
                String idOrUsername = matcherGroup.group(4);
                String regexId = "^[0-9]+$";
                Pattern patternId = Pattern.compile(regexId);
                Matcher matcherId = patternId.matcher(idOrUsername);
                boolean matchesId = matcherId.matches();
                if (matchesId) {
                    groupId = idOrUsername;
                } else {
                    groupUsername = idOrUsername;
                    // must find group id via find button Join Group in Document
                    Elements forms = profileHTMLDoc.select("div#objects_container").select("form[method=post]");
                    if (!forms.isEmpty()) {
                        for (Element form : forms) {
                            String relativeActionUrl = form.attr("action");
                            if (StringUtils.isNotEmpty(relativeActionUrl) && relativeActionUrl.startsWith("/a/group/join/")) {
                                URL absoluteActionUrl = new URL(FacebookURL.BASE_URL + relativeActionUrl);
                                Map<String, List<String>> query_pairs = Parser.splitQuery(absoluteActionUrl);
                                groupId = query_pairs.get("group_id").get(0);
                            }
                        }
                    }
                }
                if (StringUtils.isNotEmpty(groupId)) {
                    retInfo.put(Constant.KEY_TYPE, Constant.TYPE_GROUP);
                    retInfo.put(Constant.KEY_ID, groupId);
                    if (StringUtils.isNotEmpty(groupUsername)) {
                        retInfo.put(Constant.KEY_USERNAME, groupUsername);
                    }
                    return retInfo;
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } else { // URL is User or Page
            String retProfileURL = rewriteProfileURL(profileUrl);
            if (!StringUtils.isEmpty(retProfileURL)) {
                URL url = new URL(retProfileURL);
                if (StringUtils.isEmpty(url.getQuery())) {
                    String username = url.getPath().substring(1);
                    retInfo.put(Constant.KEY_USERNAME, username);
                }
            }

            Elements links = profileHTMLDoc.select("div#objects_container").select("a[href]");

            // map with key is relative url, value is name of url
            Map<String, String> urlMap = new HashMap<>();
            for (Element link : links) {
                if (link.attr(Html.Attribute.HREF).toLowerCase().startsWith("/")) {
                    String text = link.text();
                    String relUrl = link.attr(Html.Attribute.HREF);
                    urlMap.put(relUrl, text);
                }
            }

            for (Map.Entry<String, String> entry : urlMap.entrySet()) {
                // relative url
                String key = entry.getKey();
                // text
                String value = entry.getValue();

                URL absUrl = new URL(FacebookURL.BASE_URL + key);
                Map<String, List<String>> query_pairs = Parser.splitQuery(absUrl);

                if (value.compareToIgnoreCase(Constant.BTN_ADD_FRIEND) == 0
                        && key.toLowerCase().startsWith("/a/mobile/friends/profile_add_friend.php")) {
                    // button Add Friend
                    String userId = query_pairs.get("subjectid").get(0);
                    retInfo.put(Constant.KEY_TYPE, Constant.TYPE_USER);
                    retInfo.put(Constant.KEY_ID, userId);
                    return retInfo;
                } else if (value.compareToIgnoreCase(Constant.BTN_FOLLOW) == 0 && key.toLowerCase().startsWith("/a/subscribe.php")) {
                    // button follow
                    String userId = query_pairs.get("id").get(0);
                    retInfo.put(Constant.KEY_TYPE, Constant.TYPE_USER);
                    retInfo.put(Constant.KEY_ID, userId);
                    return retInfo;
                } else if (value.compareToIgnoreCase(Constant.BTN_BLOCK_THIS_PERSON) == 0
                        && key.toLowerCase().startsWith("/privacy/touch/block/confirm/")) {
                    // button Block this person
                    String userId = query_pairs.get("bid").get(0);
                    retInfo.put(Constant.KEY_TYPE, Constant.TYPE_USER);
                    retInfo.put(Constant.KEY_ID, userId);
                    return retInfo;
                } else if (value.compareToIgnoreCase(Constant.BTN_LIKE) == 0 && key.toLowerCase().startsWith("/a/profile.php")) {
                    // button Like
                    if (query_pairs.containsKey("fan")) {
                        String pageId = query_pairs.get("id").get(0);
                        retInfo.put(Constant.KEY_TYPE, Constant.TYPE_PAGE);
                        retInfo.put(Constant.KEY_ID, pageId);
                        return retInfo;
                    }
                } else if (value.compareToIgnoreCase(Constant.BTN_UNLIKE) == 0 && key.toLowerCase().startsWith("/a/profile.php")) {
                    // button Unlike
                    if (query_pairs.containsKey("unfan")) {
                        String pageId = query_pairs.get("id").get(0);
                        retInfo.put(Constant.KEY_TYPE, Constant.TYPE_PAGE);
                        retInfo.put(Constant.KEY_ID, pageId);
                        return retInfo;
                    }
                }
            }
            return null;
        }
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
                query_pairs = Parser.splitQuery(url);

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
     *
     * @param inputUrl
     * @return
     */
    public static Pair<Boolean, String> normalizeFbProfileUrl(String inputUrl) {
        Pair<Boolean, String> result = new Pair<>(false, "");
        String regex = "^(https?)://(www|m|vi-vn)?(\\.)?facebook\\.com/.*$";
        String regexUserPage = "^(/)([0-9a-zA-Z\\.]+)$";
        String regexGroup = "^(/)(groups)(/)([0-9a-zA-Z\\.]+)([/]?)$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(inputUrl);
        boolean matches = matcher.matches();
        if (matches) {
            boolean isProfile = false;
            String rewriteUrl = "";

            try {
                URL url = new URL(inputUrl);
                Map<String, List<String>> query_pairs;
                query_pairs = Parser.splitQuery(url);

                if (MapUtils.isEmpty(query_pairs)) {
                    Pattern patternUserPage = Pattern.compile(regexUserPage);
                    Matcher matcherUserPage = patternUserPage.matcher(url.getPath());
                    boolean matchesUserPage = matcherUserPage.matches();

                    if (matchesUserPage) {
                        UrlBuilder finalURL = UrlBuilder.empty().withScheme("https").withHost("m.facebook.com").withPath(url.getPath());
                        rewriteUrl = finalURL.toString();
                        isProfile = true;
                    }
                } else {
                    // get path of this URL
                    String path = url.getPath();

                    // pattern for check USER and PAGE url
                    Pattern patternUserPage = Pattern.compile(regexUserPage);
                    Matcher matcherUserPage = patternUserPage.matcher(path);
                    boolean matchesUserPage = matcherUserPage.matches();

                    if (matchesUserPage) {
                        if (path.matches("^(/)([0-9a-zA-Z\\.]+)(\\.php)$")) {
                            if (path.matches("/profile.php")) {
                                if (query_pairs.containsKey("id")) {
                                    UrlBuilder finalURL = UrlBuilder.empty().withScheme("https").withHost("m.facebook.com").withPath(url.getPath()).addParameter("id", query_pairs.get("id").get(0));
                                    rewriteUrl = finalURL.toString();
                                    isProfile = true;
                                }
                            }
                        } else {
                            if (query_pairs.containsKey("v") || query_pairs.containsKey("id") || query_pairs.containsKey("fbid")) {
                                // do nothing
                            } else {
                                UrlBuilder finalURL = UrlBuilder.empty().withScheme("https").withHost("m.facebook.com").withPath(url.getPath());
                                rewriteUrl = finalURL.toString();
                                isProfile = true;
                            }
                        }
                    }

                    // pattern for check GROUP url
                    Pattern patternGroup = Pattern.compile(regexGroup);
                    Matcher matcherGroup = patternGroup.matcher(path);
                    boolean matchesGroup = matcherGroup.matches();

                    if (matchesGroup) {
                        if (query_pairs.containsKey("view") || query_pairs.containsKey("id")) {
                            // do nothing
                        } else {
                            UrlBuilder finalURL = UrlBuilder.empty().withScheme("https").withHost("m.facebook.com").withPath(url.getPath());
                            rewriteUrl = finalURL.toString();
                            isProfile = true;
                        }
                    }
                }
            } catch (MalformedURLException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            result = new Pair<>(isProfile, rewriteUrl);
        }
        return result;
    }

    /**
     *
     * @param args
     */
    public static void main(String[] args) {
        List<String> urls = new ArrayList<>();
        urls.add("https://www.facebook.com/chuyennd2");
        urls.add("http://vi-vn.facebook.com/duongth5");
        urls.add("https://facebook.com/thiendn2");
        urls.add("https://facebook.com/#");
        urls.add("https://facebook.com/profile.php?id=123445678");

        for (String url : urls) {
            Pair<Boolean, String> result = normalizeFbProfileUrl(url);
            LOG.info("{} - {}", result.first, result.second);
        }
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
                        String groupId = url.getPath().substring(9);
                        map.put("groupId", groupId);
                    } else {
                        String username = url.getPath().substring(1);
                        map.put("username", username);
                    }
                } else {
                    Map<String, List<String>> query_pairs;
                    query_pairs = Parser.splitQuery(url);
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
     * Classify all kinds of post URL. There are type of story URL, photo URL ...
     *
     * @param postUrl input post URL
     * @return Map contains information of post URL: post URL type, post ID ...
     */
    public static Map<String, String> classifyPostURL(URL postUrl) {

        Map<String, String> mapURLInfo = new HashMap<>();

        Map<String, List<String>> query_pairs;
        query_pairs = Parser.splitQuery(postUrl);
        String postId, postOwnerId;

        String path = postUrl.getPath();
        if (path.matches("/story.php")) {
            if (!query_pairs.isEmpty()) {
                if (query_pairs.containsKey("story_fbid") && query_pairs.containsKey("id")) {
                    postId = query_pairs.get("story_fbid").get(0);
                    postOwnerId = query_pairs.get("id").get(0);
                    UrlBuilder finalURL = UrlBuilder.empty().withScheme(postUrl.getProtocol()).withHost(postUrl.getHost())
                            .withPath(postUrl.getPath()).addParameter("story_fbid", postId).addParameter("id", postOwnerId);
                    LOG.debug("Story - final   : " + finalURL.toString());
                    mapURLInfo.put(Constant.KEY_POST_URL, finalURL.toString());
                    mapURLInfo.put(Constant.KEY_POST_ID, postId);
                    mapURLInfo.put(Constant.KEY_POST_OWNER, postOwnerId);
                    mapURLInfo.put(Constant.KEY_POST_URL_TYPE, Constant.URL_TYPE_STORY);
                }
            }
        } else if (path.matches("/photo.php")) {
            if (!query_pairs.isEmpty()) {
                if (query_pairs.containsKey("fbid") && query_pairs.containsKey("id") && query_pairs.containsKey("set")) {
                    postId = query_pairs.get("fbid").get(0);
                    postOwnerId = query_pairs.get("id").get(0);
                    String albumSet = query_pairs.get("set").get(0);
                    UrlBuilder finalURL = UrlBuilder.empty().withScheme(postUrl.getProtocol()).withHost(postUrl.getHost())
                            .withPath(postUrl.getPath()).addParameter("fbid", postId).addParameter("id", postOwnerId)
                            .addParameter("set", albumSet);
                    LOG.debug("Photo - final   : " + finalURL.toString());
                    mapURLInfo.put(Constant.KEY_POST_URL, finalURL.toString());
                    mapURLInfo.put(Constant.KEY_POST_ID, postId);
                    mapURLInfo.put(Constant.KEY_POST_OWNER, postOwnerId);
                    mapURLInfo.put(Constant.KEY_POST_URL_TYPE, Constant.URL_TYPE_PHOTO);
                }
            }
        } else if (path.matches("^(/)(.*)(/)(photos)(/)(.*)(/)(\\d++)(/)$")) {
            // TODO unit test again
            // https://m.facebook.com/giaitrionline/photos/a.371876336239072.54878982.107186012708107/744760195617349/?type=1&source=46&refid=17
            String regex = "^(/)(.*)(/)(photos)(/)(.*)(/)(\\d++)(/)$";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(path);
            if (matcher.matches()) {
                postId = matcher.group(8);
                UrlBuilder finalURL = UrlBuilder.empty().withScheme(postUrl.getProtocol()).withHost(postUrl.getHost())
                        .withPath(postUrl.getPath());
                LOG.debug("Photo - final   : " + finalURL.toString());
                mapURLInfo.put(Constant.KEY_POST_URL, finalURL.toString());
                mapURLInfo.put(Constant.KEY_POST_ID, postId);
                mapURLInfo.put(Constant.KEY_POST_OWNER, "");
                mapURLInfo.put(Constant.KEY_POST_URL_TYPE, Constant.URL_TYPE_PHOTO);
            }
        } else if (path.matches("^(/)(\\d++)(/)(posts)(/)(\\d++)(/)$")) {
            String regex = "^(/)(\\d++)(/)(posts)(/)(\\d++)(/)$";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(path);
            if (matcher.matches()) {
                postOwnerId = matcher.group(2);
                postId = matcher.group(6);
                UrlBuilder finalURL = UrlBuilder.empty().withScheme(postUrl.getProtocol()).withHost(postUrl.getHost())
                        .withPath(postUrl.getPath());
                LOG.debug("Story - final   : " + finalURL.toString());
                mapURLInfo.put(Constant.KEY_POST_URL, finalURL.toString());
                mapURLInfo.put(Constant.KEY_POST_ID, postId);
                mapURLInfo.put(Constant.KEY_POST_OWNER, postOwnerId);
                mapURLInfo.put(Constant.KEY_POST_URL_TYPE, Constant.URL_TYPE_STORY);
            }
        } else if (path.startsWith("/notes/")) {
            String regex = "/([0-9]++)/$";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(path);
            if (matcher.find()) {
                postId = matcher.group(1);
                UrlBuilder finalURL = UrlBuilder.empty().withScheme(postUrl.getProtocol()).withHost(postUrl.getHost())
                        .withPath(postUrl.getPath());
                LOG.debug("Note - final   : " + finalURL.toString());
                mapURLInfo.put(Constant.KEY_POST_URL, finalURL.toString());
                mapURLInfo.put(Constant.KEY_POST_ID, postId);
                mapURLInfo.put(Constant.KEY_POST_OWNER, "");
                mapURLInfo.put(Constant.KEY_POST_URL_TYPE, Constant.URL_TYPE_NOTE);
            }
        }
        return mapURLInfo;
    }

    /**
     * Find all URLs in document
     *
     * @param doc input document
     * @return set of found URLs
     */
    public static Set<String> findAllLinksInDoc(Document doc) {
        Set<String> links;
        links = new HashSet<>();
        // TODO put div#objects_container as param
        Elements div_object_container = doc.select("div#objects_container");
        if (!div_object_container.isEmpty()) {
            Elements a_href = div_object_container.get(0).select("a[href]");
            for (Element link : a_href) {
                String strLink = link.attr(Html.Attribute.HREF);
                if (!StringUtils.startsWith(strLink, "#")) {
                    if (StringUtils.startsWith(strLink, "/")) {
                        String normalizedUrl = FacebookURL.BASE_URL + strLink;
                        links.add(normalizedUrl);
                    } else if (StringUtils.startsWithIgnoreCase(strLink, "http://")
                            || StringUtils.startsWithIgnoreCase(strLink, "https://")) {
                        links.add(strLink);
                    }
                }
            }
        }
        return links;
    }

    /**
     * Check if parameter is existed in URL
     *
     * @param strUrl input URL
     * @param paramKey key of parameter
     * @param paramValue value of parameter
     * @return true if existed, false otherwise
     */
    public static boolean hasParam(String strUrl, String paramKey, String paramValue) {
        try {
            URL url = new URL(strUrl);
            Map<String, List<String>> query_pairs;
            query_pairs = Parser.splitQuery(url);
            if (query_pairs.size() > 0) {
                if (query_pairs.containsKey(paramKey)) {
                    String value = query_pairs.get(paramKey).get(0);
                    if (value.compareToIgnoreCase(paramValue) == 0) {
                        return true;
                    }
                }
            }
        } catch (MalformedURLException e) {
            LOG.error(e.getMessage(), e);
        }
        return false;
    }
}
