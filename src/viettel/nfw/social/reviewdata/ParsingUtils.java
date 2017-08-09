package viettel.nfw.social.reviewdata;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.model.facebook.Comment;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.model.facebook.Post;
import viettel.nfw.social.model.facebook.Profile;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.engine.utils.TParser;
import vn.viettel.social.fb.test.FbUrlToHtml;

/**
 *
 * @author duongth5
 */
public class ParsingUtils {

    private static final Logger LOG = LoggerFactory.getLogger(ParsingUtils.class);

    private static enum ProfileType {

        PAGE, GROUP, USER
    }

    private static enum FacebookUrlType {

        PROFILE, TIMELINE, INFO, FRIENDS, LIKES, FOLLOWING, FOLLOWERS, PAGE_ABOUT, POST_PHOTO, POST_STORY, POST_NOTE, UNDEFINED,
    }

    private static FacebookUrlType getTypeFromUrl(String url) {
        if (url.contains("?v=timeline")) {
            return FacebookUrlType.TIMELINE;
        }
        if (url.contains("story.php?")) {
            return FacebookUrlType.POST_STORY;
        }
        if (url.contains("photo.php") || url.contains("/photos/")) {
            return FacebookUrlType.POST_PHOTO;
        }
        if (url.contains("/notes/")) {
            return FacebookUrlType.POST_NOTE;
        }
        if (url.contains("v=likes")) {
            return FacebookUrlType.LIKES;
        }
        if (url.contains("v=following")) {
            return FacebookUrlType.FOLLOWING;
        }
        if (url.contains("/about")) {
            return FacebookUrlType.PAGE_ABOUT;
        }
        if (url.contains("v=friends")) {
            return FacebookUrlType.FRIENDS;
        }
        if (url.contains("v=info")) {
            return FacebookUrlType.INFO;
        }
        if (url.contains("v=followers")) {
            return FacebookUrlType.FOLLOWERS;
        }
        return FacebookUrlType.PROFILE;
    }

    /**
     * for processing profile pages
     *
     * @param foundProfilePage
     * @param urlType
     * @param profileType
     * @param fb2Html
     * @param profile
     */
    private static void processProfilePage(FbUrlToHtml fb2Html, List<FbUrlToHtml> htmls, FacebookObject res) {
        ProfileType profileType = null;
        Document profileHTMLDoc = Jsoup.parse(fb2Html.getRawHtml());

        try {
            // detect profile type
            Map<String, String> profileTypeMap = CommonParser.identifyProfileType(fb2Html.getRawUrl(), profileHTMLDoc);
            if (MapUtils.isEmpty(profileTypeMap)) {

            } else {
                if (profileTypeMap.containsKey(Constant.KEY_TYPE)) {
                    String typeS = profileTypeMap.get(Constant.KEY_TYPE);
                    switch (typeS) {
                        case Constant.TYPE_GROUP:
                        case Constant.TYPE_PRIVACY_CLOSED:
                        case Constant.TYPE_PRIVACY_OPEN:
                            profileType = ProfileType.GROUP;
                            break;
                        case Constant.TYPE_PAGE:
                            profileType = ProfileType.PAGE;
                            break;
                        case Constant.TYPE_USER:
                            profileType = ProfileType.USER;
                            break;
                        default:
                            LOG.info("Unknown profile type...");
                            break;
                    }
                }
            }
        } catch (MalformedURLException | UnsupportedEncodingException ex) {
            LOG.warn("Error in detecting profileType");
        }
        if (profileType == null) {
            return;
        }
        switch (profileType) {
            case USER:
                // System.out.println("PAGE: " + fb2Html.getRawUrl());
                processTypeUser(fb2Html.getRawUrl(), htmls, res);
                break;
            case PAGE:
                // System.out.println("PAGE: " + fb2Html.getRawUrl());
                processTypePage(fb2Html.getRawUrl(), htmls, res);
                break;
            case GROUP:
                // System.out.println("GROUP: " + fb2Html.getRawUrl());
                processTypeGroup(fb2Html.getRawUrl(), htmls, res);
                break;
            default:
                break;
        }
    }

    private static void processTypeUser(String profileUrl, List<FbUrlToHtml> htmls, FacebookObject res) {
        LOG.debug("###############");
        LOG.debug(profileUrl);
        for (FbUrlToHtml html : htmls) {
            FacebookUrlType urlType = getTypeFromUrl(html.getRawUrl());
            LOG.debug(urlType + " - " + html.getRawHtml());
            switch (urlType) {
                case PROFILE: {
                    String profileId = TParser.getContent(html.getRawHtml(), "/profile/picture/view/\\?profile_id=", "['\"]");
                    if (profileId == null) {
                        profileId = TParser.getContent(html.getRawHtml(), "/messages/thread/", "/");
                    }
                    ///privacy/touch/block/confirm/?bid=100000956897310&ret_cancel&gfid=AQAIJ-E6kmmMtHKf
                    if (profileId == null) {
                        profileId = TParser.getContent(html.getRawHtml(), "privacy/touch/block/confirm/\\?bid=", "&");
                    }
                    if (profileId == null) {
                        LOG.debug("profileId is null at {}", html.getRawUrl());
                        break;
                    }
                    res.getInfo().setId(profileId);
                    res.getInfo().setUrl(html.getRawUrl());
                    String fullname = TParser.getContent(html.getRawHtml(), "<strong class=\"bp\">", "</strong>");
                    if (StringUtils.isEmpty(fullname)) {
                        Document doc = Jsoup.parse(html.getRawHtml());
                        fullname = doc.title();
                    }
                    res.getInfo().setFullname(fullname);
                    break;
                }
                case TIMELINE:
                    processTimelinePageUser(html, res);
                    break;
                case POST_STORY:
                    processPostStoryPageUser(html, res);
                    break;
                case POST_PHOTO:
                    processPostPhotoPageUser(html, res);
                    break;
            }
        }
    }

