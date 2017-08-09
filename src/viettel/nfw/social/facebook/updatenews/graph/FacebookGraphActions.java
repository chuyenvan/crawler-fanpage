package viettel.nfw.social.facebook.updatenews.graph;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import viettel.nfw.social.facebook.updatenews.repo.ProfilePostsRepository;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import com.restfb.Connection;
import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.FacebookClient.AccessToken;
import com.restfb.Parameter;
import com.restfb.Version;
import com.restfb.exception.FacebookException;
import com.restfb.exception.FacebookOAuthException;
import com.restfb.types.Comment;
import com.restfb.types.Group;
import com.restfb.types.Page;
import com.restfb.types.Post;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Proxy;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectType;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.social.utils.HttpResponseInfo;
import vn.viettel.social.utils.consts.SCommon;

/**
 *
 * @author duongth5
 */
public class FacebookGraphActions {

    private static final Logger LOG = LoggerFactory.getLogger(FacebookGraphActions.class);

    // url format
    private static final String GRAPH_URL = "https://graph.facebook.com/";
    private static final SimpleDateFormat SDF_YMD = new SimpleDateFormat("yyyyMMdd");

//	private static final String FORMAT_URL_GROUP_INFO = "%s%s?format=json&access_token=%s"; // graph_url/group_id/access_token
//	private static final String FORMAT_URL_POST_INFO = "%s%s?fields=%s&access_token=%s"; // graph_url/post_id/post_fields/access_token
//	private static final String FORMAT_URL_POST_LIKES = "%s%s/likes?fields=id,name&format=json&access_token=%s&limit=%s"; // graph_url/post_id/access_token/limit
//	private static final String FORMAT_URL_POST_INFO_COUNT_LIKES_COMMENTS = "%s%s?fields=%s&access_token=%s"; // graph_url/post_id/post_fields/access_token
    private static final int NUMBER_RETRY = 3;
    private static final long SLEEP_TIME_BETWEEN_RETRY = Funcs.randInt(1000, 2000);
    private static final int MAX_NEXT_PAGE = 3;
    private static final int ALLOW_MAX_OLD_POSTS = 10;
    private static final int MAX_DAYS_FOR_QUERY_FEED = 10;
    private static final double PERCENT_NEW_POSTS = 0.8;
    private static final int MAX_SIZE = 100000;

    // fields for query restfb
    private static final String FIELDS = "fields";
    private static final String LIMIT = "limit";
    private static final String FORMAT_PAGE_FEED = "%s/feed"; // page_id/feed
    private static final String FORMAT_POST_COMMENTS = "%s/comments"; // post_id/comments
    private static final String PAGE_INFO_FIELDS = "id,username,name,link,mission,general_info,description,about,website,phone,likes";
    private static final String GROUP_INFO_FIELDS = "id,name,privacy,description,link,owner,email,parent";
    private static final String POST_INFO_FIELDS = "id,from,to,status_type,message,description,link,likes.limit(10){id,name},comments.limit(10){attachment,from,message,created_time,id,like_count},created_time,updated_time,shares,type,caption";
    private static final String POST_SUMMARY_FIELDS = "likes.summary(true),comments.summary(true)";
    private static final String COMMENT_FIELDS = "from,message,created_time,id,attachment,like_count";
    private static final int COMMENT_LIMIT = 100;
    private static final long TIME_REFRESH_TOKEN_BEFORE_EXPIRED = 6 * 60 * 60 * 1000L; // 6 hours
    private static final Random RANDOMIZE = new Random();

    private FacebookClient facebookClient;
    private FacebookApp appInfo;
    private FacebookClient.DebugTokenInfo tokenInfo;

    public FacebookGraphActions(FacebookApp appInfo) {
        this.facebookClient = new DefaultFacebookClient(
                    appInfo.getUserAccessToken(),
                    appInfo.getAppSecret(),
                    getVersion(appInfo.getApiVersion())
            );
        this.appInfo = appInfo;
        this.tokenInfo = null;
    }

    public boolean initApp() {
        boolean isOK = true;
        try {
            this.facebookClient = new DefaultFacebookClient(
                    appInfo.getUserAccessToken(),
                    appInfo.getAppSecret(),
                    getVersion(appInfo.getApiVersion())
            );
        } catch (Exception ex) {
            LOG.error("Error in init Facebook App {}", appInfo.getAppID());
            LOG.error(ex.getMessage(), ex);
            isOK = false;
        }
        return isOK;
    }

    public boolean refreshApp() {
        boolean isOK = true;
        try {
            this.facebookClient = null;
            Funcs.sleep(900);
            LOG.info("Refresh app {} with access_oken: {}", appInfo.getAppID(), appInfo.getUserAccessToken());
            this.facebookClient = new DefaultFacebookClient(
                    appInfo.getUserAccessToken(),
                    appInfo.getAppSecret(),
                    getVersion(appInfo.getApiVersion())
            );
        } catch (Exception ex) {
            LOG.error("Error while refreshing app {}", appInfo.getAppID());
            LOG.error(ex.getMessage(), ex);
            isOK = false;
        }
        return isOK;
    }

