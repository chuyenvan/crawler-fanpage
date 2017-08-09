package viettel.nfw.social.reviewdata;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.model.facebook.Comment;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.model.facebook.Post;
import viettel.nfw.social.model.facebook.Profile;
import viettel.nfw.social.utils.Pair;
import vn.viettel.engine.utils.TParser;
import vn.viettel.social.fb.test.FbUrlToHtml;

/**
 *
 *
 * Combine all info from htmls to FB object
 *
 *
 * Note that this is only draft verion, precombine all regex in the futures
 *
 * @author thiendn2
 *
 * Created on Mar 28, 2015, 3:33:17 PM
 */
public class SocialObjectUtils {

    private static final Logger LOG = LoggerFactory.getLogger(SocialObjectUtils.class);

    private static String getPostIdFromUrl(String url) {
        ///photo.php?fbid=607531879257044&id=100000007125369&set=a.332932590050309.90408.100000007125369&refid=17&_ft_&__tn__=E
        if (url.contains("photo.php?") || url.contains("story.php?")) {
            return TParser.getContent(url, "id=", "&");
        }
        LOG.warn("No post Id for {}", url);
        return null;
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
    private static void processProfilePage(boolean foundProfilePage, FacebookUrlType urlType, ProfileType profileType, FbUrlToHtml fb2Html, Profile profile) {
        if (foundProfilePage) {
            LOG.error("Profile url was found, please check {}", urlType);
            return;
        }
        foundProfilePage = true;
        Document profileHTMLDoc = Jsoup.parse(fb2Html.getRawHtml());

        try {
            // detect profile type
            Map<String, String> profileTypeMap = CommonParser.identifyProfileType(fb2Html.getRawUrl(), profileHTMLDoc);
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
        } catch (MalformedURLException | UnsupportedEncodingException ex) {
            LOG.warn("Error in detecting profileType");
        }
        if (profileType == null) {
            return;
        }
        switch (profileType) {
            case USER:
                String profileId = TParser.getContent(fb2Html.getRawHtml(), "/profile/picture/view/\\?profile_id=", "['\"]");
                if (profileId == null) {
                    profileId = TParser.getContent(fb2Html.getRawHtml(), "/messages/thread/", "/");
                }
                ///privacy/touch/block/confirm/?bid=100000956897310&ret_cancel&gfid=AQAIJ-E6kmmMtHKf
                if (profileId == null) {
                    profileId = TParser.getContent(fb2Html.getRawHtml(), "privacy/touch/block/confirm/\\?bid=", "&");
                }
                if (profileId == null) {
                    LOG.error("profileId is null at {}", fb2Html.getRawUrl());
                    break;
                }
                profile.setId(profileId);
                profile.setUrl(fb2Html.getRawUrl());
                profile.setFullname(TParser.getContent(fb2Html.getRawHtml(), "<strong class=\"bp\">", "</strong>"));
                break;
            default:
                break;
        }
    }

    /**
     * Processing timeline pages
     *
     * @param fb2Html
     * @param profile
     * @param res
     */
    private static void processTimelinePage(FbUrlToHtml fb2Html, Profile profile, FacebookObject res) {
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
                post.setActorProfileId(actorId);
                post.setWallProfileId(profile.getId());
                post.setContent(extractTextFromElements(new Elements(e)));
                Pair<String, Integer> postIdAndComments = getPostIdAndCommentFromLinks(links, fb2Html);
                String postId = postIdAndComments.first;
                int numComments = postIdAndComments.second;
                if (postId != null) {
                    post.setId(postId);
                }
                if (numComments >= 0) {
                    post.setCommentsCount(numComments);
                }
                res.getPosts().add(post);
            } catch (Exception ex) {
                ex.printStackTrace();
                LOG.error("Error in parsing post at ", fb2Html.getRawUrl());
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

    private static final String WITH_PROFILE_STRING = "— with";
    private static final String CONTINUE_READING_STRING = "... Continue Reading";
    private static final String[] UNEXPECTED_STRINGS = new String[]{
        WITH_PROFILE_STRING,
        CONTINUE_READING_STRING
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
                text = text.substring(0, text.indexOf(unExpected));
            }
        }
        return text;
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
                    post.setActorProfileId(actorProfileId);
                    try {
                        String postContent = extractTextFromElements(aTag.parent().getElementsByTag("div"));
                        post.setContent(postContent);
                    } catch (NullPointerException ex) {
                        LOG.warn("No post content {}", fb2Html.getRawUrl());
                    }
                    break;
                }
            }
            break;
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
                comment.setActorProfileId(profileUrls);
                String textComments = extractTextFromElements(e.getElementsByTag("div"));
                comment.setContent(textComments);
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
            post.setActorProfileId(actorId);
            Pair<String, Integer> postIdAndNumComments = getPostIdAndCommentFromLinks(links, fb2Html);
            String postId = postIdAndNumComments.first;
            if (postId == null) {
                continue;
            }
            post.setId(postId);
            post.setCommentsCount(postIdAndNumComments.second);
            post.setWallProfileId(res.getInfo().getId());

            post.setContent(extractTextFromElements(new Elements(e)));
            break;
        }
        if (post.getId() == null) {
            LOG.warn("No post ID found in {}", fb2Html.getRawUrl());
            return;
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
                Comment comment = new Comment();
                comment.setId(divId);
                comment.setPostId(post.getId());
                comment.setActorProfileId(profileUrls);
                comment.setContent(textComments);
                res.getComments().add(comment);
            }
        }
    }

    /**
     * Convert from downloaded html to facebook object
     *
     * @param htmls
     * @return
     */
    public static FacebookObject fromHtmls2FacebookObject(List<FbUrlToHtml> htmls) {
        FacebookObject res = new FacebookObject();

        Profile profile = new Profile();
        res.setInfo(profile);
        res.setPosts(new ArrayList<Post>());
        res.setComments(new ArrayList<Comment>());
        boolean foundProfilePage = false;
        ProfileType profileType = null;
        for (FbUrlToHtml fb2Html : htmls) {
            FacebookUrlType urlType = getTypeFromUrl(fb2Html.getRawUrl());
            switch (urlType) {
                case PROFILE:
                    processProfilePage(foundProfilePage, urlType, profileType, fb2Html, profile);
                    break;
                case TIMELINE:
                    processTimelinePage(fb2Html, profile, res);
                    break;
                case POST_STORY:
                    processPostStoryPage(fb2Html, res);
                    break;
                case POST_PHOTO:
                    processPostPhotoPage(fb2Html, res);
                    break;
            }
        }
        return res;
    }

    private static enum ProfileType {

        PAGE, GROUP, USER
    }

    private static enum FacebookUrlType {

        PROFILE, TIMELINE, POST_PHOTO, POST_STORY, UNDEFINED,
    }

    private static FacebookUrlType getTypeFromUrl(String url) {
        if (url.contains("?v=timeline")) {
            return FacebookUrlType.TIMELINE;
        }
        if (url.contains("story.php?")) {
            return FacebookUrlType.POST_STORY;
        }
        if (url.contains("photo.php")) {
            return FacebookUrlType.POST_PHOTO;
        }
        return FacebookUrlType.PROFILE;
    }

}