    /**
     * Process photo pages
     *
     * @param fb2Html
     * @param res
     */
    private static void processPostPhotoPageUser(FbUrlToHtml fb2Html, FacebookObject res) {
        URI baseURI = null;
        try {
            baseURI = new URI(fb2Html.getRawUrl());
        } catch (URISyntaxException ex) {
            // Skip this cause we will never met it			
            ex.printStackTrace();
            return;
        }
        Document doc = Jsoup.parse(fb2Html.getRawHtml());
        Elements els = doc.getElementsByTag("div");
        Post post = new Post();
        post.setId(getPostIdFromUrl(fb2Html.getRawUrl()));

        if (post.getId() == null) {
            LOG.warn("No post ID found in {}", fb2Html.getRawUrl());
            return;
        }
        post.setUrl(fb2Html.getRawUrl());
        for (Element e : els) {
            String id = e.attr("id");
            if (id == null || (!id.equals("root"))) {
                continue;
            }
            Elements aTags = e.getElementsByTag("a");
            for (Element aTag : aTags) {
                if (aTag.html().contains("<strong")) {
                    String actorProfileId = baseURI.resolve(aTag.attr("href")).toString();

                    String actorIdNorm = null;
                    try {
                        actorIdNorm = Parser.normalizeProfileUrl(new URI(actorProfileId));
                    } catch (URISyntaxException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                    if (StringUtils.isNotEmpty(actorIdNorm)) {
                        String[] parts = StringUtils.split(actorIdNorm, "|");
                        actorProfileId = parts[1];
                    }

                    post.setWallProfileId(res.getInfo().getId());
                    post.setActorProfileId(actorProfileId);
                    post.setActorProfileDisplayName(aTag.text());
                    try {
                        String postContent = extractTextFromElements(aTag.parent().getElementsByTag("div"));
                        if (StringUtils.isEmpty(postContent)) {
                            postContent = aTag.parent().getElementsByTag("div").text();
                        }
                        post.setContent(postContent);
                    } catch (NullPointerException ex) {
                        LOG.warn("No post content {}", fb2Html.getRawUrl());
                    }
                    break;
                }
            }
            try {
                Elements times = e.getElementsByTag("abbr");
                if (!times.isEmpty()) {
                    Element time = times.get(0);
                    Date date = Funcs.humanTimeParser(time.text());
                    if (date != null) {
                        post.setPostTime(date);
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
            // find shared link
            for (Element link : aTags) {
                try {
                    String href = link.attr("href");
                    if (href == null) {
                        continue;
                    }
                    URI uri = baseURI.resolve(href);
                    String url = uri.toString();
                    if (!url.contains("facebook.com/l.php?")) {
                        continue;
                    }
                    String sharedUrl = Parser.splitQuery(uri.toURL()).get("u").get(0);
                    if (StringUtils.isNotEmpty(sharedUrl)) {
                        post.setOutsideUrl(sharedUrl);
                        break;
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
            break;
        }

        // find likes
        try {
            for (Element el : els) {
                String id = el.attr("id");
                if (id == null || (!id.startsWith("ufi_"))) {
                    continue;
                }
                Elements subDivATags = el.select("div > a");
                for (Element subDivATag : subDivATags) {
                    String aText = subDivATag.ownText();
                    Element divParent = subDivATag.parent();
                    String divText = divParent.ownText();
                    if (divText.contains("like this")
                            && aText.contains(" people")) {
                        LOG.info("TEXT {} - {}", divText, aText);
                        String numberLikes = aText;
                        try {
                            numberLikes = numberLikes.replace(" people", "").replace(",", "");
                            long numLikeCount = 0;
                            if (StringUtils.isNotEmpty(numberLikes)) {
                                numLikeCount = Long.parseLong(numberLikes);
                            }
                            post.setLikesCount(numLikeCount);
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                        break;
                    }
                }
                break;
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (Element e : els) {
            String id = e.attr("id");
            if (id == null || (!id.startsWith("ufi_"))) {
                continue;
            }
            Elements subDivs = e.getElementsByTag("div");
            for (Element subDiv : subDivs) {
                String divId = subDiv.attr("id");
                if (divId == null || !TParser.containSubstringWithFormatAsRegex(divId, "^[0-9]++$")) {
                    continue;
                }
                Element linkProfile = subDiv.getElementsByTag("a").get(0);
                String profileUrls = baseURI.resolve(linkProfile.attr("href")).toString();

                Comment comment = new Comment();
                comment.setId(divId);
                comment.setPostId(post.getId());

                String actorIdNorm = null;
                try {
                    actorIdNorm = Parser.normalizeProfileUrl(new URI(profileUrls));
                } catch (URISyntaxException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                if (StringUtils.isNotEmpty(actorIdNorm)) {
                    String[] parts = StringUtils.split(actorIdNorm, "|");
                    profileUrls = parts[1];
                }

                comment.setActorProfileId(profileUrls);
                comment.setActorProfileDisplayName(linkProfile.text());
                String textComments = extractTextFromElements(e.getElementsByTag("div"));
                if (StringUtils.isEmpty(textComments)) {
                    try {
                        Element contentDiv = subDiv.getElementsByTag("div").get(1);
                        textComments = contentDiv.text();
                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
                comment.setContent(textComments);
                try {
                    Elements times = e.getElementsByTag("abbr");
                    if (!times.isEmpty()) {
                        Element time = times.get(0);
                        Date date = Funcs.humanTimeParser(time.text());
                        if (date != null) {
                            comment.setCommentTime(date);
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                res.getComments().add(comment);
            }
        }
    }

    /**
     * Process full story pages
     *
     * @param fb2Html
     * @param res
     */
    private static void processPostStoryPageUser(FbUrlToHtml fb2Html, FacebookObject res) {
        URI baseURI = null;
        try {
            baseURI = new URI(fb2Html.getRawUrl());
        } catch (URISyntaxException ex) {
            // Skip this cause we will never met it			
            ex.printStackTrace();
            return;
        }
        Document doc = Jsoup.parse(fb2Html.getRawHtml());
        Elements els = doc.getElementsByTag("div");
        Post post = new Post();
        for (Element e : els) {
            String id = e.attr("id");
            if (id == null || !TParser.containSubstringWithFormatAsRegex(id, "^u_[0-9]_[0-9]$")) {
                continue;
            }
            Elements links = e.getElementsByTag("a");
            String actorId = links.get(0).attr("href");
            actorId = baseURI.resolve(actorId).toString();

            String actorIdNorm = null;
            try {
                actorIdNorm = Parser.normalizeProfileUrl(new URI(actorId));
            } catch (URISyntaxException ex) {
                LOG.error(ex.getMessage(), ex);
            }
            if (StringUtils.isNotEmpty(actorIdNorm)) {
                String[] parts = StringUtils.split(actorIdNorm, "|");
                actorId = parts[1];
            }

            post.setActorProfileId(actorId);
            post.setActorProfileDisplayName(links.get(0).text());
            Pair<String, Integer> postIdAndNumComments = getPostIdAndCommentFromLinks(links, fb2Html);
            String postId = postIdAndNumComments.first;
            if (StringUtils.isEmpty(postId)) {
                postId = getPostIdFromUrl(fb2Html.getRawUrl());
            }
            if (StringUtils.isEmpty(postId)) {
                continue;
            }
            post.setId(postId);
            post.setCommentsCount(postIdAndNumComments.second);
            post.setWallProfileId(res.getInfo().getId());
            try {
                Elements times = e.getElementsByTag("abbr");
                if (!times.isEmpty()) {
                    Element time = times.get(0);
                    Date date = Funcs.humanTimeParser(time.text());
                    if (date != null) {
                        post.setPostTime(date);
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
            // find shared link
            for (Element link : links) {
                try {
                    String href = link.attr("href");
                    if (href == null) {
                        continue;
                    }
                    URI uri = baseURI.resolve(href);
                    String url = uri.toString();
                    if (!url.contains("facebook.com/l.php?")) {
                        continue;
                    }
                    String sharedUrl = Parser.splitQuery(uri.toURL()).get("u").get(0);
                    if (StringUtils.isNotEmpty(sharedUrl)) {
                        post.setOutsideUrl(sharedUrl);
                        break;
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }

            post.setUrl(baseURI.toString());
            post.setContent(extractTextFromElements(new Elements(e)));
            break;
        }
        if (post.getId() == null) {
            LOG.warn("No post ID found in {}", fb2Html.getRawUrl());
            return;
        }

        // find likes
        try {
            for (Element el : els) {
                String id = el.attr("id");
                if (id == null || (!id.startsWith("ufi_"))) {
                    continue;
                }
                Elements subDivATags = el.select("div > a");
                for (Element subDivATag : subDivATags) {
                    String aText = subDivATag.ownText();
                    Element divParent = subDivATag.parent();
                    String divText = divParent.ownText();
                    if (divText.contains("like this")
                            && aText.contains(" people")) {
                        LOG.info("TEXT {} - {}", divText, aText);
                        String numberLikes = aText;
                        try {
                            numberLikes = numberLikes.replace(" people", "").replace(",", "");
                            long numLikeCount = 0;
                            if (StringUtils.isNotEmpty(numberLikes)) {
                                numLikeCount = Long.parseLong(numberLikes);
                            }
                            post.setLikesCount(numLikeCount);
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                        break;
                    }
                }
                break;
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (Element e : els) {
            String id = e.attr("id");
            if (id == null || (!id.startsWith("ufi_"))) {
                continue;
            }
            Elements subDivs = e.getElementsByTag("div");
            for (Element subDiv : subDivs) {
                String divId = subDiv.attr("id");
                if (divId == null || !TParser.containSubstringWithFormatAsRegex(divId, "^[0-9]++$")) {
                    continue;
                }
                Element linkProfile = subDiv.getElementsByTag("a").get(0);
                String profileUrls = baseURI.resolve(linkProfile.attr("href")).toString();
                String textComments = extractTextFromElements(subDiv.getElementsByTag("div"));
                if (StringUtils.isEmpty(textComments)) {
                    try {
                        Element contentDiv = subDiv.getElementsByTag("div").get(1);
                        textComments = contentDiv.text();
                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
                Comment comment = new Comment();
                comment.setId(divId);
                comment.setPostId(post.getId());

                String actorIdNorm = null;
                try {
                    actorIdNorm = Parser.normalizeProfileUrl(new URI(profileUrls));
                } catch (URISyntaxException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                if (StringUtils.isNotEmpty(actorIdNorm)) {
                    String[] parts = StringUtils.split(actorIdNorm, "|");
                    profileUrls = parts[1];
                }

                comment.setActorProfileId(profileUrls);
                comment.setActorProfileDisplayName(linkProfile.text());
                comment.setContent(textComments);
                try {
                    Elements times = e.getElementsByTag("abbr");
                    if (!times.isEmpty()) {
                        Element time = times.get(0);
                        Date date = Funcs.humanTimeParser(time.text());
                        if (date != null) {
                            comment.setCommentTime(date);
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                res.getComments().add(comment);
            }
        }
    }

    /**
     * Processing timeline pages
     *
     * @param fb2Html
     * @param profile
     * @param res
     */
    private static void processTimelinePageUser(FbUrlToHtml fb2Html, FacebookObject res) {
        Document doc = Jsoup.parse(fb2Html.getRawHtml());
        URI baseURI = null;
        try {
            baseURI = new URI(fb2Html.getRawUrl());
        } catch (URISyntaxException ex) {
            // Skip this cause we will never met it
            ex.printStackTrace();
        }
        Elements els = doc.getElementsByTag("div");
        for (Element e : els) {
            try {
                String id = e.attr("id");
                if (!TParser.containSubstringWithFormatAsRegex(id, "^u_[0-9]_[0-9]$")) {
                    continue;
                }
                Post post = new Post();
                Elements links = e.getElementsByTag("a");
                String actorId = links.get(0).attr("href");
                actorId = baseURI.resolve(actorId).toString();
                String actorIdNorm = Parser.normalizeProfileUrl(new URI(actorId));
                if (StringUtils.isNotEmpty(actorIdNorm)) {
                    String[] parts = StringUtils.split(actorIdNorm, "|");
                    actorId = parts[1];
                }
                post.setActorProfileId(actorId);
                post.setActorProfileDisplayName(links.get(0).text());

                post.setWallProfileId(res.getInfo().getId());
                post.setContent(extractTextFromElements(new Elements(e)));

                try {
                    Elements times = e.getElementsByTag("abbr");
                    if (!times.isEmpty()) {
                        Element time = times.get(0);
                        Date date = Funcs.humanTimeParser(time.text());
                        if (date != null) {
                            post.setPostTime(date);
                        }
                    }

                    for (Element link : links) {
                        String linkText = link.ownText();
                        if (StringUtils.equalsIgnoreCase(linkText, "Full Story")) {
                            String linkHref = link.attr("href");
                            linkHref = baseURI.resolve(linkHref).toString();
                            post.setUrl(linkHref);
                            break;
                        }
                    }

                    // find shared link
                    for (Element link : links) {
                        try {
                            String href = link.attr("href");
                            if (href == null) {
                                continue;
                            }
                            URI uri = baseURI.resolve(href);
                            String url = uri.toString();
                            if (!url.contains("facebook.com/l.php?")) {
                                continue;
                            }
                            String sharedUrl = Parser.splitQuery(uri.toURL()).get("u").get(0);
                            if (StringUtils.isNotEmpty(sharedUrl)) {
                                post.setOutsideUrl(sharedUrl);
                                break;
                            }
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    }

                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                Pair<String, Integer> postIdAndComments = getPostIdAndCommentFromLinks(links, fb2Html);
                String postId = postIdAndComments.first;
                int numComments = postIdAndComments.second;
                if (postId != null) {
                    post.setId(postId);
                }
                if (numComments >= 0) {
                    post.setCommentsCount(numComments);
                }

                // find likes
                try {
                    // find a tag has att aria-label
                    Elements likeATags = e.select("a[aria-label=Likes]");
                    if (!likeATags.isEmpty()) {
                        Element likeATag = likeATags.get(0);
                        try {
                            String numberLikes = likeATag.text();
                            LOG.info("TEXT - 1 - {}", numberLikes);
                            numberLikes = StringUtils.replace(numberLikes, ",", "");
                            long numLikeCount = Long.parseLong(numberLikes);
                            post.setLikesCount(numLikeCount);
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                res.getPosts().add(post);
            } catch (Exception ex) {
                ex.printStackTrace();
                LOG.error("Error in parsing post at ", fb2Html.getRawUrl());
            }
        }
    }

    private static final String WITH_PROFILE_STRING = "— with";
    private static final String CONTINUE_READING_STRING = "... Continue Reading";
    private static final String[] UNEXPECTED_STRINGS = new String[]{
        WITH_PROFILE_STRING,
        CONTINUE_READING_STRING,
        " More"
    };

    /**
     * Extract text from web elements, use for facebook parser only
     *
     * Getting text from span tags and remove short texts
     *
     * @param divs
     * @return
     */
    private static String extractTextFromElements(Elements divs) {
        StringBuilder sb = new StringBuilder();
        for (Element e : divs) {
            Elements spanTags = e.getElementsByTag("span");
            if (spanTags == null) {
                continue;
            }
            for (Element span : spanTags) {
                String spanText = span.text();
                if (spanText.length() < 30) {
                    continue;
                }
                sb.append(spanText).append(" . ");
            }
        }

        String text = sb.toString();

        for (String unExpected : UNEXPECTED_STRINGS) {
            if (text.contains(unExpected)) {
                text = text.replace(unExpected, "");
//                text = text.substring(0, text.indexOf(unExpected));
            }
        }
        return text;
    }

    private static void processTypeGroup(String profileUrl, List<FbUrlToHtml> htmls, FacebookObject res) {
        LOG.debug("###############");
        LOG.debug(profileUrl);
        for (FbUrlToHtml html : htmls) {
            FacebookUrlType urlType = getTypeFromUrl(html.getRawUrl());
            LOG.debug(urlType + " - " + html.getRawHtml());
            switch (urlType) {
                case PROFILE: {
                    Document doc = Jsoup.parse(html.getRawHtml());
                    URI baseURI = null;
                    try {
                        baseURI = new URI(html.getRawUrl());
                    } catch (URISyntaxException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                    Elements timelineEls = doc.select("div#m-timeline-cover-section");
                    for (Element timelineEl : timelineEls) {
                        Elements links = timelineEl.getElementsByTag("a");
                        for (Element link : links) {
                            String text = link.text();
                            String href = link.attr("href");
                            if (StringUtils.equals(text, "More")) {
                                String profileId = TParser.getContent(href, "/pages/more/", "/\\?");
                                res.getInfo().setId(profileId);
                                break;
                            }
                            if (StringUtils.equals(text, "Message")) {
                                String profileId = TParser.getContent(href, "/messages/thread/", "/\\?");
                                res.getInfo().setId(profileId);
                                break;
                            }
                        }
                        res.getInfo().setUrl(html.getRawUrl());
                        res.getInfo().setFullname(doc.title());
                        res.getInfo().setType("page");
                    }

                    try {
                        Elements els = doc.select("div#m_group_stories_container").get(0).select("div > div[class]");
                        for (Element e : els) {
                            try {
                                String id = e.attr("id");
                                if (!TParser.containSubstringWithFormatAsRegex(id, "^u_[0-9]_[0-9a-z]$")) {
                                    continue;
                                }
                                Post post = new Post();
                                post.setWallProfileId(profileUrl);

                                Elements links = e.getElementsByTag("a");

                                String actorId = links.get(0).attr("href");
                                actorId = baseURI.resolve(actorId).toString();
                                String actorIdNorm = Parser.normalizeProfileUrl(new URI(actorId));
                                if (StringUtils.isNotEmpty(actorIdNorm)) {
                                    String[] parts = StringUtils.split(actorIdNorm, "|");
                                    actorId = parts[1];
                                }
                                post.setActorProfileId(actorId);
                                post.setActorProfileDisplayName(links.get(0).text());

                                Pair<String, Integer> postIdAndComments = getPostIdAndCommentFromLinks(links, html);
                                String postId = postIdAndComments.first;
                                int numComments = postIdAndComments.second;
                                if (postId != null) {
                                    post.setId(postId);
                                }
                                if (numComments >= 0) {
                                    post.setCommentsCount(numComments);
                                }

                                try {
                                    Elements times = e.getElementsByTag("abbr");
                                    if (!times.isEmpty()) {
                                        Element time = times.get(0);
                                        Date date = Funcs.humanTimeParser(time.text());
                                        if (date != null) {
                                            post.setPostTime(date);
                                        }
                                    }

                                    for (Element link : links) {
                                        String linkText = link.ownText();
                                        if (StringUtils.equalsIgnoreCase(linkText, "Full Story")) {
                                            String linkHref = link.attr("href");
                                            linkHref = baseURI.resolve(linkHref).toString();
                                            post.setUrl(linkHref);
                                            break;
                                        }
                                    }

                                } catch (Exception ex) {
                                    LOG.error(ex.getMessage(), ex);
                                }

                                // find likes
                                try {
                                    // find a tag has att aria-label
                                    Elements likeATags = e.select("a[aria-label=Likes]");
                                    if (!likeATags.isEmpty()) {
                                        Element likeATag = likeATags.get(0);
                                        try {
                                            String numberLikes = likeATag.text();
                                            LOG.info("TEXT - 1 - {}", numberLikes);
                                            numberLikes = StringUtils.replace(numberLikes, ",", "");
                                            long numLikeCount = Long.parseLong(numberLikes);
                                            post.setLikesCount(numLikeCount);
                                        } catch (Exception ex) {
                                            LOG.error(ex.getMessage(), ex);
                                        }
                                    }
                                } catch (Exception ex) {
                                    LOG.error(ex.getMessage(), ex);
                                }

                                StringBuilder sb = new StringBuilder();
                                Elements spans = e.getElementsByTag("span");
                                for (Element span : spans) {
                                    span.select("a[href]").remove();
                                    String tempText = span.text();
                                    if (tempText.length() >= 30) {
                                        sb.append(tempText);
                                        sb.append(" ");
                                    }
                                }
                                post.setContent(sb.toString());
                                res.getPosts().add(post);
                            } catch (Exception ex) {
                                LOG.error(ex.getMessage(), ex);
                                LOG.error("Error in parsing post at ", html.getRawUrl());
                            }
                        }
                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                    break;
                }
                case POST_PHOTO:
                    processPostPhotoPage(html, res);
                    break;
                case POST_STORY:
                    processPostStoryPage(html, res);
                    break;
            }
        }
    }

    private static void processTypePage(String profileUrl, List<FbUrlToHtml> htmls, FacebookObject res) {
        LOG.debug("###############");
        LOG.debug(profileUrl);
        for (FbUrlToHtml html : htmls) {
            FacebookUrlType urlType = getTypeFromUrl(html.getRawUrl());
            LOG.debug(urlType + " - " + html.getRawHtml());
            switch (urlType) {
                case PROFILE:
                case TIMELINE: {
                    Document doc = Jsoup.parse(html.getRawHtml());
                    URI baseURI = null;
                    try {
                        baseURI = new URI(html.getRawUrl());
                    } catch (URISyntaxException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                    Elements timelineEls = doc.select("div#m-timeline-cover-section");
                    for (Element timelineEl : timelineEls) {
                        Elements links = timelineEl.getElementsByTag("a");
                        for (Element link : links) {
                            String text = link.text();
                            String href = link.attr("href");
                            if (StringUtils.equals(text, "More")) {
                                String profileId = TParser.getContent(href, "/pages/more/", "/\\?");
                                res.getInfo().setId(profileId);
                                break;
                            }
                            if (StringUtils.equals(text, "Message")) {
                                String profileId = TParser.getContent(href, "/messages/thread/", "/\\?");
                                res.getInfo().setId(profileId);
                                break;
                            }
                        }
                        res.getInfo().setUrl(html.getRawUrl());
                        res.getInfo().setFullname(doc.title());
                        res.getInfo().setType("page");
                    }

                    try {
                        Elements els = doc.select("div#recent").get(0).select("div[class]");
                        for (Element e : els) {
                            try {
                                String id = e.attr("id");
                                if (!TParser.containSubstringWithFormatAsRegex(id, "^u_[0-9]_[0-9a-z]$")) {
                                    continue;
                                }
                                Post post = new Post();

                                String wallProfileId = res.getInfo().getId();
                                if (StringUtils.isEmpty(wallProfileId)) {
                                    wallProfileId = profileUrl;
                                }
                                post.setWallProfileId(wallProfileId);
                                post.setActorProfileId(wallProfileId);

                                try {
                                    Elements divContents = e.select("div[id] > div > div > span");
                                    if (!divContents.isEmpty()) {
                                        String text = divContents.get(0).text();
                                        post.setActorProfileDisplayName(text);
                                    }
                                } catch (Exception ex) {
                                    LOG.error(ex.getMessage(), ex);
                                }

                                Elements links = e.getElementsByTag("a");
                                Pair<String, Integer> postIdAndComments = getPostIdAndCommentFromLinks(links, html);
                                String postId = postIdAndComments.first;
                                int numComments = postIdAndComments.second;
                                if (postId != null) {
                                    post.setId(postId);
                                }
                                if (numComments >= 0) {
                                    post.setCommentsCount(numComments);
                                }

                                // find likes
                                try {
                                    // find a tag has att aria-label
                                    Elements likeATags = e.select("a[aria-label=Likes]");
                                    if (!likeATags.isEmpty()) {
                                        Element likeATag = likeATags.get(0);
                                        try {
                                            String numberLikes = likeATag.text();
                                            LOG.info("TEXT - 1 - {}", numberLikes);
                                            numberLikes = StringUtils.replace(numberLikes, ",", "");
                                            long numLikeCount = Long.parseLong(numberLikes);
                                            post.setLikesCount(numLikeCount);
                                        } catch (Exception ex) {
                                            LOG.error(ex.getMessage(), ex);
                                        }
                                    }
                                } catch (Exception ex) {
                                    LOG.error(ex.getMessage(), ex);
                                }

                                try {
                                    Elements times = e.getElementsByTag("abbr");
                                    if (!times.isEmpty()) {
                                        Element time = times.get(0);
                                        Date date = Funcs.humanTimeParser(time.text());
                                        if (date != null) {
                                            post.setPostTime(date);
                                        }
                                    }

                                    for (Element link : links) {
                                        String linkText = link.ownText();
                                        if (StringUtils.contains(linkText, "Comment")) {
                                            String linkHref = link.attr("href");
                                            linkHref = baseURI.resolve(linkHref).toString();
                                            post.setUrl(linkHref);
                                            break;
                                        }
                                    }

                                } catch (Exception ex) {
                                    LOG.error(ex.getMessage(), ex);
                                }

                                // find shared link
                                for (Element link : links) {
                                    try {
                                        String href = link.attr("href");
                                        if (href == null) {
                                            continue;
                                        }
                                        URI uri = baseURI.resolve(href);
                                        String url = uri.toString();
                                        if (!url.contains("facebook.com/l.php?")) {
                                            continue;
                                        }
                                        String sharedUrl = Parser.splitQuery(uri.toURL()).get("u").get(0);
                                        if (StringUtils.isNotEmpty(sharedUrl)) {
                                            post.setOutsideUrl(sharedUrl);
                                            break;
                                        }
                                    } catch (Exception ex) {
                                        LOG.error(ex.getMessage(), ex);
                                    }
                                }

                                StringBuilder sb = new StringBuilder();
//                                Elements spans = e.getElementsByTag("span");
//                                for (Element span : spans) {
//                                    span.select("a[href]").remove();
//                                    String tempText = span.text();
//                                    if (tempText.length() >= 30) {
//                                        sb.append(tempText);
//                                        sb.append(" ");
//                                    }
//                                }
                                try {
                                    Elements divContents = e.select("div[id] > div > div");
                                    if (!divContents.isEmpty()) {
                                        String text = divContents.get(1).text();
                                        sb.append(text);
                                    }
                                } catch (Exception ex) {
                                    LOG.error(ex.getMessage(), ex);
                                }

                                post.setContent(sb.toString());
                                res.getPosts().add(post);
                            } catch (Exception ex) {
                                LOG.error(ex.getMessage(), ex);
                                LOG.error("Error in parsing post at ", html.getRawUrl());
                            }
                        }
                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                    break;
                }
                case POST_PHOTO:
                    processPostPhotoPage(html, res);
                    break;
                case POST_STORY:
                    processPostStoryPage(html, res);
                    break;
            }
        }
    }

    /**
     * Process photo pages
     *
     * @param fb2Html
     * @param res
     */
    private static void processPostPhotoPage(FbUrlToHtml fb2Html, FacebookObject res) {
        URI baseURI = null;
        try {
            baseURI = new URI(fb2Html.getRawUrl());
        } catch (URISyntaxException ex) {
            // Skip this cause we will never met it			
            ex.printStackTrace();
            return;
        }
        Document doc = Jsoup.parse(fb2Html.getRawHtml());
        Elements els = doc.getElementsByTag("div");
        Post post = new Post();
        post.setId(getPostIdFromUrl(fb2Html.getRawUrl()));

        if (post.getId() == null) {
            LOG.warn("No post ID found in {}", fb2Html.getRawUrl());
            return;
        }
        for (Element e : els) {
            String id = e.attr("id");
            if (id == null || (!id.equals("root"))) {
                continue;
            }
            Elements aTags = e.getElementsByTag("a");
            for (Element aTag : aTags) {
                if (aTag.html().contains("<strong")) {
                    String actorProfileId = baseURI.resolve(aTag.attr("href")).toString();
                    String actorIdNorm = null;
                    try {
                        actorIdNorm = Parser.normalizeProfileUrl(new URI(actorProfileId));
                    } catch (URISyntaxException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                    if (StringUtils.isNotEmpty(actorIdNorm)) {
                        String[] parts = StringUtils.split(actorIdNorm, "|");
                        actorProfileId = parts[1];
                    }
                    post.setActorProfileId(actorProfileId);
                    post.setActorProfileDisplayName(aTag.text());
                    try {
                        post.setContent(aTag.parent().getElementsByTag("div").text());
                    } catch (NullPointerException ex) {
                        LOG.warn("No post content {}", fb2Html.getRawUrl());
                    }
                    break;
                }
            }
            try {
                Elements times = e.getElementsByTag("abbr");
                if (!times.isEmpty()) {
                    Element time = times.get(0);
                    Date date = Funcs.humanTimeParser(time.text());
                    if (date != null) {
                        post.setPostTime(date);
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
            // find shared link
            for (Element link : aTags) {
                try {
                    String href = link.attr("href");
                    if (href == null) {
                        continue;
                    }
                    URI uri = baseURI.resolve(href);
                    String url = uri.toString();
                    if (!url.contains("facebook.com/l.php?")) {
                        continue;
                    }
                    String sharedUrl = Parser.splitQuery(uri.toURL()).get("u").get(0);
                    if (StringUtils.isNotEmpty(sharedUrl)) {
                        post.setOutsideUrl(sharedUrl);
                        break;
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
            post.setUrl(fb2Html.getRawUrl());
            break;
        }

        // find likes
        try {
            for (Element el : els) {
                String id = el.attr("id");
                if (id == null || (!id.startsWith("ufi_"))) {
                    continue;
                }
                Elements subDivATags = el.select("div > a");
                for (Element subDivATag : subDivATags) {
                    String aText = subDivATag.ownText();
                    Element divParent = subDivATag.parent();
                    String divText = divParent.ownText();
                    if (divText.contains("like this")
                            && aText.contains(" people")) {
                        LOG.info("TEXT {} - {}", divText, aText);
                        String numberLikes = aText;
                        try {
                            numberLikes = numberLikes.replace(" people", "").replace(",", "");
                            long numLikeCount = 0;
                            if (StringUtils.isNotEmpty(numberLikes)) {
                                numLikeCount = Long.parseLong(numberLikes);
                            }
                            post.setLikesCount(numLikeCount);
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                        break;
                    }
                }
                break;
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (Element e : els) {
            String id = e.attr("id");
            if (id == null || (!id.startsWith("ufi_"))) {
                continue;
            }
            Elements subDivs = e.getElementsByTag("div");
            for (Element subDiv : subDivs) {
                String divId = subDiv.attr("id");
                if (divId == null || !TParser.containSubstringWithFormatAsRegex(divId, "^[0-9]++$")) {
                    continue;
                }
                Element linkProfile = subDiv.getElementsByTag("a").get(0);
                String profileUrls = baseURI.resolve(linkProfile.attr("href")).toString();
                String textComments = subDiv.getElementsByTag("div").get(1).text();
                Comment comment = new Comment();
                comment.setId(divId);
                comment.setPostId(post.getId());

                String actorIdNorm = null;
                try {
                    actorIdNorm = Parser.normalizeProfileUrl(new URI(profileUrls));
                } catch (URISyntaxException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                if (StringUtils.isNotEmpty(actorIdNorm)) {
                    String[] parts = StringUtils.split(actorIdNorm, "|");
                    profileUrls = parts[1];
                }

                comment.setActorProfileId(profileUrls);
                comment.setActorProfileDisplayName(linkProfile.text());
                comment.setContent(textComments);
                try {
                    Elements times = e.getElementsByTag("abbr");
                    if (!times.isEmpty()) {
                        Element time = times.get(0);
                        Date date = Funcs.humanTimeParser(time.text());
                        if (date != null) {
                            comment.setCommentTime(date);
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                res.getComments().add(comment);
            }
        }
    }

    /**
     * Process full story pages
     *
     * @param fb2Html
     * @param res
     */
    private static void processPostStoryPage(FbUrlToHtml fb2Html, FacebookObject res) {
        URI baseURI = null;
        try {
            baseURI = new URI(fb2Html.getRawUrl());
        } catch (URISyntaxException ex) {
            // Skip this cause we will never met it			
            ex.printStackTrace();
            return;
        }
        Document doc = Jsoup.parse(fb2Html.getRawHtml());
        Elements els = doc.getElementsByTag("div");
        Post post = new Post();
        for (Element e : els) {
            String id = e.attr("id");
            if (id == null || !TParser.containSubstringWithFormatAsRegex(id, "^u_[0-9]_[0-9]$")) {
                continue;
            }
            Elements links = e.getElementsByTag("a");
            String actorId = links.get(0).attr("href");
            actorId = baseURI.resolve(actorId).toString();

            String actorIdNorm = null;
            try {
                actorIdNorm = Parser.normalizeProfileUrl(new URI(actorId));
            } catch (URISyntaxException ex) {
                LOG.error(ex.getMessage(), ex);
            }
            if (StringUtils.isNotEmpty(actorIdNorm)) {
                String[] parts = StringUtils.split(actorIdNorm, "|");
                actorId = parts[1];
            }

            post.setActorProfileId(actorId);
            post.setActorProfileDisplayName(links.get(0).text());
            Pair<String, Integer> postIdAndNumComments = getPostIdAndCommentFromLinks(links, fb2Html);
            String postId = postIdAndNumComments.first;
            if (StringUtils.isEmpty(postId)) {
                postId = getPostIdFromUrl(fb2Html.getRawUrl());
            }
            if (StringUtils.isEmpty(postId)) {
                continue;
            }
            post.setId(postId);
            post.setCommentsCount(postIdAndNumComments.second);
            post.setWallProfileId(res.getInfo().getId());
            post.setContent(e.text());
            try {
                Elements times = e.getElementsByTag("abbr");
                if (!times.isEmpty()) {
                    Element time = times.get(0);
                    Date date = Funcs.humanTimeParser(time.text());
                    if (date != null) {
                        post.setPostTime(date);
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
            // find shared link
            for (Element link : links) {
                try {
                    String href = link.attr("href");
                    if (href == null) {
                        continue;
                    }
                    URI uri = baseURI.resolve(href);
                    String url = uri.toString();
                    if (!url.contains("facebook.com/l.php?")) {
                        continue;
                    }
                    String sharedUrl = Parser.splitQuery(uri.toURL()).get("u").get(0);
                    if (StringUtils.isNotEmpty(sharedUrl)) {
                        post.setOutsideUrl(sharedUrl);
                        break;
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
            post.setUrl(fb2Html.getRawUrl());
            break;
        }
        if (post.getId() == null) {
            LOG.warn("No post ID found in {}", fb2Html.getRawUrl());
            return;
        }

        // find likes
        try {
            for (Element el : els) {
                String id = el.attr("id");
                if (id == null || (!id.startsWith("ufi_"))) {
                    continue;
                }
                Elements subDivATags = el.select("div > a");
                for (Element subDivATag : subDivATags) {
                    String aText = subDivATag.ownText();
                    Element divParent = subDivATag.parent();
                    String divText = divParent.ownText();
                    if (divText.contains("like this")
                            && aText.contains(" people")) {
                        LOG.info("TEXT {} - {}", divText, aText);
                        String numberLikes = aText;
                        try {
                            numberLikes = numberLikes.replace(" people", "").replace(",", "");
                            long numLikeCount = 0;
                            numLikeCount = Long.parseLong(numberLikes);
                            post.setLikesCount(numLikeCount);
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                        break;
                    }
                }
                break;
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (Element e : els) {
            String id = e.attr("id");
            if (id == null || (!id.startsWith("ufi_"))) {
                continue;
            }
            Elements subDivs = e.getElementsByTag("div");
            for (Element subDiv : subDivs) {
                String divId = subDiv.attr("id");
                if (divId == null || !TParser.containSubstringWithFormatAsRegex(divId, "^[0-9]++$")) {
                    continue;
                }
                Element linkProfile = subDiv.getElementsByTag("a").get(0);
                String profileUrls = baseURI.resolve(linkProfile.attr("href")).toString();
                String textComments = subDiv.getElementsByTag("div").get(1).text();

                Comment comment = new Comment();
                comment.setId(divId);
                comment.setPostId(post.getId());

                String actorIdNorm = null;
                try {
                    actorIdNorm = Parser.normalizeProfileUrl(new URI(profileUrls));
                } catch (URISyntaxException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                if (StringUtils.isNotEmpty(actorIdNorm)) {
                    String[] parts = StringUtils.split(actorIdNorm, "|");
                    profileUrls = parts[1];
                }

                comment.setActorProfileId(profileUrls);
                comment.setActorProfileDisplayName(linkProfile.text());
                comment.setContent(textComments);
                try {
                    Elements times = e.getElementsByTag("abbr");
                    if (!times.isEmpty()) {
                        Element time = times.get(0);
                        Date date = Funcs.humanTimeParser(time.text());
                        if (date != null) {
                            comment.setCommentTime(date);
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                res.getComments().add(comment);
            }
        }
    }

    /**
     * Extract postId and Comments from Links in post content
     *
     * @param links
     * @param fb2Html
     * @return
     */
    private static Pair<String, Integer> getPostIdAndCommentFromLinks(Elements links, FbUrlToHtml fb2Html) {

        String postId = null;
        int numComments = -1;
        for (int i = 1; i < links.size(); i++) {
            try {
                if (postId == null) {
                    postId = getPostIdFromUrl(links.get(i).attr("href"));
                }
                if (numComments == -1) {
                    String anchor = links.get(i).text();
                    if (TParser.containSubstringWithFormatAsRegex(anchor, "[0-9]++ Comment[s]?")) {
                        numComments = Integer.parseInt(TParser.getOneInGroup(anchor, "[0-9]++"));
                    }
                }
            } catch (NumberFormatException | NullPointerException ex) {
                LOG.error("Error in getting postId from {}", fb2Html.getRawUrl());
                ex.printStackTrace();
            }
        }
        return new Pair<>(postId, numComments);
    }

    private static String getPostIdFromUrl(String url) {
        ///photo.php?fbid=607531879257044&id=100000007125369&set=a.332932590050309.90408.100000007125369&refid=17&_ft_&__tn__=E
        if (url.contains("photo.php?")) {
            return TParser.getContent(url, "fbid=", "&");
        }
        if (url.contains("story.php?")) {
            return TParser.getContent(url, "story_fbid=", "&");
        }
        if (url.contains("/photos/")) {
            String temp = TParser.getOneInGroup(url, "/[0-9]++/");
            return temp.replaceAll("/", "");
        }
        LOG.warn("No post Id for {}", url);
        return null;
    }

    public static FacebookObject fromHtmltoFacebookObject(List<FbUrlToHtml> htmls) {

        FacebookObject res = new FacebookObject();
        Profile profile = new Profile();
        res.setInfo(profile);
        res.setPosts(new ArrayList<Post>());
        res.setComments(new ArrayList<Comment>());

        for (FbUrlToHtml html : htmls) {
            FacebookUrlType urlType = getTypeFromUrl(html.getRawUrl());
            switch (urlType) {
                case PROFILE:
                    processProfilePage(html, htmls, res);
                    break;
            }
        }

        LOG.debug(res.toString());
        return res;
    }

//    public static void main(String[] args) {
//        String pathFolder = "D:\\git\\abc\\review-data\\storage";
//        // String pathFolder = "/home/duongth5/data/facebook";
//        try {
//            File folder = new File(pathFolder);
//            File[] listOfFiles = folder.listFiles();
//            for (File file : listOfFiles) {
//                if (file.isFile()) {
//                    LOG.debug("File {}", file.getAbsolutePath());
//                    List<FbUrlToHtml> htmls = (List<FbUrlToHtml>) FileUtils.readObjectFromFile(file, false);
//                    fromHtmltoFacebookObject(htmls);
//                } else if (file.isDirectory()) {
//                    LOG.warn("Directory {}", file.getAbsolutePath());
//                }
//            }
//        } catch (Exception ex) {
//            LOG.error(ex.getMessage(), ex);
//        }
//    }
}