    public FacebookApp getAppInfo() {
        return appInfo;
    }

    public void setAppInfo(FacebookApp appInfo) {
        this.appInfo = appInfo;
    }

    public FacebookClient.DebugTokenInfo getTokenInfo() {
        return tokenInfo;
    }

    public void setTokenInfo(FacebookClient.DebugTokenInfo tokenInfo) {
        this.tokenInfo = tokenInfo;
    }

    private static Version getVersion(String apiVersionStr) {
        Version version = Version.UNVERSIONED;
        if (StringUtils.isNotEmpty(apiVersionStr)) {
            if (StringUtils.equalsIgnoreCase(apiVersionStr, "v2.3")) {
                version = Version.VERSION_2_3;
            } else if (StringUtils.equalsIgnoreCase(apiVersionStr, "v2.4")) {
                version = Version.VERSION_2_4;
            } else if (StringUtils.equalsIgnoreCase(apiVersionStr, "v2.5")) {
                version = Version.VERSION_2_5;
            } else if (StringUtils.equalsIgnoreCase(apiVersionStr, "v2.6")) {
                version = Version.VERSION_2_6;
            } else if (StringUtils.equalsIgnoreCase(apiVersionStr, "v2.7")) {
                version = Version.VERSION_2_7;
            }
        }
        return version;
    }

    /**
     * Check user access token is valid or not
     *
     * @param currentAccessToken current user access token
     * @return true if valid, false if not
     */
    public boolean isUserAccessTokenValid(String currentAccessToken) throws FacebookException {
        boolean isValid = false;
        String appAccessTokenStr = null;
        try {
            FacebookClient.AccessToken appAccessToken = facebookClient.obtainAppAccessToken(appInfo.getAppID(), appInfo.getAppSecret());
            appAccessTokenStr = appAccessToken.getAccessToken();
        } catch (FacebookOAuthException e) {
            LOG.error("Error obtainAppAccessToken of " + appInfo.getAppID(), e);
        }
        if (StringUtils.isEmpty(appAccessTokenStr)) {
            appAccessTokenStr = appInfo.getAppAccessToken();
            LOG.info("obtainAppAccessToken of {} by available data in storage", appInfo.getAppID());
        }
        FacebookClient fbDebugClient = new DefaultFacebookClient(appAccessTokenStr, appInfo.getAppSecret(), getVersion(appInfo.getApiVersion()));
        FacebookClient.DebugTokenInfo debugTokenInfo = fbDebugClient.debugToken(currentAccessToken);
        // set debug token info
        setTokenInfo(debugTokenInfo);
        if (debugTokenInfo != null) {
            LOG.info("DebugTokenInfo: {}", debugTokenInfo.toString());
            // check status in return DebugTokenInfo
            isValid = debugTokenInfo.isValid();
            // need to check time more
            Date expiresDate = debugTokenInfo.getExpiresAt();
            if (expiresDate != null) {
                long diff = expiresDate.getTime() - System.currentTimeMillis();
                LOG.debug("A-Expire_Date str: {}, time: {}, curent: {}, diff: {}", new Object[]{expiresDate.toString(), expiresDate.getTime(), System.currentTimeMillis(), diff});
                isValid = diff > TIME_REFRESH_TOKEN_BEFORE_EXPIRED;
            } else {
                LOG.warn("FAILED to DEBUG token because of ExpiresDate null: {}", currentAccessToken);
            }
        } else {
            LOG.warn("FAILED to DEBUG token because of restfb: {}", currentAccessToken);
        }
        return isValid;
    }

    /**
     * Check if current token is expire soon?
     *
     * if token expire soon, return true. Otherwise return false
     *
     * @return
     */
    public boolean isTokenExpireSoon() {
        boolean isValid = false;
        if (tokenInfo != null) {
            LOG.debug("DebugTokenInfo: {}", tokenInfo.toString());
            // check status in return DebugTokenInfo
            isValid = tokenInfo.isValid();
            // need to check time more
            Date expiresDate = tokenInfo.getExpiresAt();
            if (expiresDate != null) {
                long diff = expiresDate.getTime() - System.currentTimeMillis();
                LOG.debug("B-Expire_Date str: {}, time: {}, curent: {}, diff: {}", new Object[]{expiresDate.toString(), expiresDate.getTime(), System.currentTimeMillis(), diff});
                isValid = diff > TIME_REFRESH_TOKEN_BEFORE_EXPIRED;
            } else {
                LOG.warn("FAILED to DEBUG token because of ExpiresDate null");
            }
        } else {
            LOG.warn("FAILED to DEBUG token because of restfb");
        }
        // if token still valid, return false. otherwise, return true
        return !isValid;
    }

    /**
     * Request get new user access token
     *
     * @param currentAccessToken current user access token
     * @return new valid user access token
     */
    public String getNewUserAccessToken(String currentAccessToken) throws FacebookException {
        FacebookClient.AccessToken newAccessToken;
        if (StringUtils.isEmpty(currentAccessToken)) {
            // if not input current access token, we use current session of facebookClient
            newAccessToken = facebookClient.obtainExtendedAccessToken(appInfo.getAppID(), appInfo.getAppSecret());
        } else {
            // if input current access token
            newAccessToken = facebookClient.obtainExtendedAccessToken(appInfo.getAppID(), appInfo.getAppSecret(), currentAccessToken);
        }
        LOG.info("New AccessToken: {}", newAccessToken.toString());
        String extendedAccessToken = newAccessToken.getAccessToken();
        return extendedAccessToken;
    }

    private static final String FAKE_REDIRECT_URI = "http://localhost:7017/login";

    public String generateLongLiveToken(String currentAccessToken) {

        int numRetry = 2;
        // Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("192.168.4.13", 3128));
        Proxy proxy = null;
        // step 1: get client code
        String clientCodeUrl;
        try {
            clientCodeUrl = generateGetClientCodeUrl(currentAccessToken, appInfo.getAppID(), appInfo.getAppSecret(), FAKE_REDIRECT_URI);
        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
            return null;
        }
        LOG.info("GET_CLIENT_CODE_URL {}", clientCodeUrl);
        // download and get code
        String clientCode = null;
        for (int i = 0; i < numRetry; i++) {
            HttpResponseInfo response = vn.viettel.social.utils.Utils.singleGet(clientCodeUrl, proxy);
            if (response.getStatus() == 200) {
                String responseBody = response.getBody();
                if (StringUtils.isNotEmpty(responseBody)) {
                    LOG.info("App {} - GET_CLIENT_CODE_RESPONSE {}", appInfo.getAppID(), responseBody);
                    try {
                        JsonParser jsonParser = new JsonParser();
                        JsonElement jsonElement = jsonParser.parse(responseBody);
                        JsonObject jsonObject = jsonElement.getAsJsonObject();
                        JsonElement codeElement = jsonObject.get("code");
                        if (codeElement != null) {
                            clientCode = codeElement.getAsString();
                            if (StringUtils.isNotEmpty(clientCode)) {
                                break;
                            }
                        }
                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                    }
                }
            }
        }

        if (StringUtils.isEmpty(clientCode)) {
            // cannot get client code, return new access token is null
            return null;
        }

        String newLongLiveToken = null;
        // step 2: Redeeming the code for an access token 
        String newLongLiveTokenUrl;
        try {
            newLongLiveTokenUrl = generateGetNewLongLiveTokenUrl(clientCode, appInfo.getAppID(), FAKE_REDIRECT_URI);
        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
            return null;
        }
        LOG.info("GET_NEW_LONG_LIVE_TOKEN_URL {}", newLongLiveTokenUrl);
        for (int i = 0; i < numRetry; i++) {
            HttpResponseInfo response = vn.viettel.social.utils.Utils.singleGet(newLongLiveTokenUrl, proxy);
            if (response.getStatus() == 200) {
                String responseBody = response.getBody();
                if (StringUtils.isNotEmpty(responseBody)) {
                    LOG.info("App {} - GET_NEW_LONG_LIVE_TOKEN_RESPONSE {}", appInfo.getAppID(), responseBody);
                    try {
                        JsonParser jsonParser = new JsonParser();
                        JsonElement jsonElement = jsonParser.parse(responseBody);
                        JsonObject jsonObject = jsonElement.getAsJsonObject();
                        JsonElement tokenElement = jsonObject.get("access_token");
                        if (tokenElement != null) {
                            newLongLiveToken = tokenElement.getAsString();
                            if (StringUtils.isNotEmpty(newLongLiveToken)) {
                                break;
                            }
                        }
                    } catch (Exception e) {
                        LOG.error(e.getMessage(), e);
                    }
                }
            }
        }
        LOG.info("App {} - New Long Lived AccessToken: {}", appInfo.getAppID(), newLongLiveToken);
        return newLongLiveToken;
    }

    private static String generateGetClientCodeUrl(String accessToken, String clientId, String clienSecret, String redirectUri) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        sb.append("https://graph.facebook.com/oauth/client_code?");
        sb.append("access_token=").append(accessToken).append("&");
        sb.append("client_id=").append(clientId).append("&");
        sb.append("client_secret=").append(clienSecret).append("&");
        sb.append("redirect_uri=").append(URLEncoder.encode(redirectUri, SCommon.CHARSET_UTF_8));
        return sb.toString();
    }

    private static String generateGetNewLongLiveTokenUrl(String clientCode, String clientId, String redirectUri) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        sb.append("https://graph.facebook.com/oauth/access_token?");
        sb.append("code=").append(clientCode).append("&");
        sb.append("client_id=").append(clientId).append("&");
        sb.append("redirect_uri=").append(URLEncoder.encode(redirectUri, SCommon.CHARSET_UTF_8));
        return sb.toString();
    }

    /**
     * Request get app access token
     *
     * @return app access token
     */
    public String getAppAccessToken() throws FacebookException {
        FacebookClient.AccessToken appAccessToken = facebookClient.obtainAppAccessToken(appInfo.getAppID(), appInfo.getAppSecret());
        String appAccessTokenStr = appAccessToken.getAccessToken();
        return appAccessTokenStr;
    }

    /**
     * Download profile info from graph using RestFb Lib
     *
     * TODO: write a function get profile info by create own request to Facebook
     * and parse return json
     *
     * @param profileId
     * @param profileType
     * @return
     * @throws FacebookException
     * @throws IOException
     */
    public Pair<viettel.nfw.social.model.facebook.Profile, Integer> getProfileInfo(
            String profileId, ObjectType profileType) throws FacebookException, IOException {
        int countRequest = 0;
        viettel.nfw.social.model.facebook.Profile profileInfo = null;
        if (StringUtils.isNotEmpty(profileId)) {
            // get Profile Info
            switch (profileType) {
                case PAGE:
                    Page page = this.facebookClient.fetchObject(
                            profileId, Page.class,
                            Parameter.with(FIELDS, PAGE_INFO_FIELDS));
                    countRequest++;
                    if (page != null) {
                        profileInfo = FacebookGraphObjectConvert.convertRestFbPage(page);
                    }
                    break;
                case GROUP:
                    Group group = this.facebookClient.fetchObject(
                            profileId, Group.class,
                            Parameter.with(FIELDS, GROUP_INFO_FIELDS));
                    countRequest++;
                    if (group != null) {
                        profileInfo = FacebookGraphObjectConvert.convertRestFbGroup(group);
                        // get group username
                        String groupUsername = getGroupUsername(profileId, appInfo.getUserAccessToken(), null);
                        countRequest++;
                        if (StringUtils.isNotEmpty(groupUsername)) {
                            profileInfo.setUsername(groupUsername);
                        }
                    }
                    break;
            }
        }
        return new Pair<>(profileInfo, countRequest);
    }

    public Pair<viettel.nfw.social.model.facebook.Post, Integer> getSinglePostInfo(
            String postId, ObjectType postType) throws FacebookException, IOException {
        int countRequest = 0;
        viettel.nfw.social.model.facebook.Post postInfo = new viettel.nfw.social.model.facebook.Post();
        if (StringUtils.isNotEmpty(postId)) {
            if (postType.equals(ObjectType.POST)) {
                // get post information
                Post restfbPostInfo = this.facebookClient.fetchObject(
                        postId, Post.class,
                        Parameter.with(FIELDS, POST_INFO_FIELDS));
                countRequest++;

                if (restfbPostInfo != null) {
                    String maybeWallProfileId = "";
                    String[] partIds = StringUtils.split(postId, "_");
                    if (partIds.length == 2) {
                        maybeWallProfileId = partIds[0];
                    }
                    postInfo = FacebookGraphObjectConvert.convertRestFbPost(restfbPostInfo, maybeWallProfileId);

                    // get comments and likes count
                    Post summaryPost = this.facebookClient.fetchObject(postId,
                            Post.class,
                            Parameter.with(FIELDS, POST_SUMMARY_FIELDS));
                    countRequest++;

                    if (summaryPost != null) {
                        long likesCount = 0;
                        if (summaryPost.getLikesCount() != null) {
                            likesCount = summaryPost.getLikesCount();
                        }
                        long commentsCount = 0;
                        if (summaryPost.getCommentsCount() != null) {
                            commentsCount = summaryPost.getCommentsCount();
                        }
                        postInfo.setLikesCount(likesCount);
                        postInfo.setCommentsCount(commentsCount);
                    }

                } else {
                    // this type is not supported yet!
                    return new Pair<>(null, countRequest);
                }
            }
        }
        return new Pair<>(postInfo, countRequest);
    }

    public static Pair<viettel.nfw.social.model.facebook.Post, Integer> getSinglePostInfo(String postId, ObjectType postType, String accessToken, Proxy proxy) {
        int countRequest = 0;
        viettel.nfw.social.model.facebook.Post postInfo = null;
        if (StringUtils.isNotEmpty(postId)) {
            if (postType.equals(ObjectType.POST)) {
                // build url
                String postInfoUrl = FacebookGraphUrlBuilder.buildPostInfoUrl(null, postId, accessToken);

                // get post infomation
                for (int i = 0; i < NUMBER_RETRY; i++) {
                    try {
                        HttpResponseInfo response = vn.viettel.social.utils.Utils.singleGet(postInfoUrl, proxy);
                        countRequest++;
                        if (response.getStatus() == 200) {
                            String responseBody = response.getBody();
                            if (StringUtils.isNotEmpty(responseBody)) {
                                String maybeWallProfileId = "";
                                String[] partIds = StringUtils.split(postId, "_");
                                if (partIds.length == 2) {
                                    maybeWallProfileId = partIds[0];
                                }
                                postInfo = FacebookGraphObjectConvert.jsonParserFbPostInfo(responseBody, maybeWallProfileId);
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        LOG.error("Error while getting information of post " + postId, ex);
                    }
                    Funcs.sleep(SLEEP_TIME_BETWEEN_RETRY);
                }

                if (postInfo != null) {
                    // get likes/comment counter
                    String postCounterUrl = FacebookGraphUrlBuilder.buildCountLikesCommentOfPostUrl(null, postId, accessToken);
                    for (int i = 0; i < NUMBER_RETRY; i++) {
                        try {
                            HttpResponseInfo response = vn.viettel.social.utils.Utils.singleGet(postCounterUrl, proxy);
                            countRequest++;
                            if (response.getStatus() == 200) {
                                String responseBody = response.getBody();
                                if (StringUtils.isNotEmpty(responseBody)) {
                                    Pair<Long, Long> counter = FacebookGraphObjectConvert.jsonParserFbPostLikesCommentsCounter(responseBody);
                                    if (counter != null) {
                                        long likesCount = counter.first;
                                        long commentsCount = counter.second;
                                        if (likesCount >= postInfo.getLikesCount()) {
                                            postInfo.setLikesCount(likesCount);
                                        }
                                        if (commentsCount >= postInfo.getCommentsCount()) {
                                            postInfo.setCommentsCount(commentsCount);
                                        }
                                        break;
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            LOG.error("Error while getting likes/comment counter of post " + postId, ex);
                        }
                        Funcs.sleep(SLEEP_TIME_BETWEEN_RETRY);
                    }
                }
            }
        }
        return new Pair<>(postInfo, countRequest);
    }

    /**
     * Get list post ID of a profile
     *
     * @param profileId
     * @param visitedPosts
     * @return A pair contains list postIds and number of requests
     * @throws FacebookException
     * @throws java.io.IOException
     */
    public Pair<List<String>, Integer> getListPostsOfProfile( 
            String profileId, List<ProfilePostsRepository.ProfilePost> visitedPosts) throws FacebookException, IOException {
        int countRequest = 0;
        List<String> postIds = new ArrayList<>();

        // get all feeds
        List<String> connectionUrls = new ArrayList<>();
        connectionUrls.add(String.format(FORMAT_PAGE_FEED, profileId));
        boolean isFirstTime = true;
        int countNextPage = 0;

        while (connectionUrls.size() > 0) {

            Funcs.sleep(RANDOMIZE.nextInt(800));
            String connectionUrl = connectionUrls.remove(0);
            Connection<Post> pageFeeds = null;
            if (isFirstTime) {
                pageFeeds = this.facebookClient.fetchConnection(connectionUrl, Post.class);
                countRequest++;
                isFirstTime = false;
            } else {
                pageFeeds = this.facebookClient.fetchConnectionPage(connectionUrl, Post.class);
                countRequest++;
            }

            if (pageFeeds == null) {
                continue;
            }

            List<Post> posts = pageFeeds.getData();
            for (Post post : posts) {
                if (post != null) {
                    String postId = post.getId();
                    if (StringUtils.isNotEmpty(postId)) {
                        postIds.add(postId);
                    }
                }
            }

            // decide to get NextPage
            boolean isGetNextPage = true;
            if (visitedPosts.size() > 0) {
                Set<String> visitedPostIds = new HashSet<>();
                for (ProfilePostsRepository.ProfilePost visitedPost : visitedPosts) {
                    visitedPostIds.add(visitedPost.postId);
                }
                for (String postId : postIds) {
                    if (visitedPostIds.contains(postId)) {
                        isGetNextPage = false;
                        break;
                    }
                }
            }

            if (isGetNextPage) {
                String nextPage = pageFeeds.getNextPageUrl();
                if (StringUtils.isNotEmpty(nextPage)
                        && countNextPage < MAX_NEXT_PAGE) {
                    connectionUrls.add(nextPage);
                    countNextPage++;
                }
            }
        }

        return new Pair<>(postIds, countRequest);
    }

    /**
     * Get list post ID of a profile
     *
     * @param profileId
     * @param crawledPosts
     * @return A pair contains list postIds and number of requests
     * @throws FacebookException
     * @throws java.io.IOException
     */
    public Pair<List<String>, Integer> getNewPosts(String profileId, List<String> crawledPosts) throws FacebookException, IOException {
        int countRequest = 0;
        List<String> newPostIds = new ArrayList<>();

        // get all feeds
        List<String> connectionUrls = new ArrayList<>();
        connectionUrls.add(String.format(FORMAT_PAGE_FEED, profileId));
        boolean isFirstTime = true;
        int countNextPage = 0;

        while (connectionUrls.size() > 0) {

            Funcs.sleep(RANDOMIZE.nextInt(800));
            String connectionUrl = connectionUrls.remove(0);
            Connection<Post> pageFeeds = null;
            if (isFirstTime) {
                pageFeeds = this.facebookClient.fetchConnection(connectionUrl, Post.class);
                countRequest++;
                isFirstTime = false;
            } else {
                pageFeeds = this.facebookClient.fetchConnectionPage(connectionUrl, Post.class);
                countRequest++;
            }

            if (pageFeeds == null) {
                continue;
            }

            int totalPosts = 0;
            int countNewPosts = 0;
            List<Post> posts = pageFeeds.getData();
            for (Post post : posts) {
                if (post != null) {
                    String postId = post.getId();
                    if (StringUtils.isNotEmpty(postId)) {
                        totalPosts++;
                        if (crawledPosts != null) {
                            if (!crawledPosts.contains(postId)) {
                                newPostIds.add(postId);
                                countNewPosts++;
                            }
                        }
                    }
                }
            }

            // decide to get NextPage
            boolean isGetNextPage = false;
            if (totalPosts != 0) {
                double percent = (double) (countNewPosts / totalPosts);
                if (percent >= PERCENT_NEW_POSTS) {
                    isGetNextPage = true;
                }
            }

            if (isGetNextPage) {
                String nextPage = pageFeeds.getNextPageUrl();
                if (StringUtils.isNotEmpty(nextPage)
                        && countNextPage < MAX_NEXT_PAGE) {
                    connectionUrls.add(nextPage);
                    countNextPage++;
                }
            }
        }

        return new Pair<>(newPostIds, countRequest);
    }

    public static Pair<Pair<Set<String>, Set<String>>, Integer> getPosts(String profileId, List<String> crawledPosts, String accessToken, Proxy proxy) {
        Set<String> newPostIds = new HashSet<>();
        Set<String> oldPostIds = new HashSet<>();
        Set<String> dates = new HashSet<>();

        int countRequests = 0;
        int countNextPages = 0;

        // get all feeds
        List<String> requestUrls = new ArrayList<>();
        String feedUrls = FacebookGraphUrlBuilder.buildListFeedUrl(null, profileId, accessToken);
        if (StringUtils.isNotEmpty(feedUrls)) {
            requestUrls.add(feedUrls);
        }

        TreeMap<String, Long> oldPostId2TimeTreemap = new TreeMap<>();
        while (requestUrls.size() > 0) {
            // get feed urls
            feedUrls = requestUrls.remove(0);

            Map<String, Long> postId2Time = null;
            String nextUrl = null;
            // download url and get result
            for (int i = 0; i < NUMBER_RETRY; i++) {
                try {
                    HttpResponseInfo response = vn.viettel.social.utils.Utils.singleGet(feedUrls, proxy);
                    countRequests++;
                    if (response.getStatus() == 200) {
                        String responseBody = response.getBody();
                        if (StringUtils.isNotEmpty(responseBody)) {
                            Pair<Map<String, Long>, String> result = FacebookGraphObjectConvert.jsonParserFeed(responseBody);
                            postId2Time = result.first;
                            nextUrl = result.second;
                            break;
                        }
                    }
                } catch (Exception ex) {
                    LOG.error("Error while getting feed of profile " + profileId, ex);
                }
                Funcs.sleep(SLEEP_TIME_BETWEEN_RETRY);
            }

            if (postId2Time != null) {
                for (Map.Entry<String, Long> entry : postId2Time.entrySet()) {
                    String postId = entry.getKey();
                    Long createdTime = entry.getValue();
                    if (crawledPosts != null) {
                        if (crawledPosts.contains(postId)) {
                            // add to tree map
                            oldPostId2TimeTreemap.put(postId, createdTime);
                        } else {
                            // add to news
                            newPostIds.add(postId);
                        }
                    } else {
                        newPostIds.add(postId);
                    }
                    if (createdTime != null) {
                        try {
                            String dateInString = SDF_YMD.format(new Date(createdTime));
                            if (StringUtils.isNotEmpty(dateInString) && dateInString.matches("^[0-9]+$")) {
                                dates.add(dateInString);
                            }
                        } catch (Exception e) {
                            LOG.error("Something wrong here!", e);
                        }
                    }
                }
            }

            // decide to add NextPage
            if (StringUtils.isNotEmpty(nextUrl)) {
                if (countNextPages < MAX_NEXT_PAGE && dates.size() < MAX_DAYS_FOR_QUERY_FEED) { // postID has date is less than 10 days
                    requestUrls.add(nextUrl);
                    countNextPages++;
                }
            }
        }

        // Calling the method sortByvalues
        try {
            Map sortedMap = sortByValues(oldPostId2TimeTreemap);
            if (sortedMap != null) {
                List<String> allOldPostIds = new ArrayList<>();
                allOldPostIds.addAll(sortedMap.keySet());
                int j = 0;
                for (int i = allOldPostIds.size() - 1; i >= 0; i--) {
                    String postId = allOldPostIds.get(i);
                    if (j < ALLOW_MAX_OLD_POSTS) {
                        oldPostIds.add(postId);
                        j++;
                    } else {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error while sort old post ids", e);
        }

        return new Pair<>(new Pair<>(newPostIds, oldPostIds), countRequests);
    }

    public static <K, V extends Comparable<V>> Map<K, V> sortByValues(final Map<K, V> map) {
        Comparator<K> valueComparator = new Comparator<K>() {
            public int compare(K k1, K k2) {
                int compare
                        = map.get(k1).compareTo(map.get(k2));
                if (compare == 0) {
                    return 1;
                } else {
                    return compare;
                }
            }
        };

        Map<K, V> sortedByValues = new TreeMap<>(valueComparator);
        sortedByValues.putAll(map);
        return sortedByValues;
    }

//	public static void main(String[] args) {
//		TreeMap<String, Long> treemap = new TreeMap<>();
//
//		// Put elements to the map
//		treemap.put("Key1", 1L);
//		treemap.put("Key2", 2231L);
//		treemap.put("Key3", 3L);
//		treemap.put("Key4", 100L);
//		treemap.put("Key5", 6L);
//
//		// Calling the method sortByvalues
//		Map sortedMap = sortByValues(treemap);
//
//		List<String> keys = new ArrayList<>();
//		keys.addAll(sortedMap.keySet());
//
//		for (int i = keys.size() - 1; i >= 0; i--) {
//			String key = keys.get(i);
//			System.out.print(key + ": ");
//			Long value = (Long) sortedMap.get(key);
//			System.out.println(sortedMap.get(key));
//		}
//	}
    public Pair<List<viettel.nfw.social.model.facebook.Comment>, Integer> getCommentsOfPost(String postId) throws FacebookException, IOException {
        int countRequest = 0;
        List<viettel.nfw.social.model.facebook.Comment> comments = new ArrayList<>();

        List<String> nextCommentUrls = new ArrayList<>();
        nextCommentUrls.add(String.format(FORMAT_POST_COMMENTS, postId));
        boolean isFirstCommentUrl = true;
        while (nextCommentUrls.size() > 0) {
            Funcs.sleep(RANDOMIZE.nextInt(900));
            String nextCommentUrl = nextCommentUrls.remove(0);
            Connection<Comment> commentFeeds;
            if (isFirstCommentUrl) {
                commentFeeds = this.facebookClient.fetchConnection(
                        nextCommentUrl, Comment.class,
                        Parameter.with(FIELDS, COMMENT_FIELDS),
                        Parameter.with(LIMIT, COMMENT_LIMIT));
                countRequest++;
                isFirstCommentUrl = false;
            } else {
                commentFeeds = this.facebookClient.fetchConnectionPage(nextCommentUrl, Comment.class);
                countRequest++;
            }
            if (commentFeeds != null) {
                List<Comment> restfbComments = commentFeeds.getData();
                for (Comment comment : restfbComments) {
                    viettel.nfw.social.model.facebook.Comment convertedComment
                            = FacebookGraphObjectConvert.convertRestFbComment(comment, postId);
                    if (convertedComment != null) {
                        comments.add(convertedComment);
                    }
                }

                // search for next page url
                String nextPageCommentUrl = commentFeeds.getNextPageUrl();
                if (StringUtils.isNotEmpty(nextPageCommentUrl)) {
                    nextCommentUrls.add(nextPageCommentUrl);
                }
            }
        }

        return new Pair<>(comments, countRequest);
    }

    public Pair<Map<String, String>, Integer> getLikesOfPost(String postId, long likesCount) {
        long limit;
        int maxDelay;
        if (likesCount < 500) {
            limit = 25; // default
            maxDelay = 600; // default
        } else if (likesCount >= 500
                && likesCount < 1000) {
            limit = 100; // 100
            maxDelay = 800;
        } else {
            limit = 200; // 200
            maxDelay = 1200;
        }

        return crawlLikesOfPost(postId, appInfo.getUserAccessToken(), limit, maxDelay, null);
    }
    public Pair<Map<Map<String, String>,String>, Integer> getReactionsOfPost(String postId, long likesCount) {
        long limit;
        int maxDelay;
        if (likesCount < 500) {
            limit = 25; // default
            maxDelay = 600; // default
        } else if (likesCount >= 500
                && likesCount < 1000) {
            limit = 100; // 100
            maxDelay = 800;
        } else {
            limit = 200; // 200
            maxDelay = 1200;
        }

        return crawlReactionsOfPost(postId, appInfo.getUserAccessToken(), limit, maxDelay, null);
    }

    /**
     * Get group username by manual request
     *
     * @param groupId
     * @param accessToken
     * @param proxy
     * @return
     */
    private static String getGroupUsername(String groupId, String accessToken, Proxy proxy) {
        String groupUsername = "";
        // build URL first
        String groupInfoUrl = FacebookGraphUrlBuilder.buildGroupInfoUrl(null, groupId, accessToken);
        try {
            HttpResponseInfo response = vn.viettel.social.utils.Utils.singleGet(groupInfoUrl, proxy);
            if (response.getStatus() == 200) {
                String responseBody = response.getBody();
                if (StringUtils.isNotEmpty(responseBody)) {
                    Map<String, String> key2values = FacebookGraphObjectConvert.jsonParserFbGroupInfo(responseBody);
                    groupUsername = key2values.get("email");
                    if (StringUtils.isNotEmpty(groupUsername)) {
                        groupUsername = StringUtils.replace(groupUsername, "@groups.facebook.com", "");
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("Error while finding username of group " + groupId, ex);
        }

        return groupUsername;
    }

    private static Pair<Map<String, String>, Integer> crawlLikesOfPost(String postId, String accessToken, long limit, int maxDelay, Proxy proxy) {

        int countRequests = 0;
        Map<String, String> profileId2Names = new HashMap<>();

        List<String> nextLikeUrls = new ArrayList<>();
        String listLikesOfPostUrl = FacebookGraphUrlBuilder.buildListLikesOfPostUrl(null, postId, String.valueOf(limit), accessToken);
        nextLikeUrls.add(listLikesOfPostUrl);

        int countNextPage = 0;
        while (nextLikeUrls.size() > 0) {
            String nextLikeUrl = nextLikeUrls.remove(0);
            Funcs.sleep(RANDOMIZE.nextInt(maxDelay));
            HttpResponseInfo response = vn.viettel.social.utils.Utils.singleGet(nextLikeUrl, proxy);
            countRequests++;
            if (response == null) {
                continue;
            }
            if (response.getStatus() == 200) {
                String responseBody = response.getBody();
                if (StringUtils.isNotEmpty(responseBody)) {

                    Pair<Map<String, String>, String> retData = FacebookGraphObjectConvert.jsonParserListLikesOfPost(responseBody);
                    if (retData.first != null) {
                        profileId2Names.putAll(retData.first);
                    }

                    if (countNextPage > MAX_NEXT_PAGE) {
                        break;
                    }

                    String nextPageUrl = retData.second;
                    if (StringUtils.isNotEmpty(nextPageUrl)
                            && StringUtils.startsWith(nextPageUrl, "https://graph.facebook.com")) {
                        nextLikeUrls.add(nextPageUrl);
                        countNextPage++;
                    }

                }
            }
        }

        return new Pair<>(profileId2Names, countRequests);
    }
    private static Pair<Map<Map<String, String>,String>, Integer> crawlReactionsOfPost(String postId, String accessToken, long limit, int maxDelay, Proxy proxy) {

        int countRequests = 0;
        Map<Map<String, String>,String> profileId2Names = new HashMap<>();

        List<String> nextReactionsUrls = new ArrayList<>();
        String listReactionsofListUrl = FacebookGraphUrlBuilder.buildListReactionsOfPostUrl(null, postId, String.valueOf(limit), accessToken);
        nextReactionsUrls.add(listReactionsofListUrl);

        int countNextPage = 0;
        while (nextReactionsUrls.size() > 0) {
            String nextReactionsUrl = nextReactionsUrls.remove(0);
            Funcs.sleep(RANDOMIZE.nextInt(maxDelay));
            HttpResponseInfo response = vn.viettel.social.utils.Utils.singleGet(nextReactionsUrl, proxy);
            countRequests++;
            if (response == null) {
                continue;
            }
            if (response.getStatus() == 200) {
                String responseBody = response.getBody();
                if (StringUtils.isNotEmpty(responseBody)) {

                    Pair<Map<Map<String, String>,String>, String> retData = FacebookGraphObjectConvert.jsonParserListReactionsOfPost(responseBody);
                    if (retData.first != null) {
                        profileId2Names.putAll(retData.first);
                    }

                    if (countNextPage > MAX_NEXT_PAGE) {
                        break;
                    }

                    String nextPageUrl = retData.second;
                    if (StringUtils.isNotEmpty(nextPageUrl)
                            && StringUtils.startsWith(nextPageUrl, "https://graph.facebook.com")) {
                        nextReactionsUrls.add(nextPageUrl);
                        countNextPage++;
                    }

                }
            }
        }

        return new Pair<>(profileId2Names, countRequests);
    }
}
