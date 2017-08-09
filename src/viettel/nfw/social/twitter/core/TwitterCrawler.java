package viettel.nfw.social.twitter.core;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.CookieManager;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import viettel.nfw.social.google.core.HttpRequest;
import viettel.nfw.social.google.entity.CrawledResult;

import viettel.nfw.social.model.twitter.Profile;
import viettel.nfw.social.model.twitter.Reply;
import viettel.nfw.social.model.twitter.Tweet;
import viettel.nfw.social.model.twitter.TwitterObject;
import viettel.nfw.social.twitter.utils.DateSerizlier;
import viettel.nfw.social.twitter.utils.TwitterError;
import viettel.nfw.social.twitter.utils.TwitterMessage;
import viettel.nfw.social.twitter.utils.TwitterURL;
import viettel.nfw.social.utils.HttpResponseInfo;
import vn.viettel.social.utils.consts.Header;
import vn.viettel.social.utils.consts.Html;
import vn.viettel.social.utils.consts.SCommon;

/**
 * Twitter Crawler
 *
 * @author duongth5
 * @version 1.0
 * @since 1.0
 */
public class TwitterCrawler {

    private static final Logger LOG = LoggerFactory.getLogger(TwitterCrawler.class);
    private static final Type listTypeTweet = new TypeToken<ArrayList<JSONTweet>>() {
    }.getType();
    private static final Type listTypeReply = new TypeToken<ArrayList<JSONReply>>() {
    }.getType();
    private static final Type listTypeString = new TypeToken<ArrayList<String>>() {
    }.getType();
    private static final Type listTypeProfile = new TypeToken<ArrayList<JSONProfile>>() {
    }.getType();

    private final Set<String> foundProfileUrls;
    private final HttpRequest http;
    private final String loginAccTwitterUsername;
    private final String loginAccTwitterPassword;
    private final Proxy proxy;
    private boolean isLogin;
    private String authenticityToken;
    private CrawledResult crawledResult;
    private final Random randomize;
    private final CookieManager cookieManager;
    private TwitterError twError;

    public TwitterCrawler(String username, String password, Proxy proxy, CookieManager cookieManager) {
        this.http = new HttpRequest(cookieManager);
        this.foundProfileUrls = new HashSet<>();
        this.randomize = new Random();
        this.loginAccTwitterUsername = username;
        this.loginAccTwitterPassword = password;
        this.proxy = proxy;
        this.isLogin = false;
        this.cookieManager = cookieManager;
        this.twError = null;
    }

    public void addFoundProfileUrl(String screenName) {
        String urlProfile = String.format(TwitterURL.FORMAT_PROFILE, screenName);
        foundProfileUrls.add(urlProfile);
    }

    public TwitterError login() {

        try {
            cookieManager.put(new URI("https://mobile.twitter.com/"), new HashMap<String, List<String>>());
            cookieManager.put(new URI("http://mobile.twitter.com/"), new HashMap<String, List<String>>());

            isLogin = false;
            long startTime = System.currentTimeMillis();

            // Login to Twitter and keep sessions
            HttpResponseInfo responseGet = http.get(TwitterURL.SESSION_NEW_URL, HttpRequest.SOCIAL_TYPE_TWITTER, proxy);
            // get m5_csrf_tkn
            String authenToken = getAuthenticityToken(responseGet.getBody());
            if (StringUtils.isEmpty(authenToken)) {
                LOG.warn(TwitterMessage.LOGIN_CANNOT_GET_AUTHEN_TOKEN);
                twError = TwitterError.LOGIN_CANNOT_GET_AUTHEN_TOKEN;
            } else {
                setAuthenticityToken(authenToken);
                String postParams = getTwitterFormParams(responseGet.getBody(), loginAccTwitterUsername, loginAccTwitterPassword);
                // Construct above post's content and then send a POST request for authentication
                HttpResponseInfo responsePost = http.post(TwitterURL.SESSION_URL, postParams,
                        HttpRequest.SOCIAL_TYPE_TWITTER, Header.Value.ACCEPT_DEFAULT, TwitterURL.SESSION_NEW_URL, proxy);
                if (responsePost.getStatus() == 302) {
                    String redirectUrl = responsePost.getHeaders().get("Location".toLowerCase()).get(0);
                    LOG.info(TwitterMessage.LOGIN_REDIRECT_URL, redirectUrl);
                    if (redirectUrl.equals(TwitterURL.BASE_URL)) {
                        isLogin = true;
                        LOG.info(TwitterMessage.LOGIN_SUCCESS);
                        twError = TwitterError.LOGIN_OK;
                    } else {
                        LOG.warn(TwitterMessage.LOGIN_FAILED);
                        twError = TwitterError.LOGIN_FAILED;
                    }
                } else {
                    LOG.warn(TwitterMessage.LOGIN_POST_AUTHEN_NOT_RETURN_302);
                    twError = TwitterError.LOGIN_POST_AUTHEN_NOT_RETURN_302;
                }
            }
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            LOG.info(TwitterMessage.PROCESS_LOGIN_TOTAL_TIME, totalTime);
        } catch (IOException | URISyntaxException ex) {
            LOG.error(ex.getMessage(), ex);
            twError = TwitterError.LOGIN_FAILED;
        }

        return twError;
    }

    /**
     * Get Twitter Login Form
     *
     * @param html Raw HTML
     * @param username Twitter account username
     * @param password Twitter account password
     * @return String parameters
     */
    public static String getTwitterFormParams(String html, String username, String password) {

        String ret = "";
        LOG.info("Extracting form's data ...");
        Document doc = Jsoup.parse(html);
        // Twitter form id
        Element loginform = doc.select("form[class=signin-form]").get(0);
        if (loginform == null) {
            return ret;
        }
        Elements inputElements = loginform.getElementsByTag(Html.Tag.INPUT);
        List<String> paramList = new ArrayList<>();
        for (Element inputElement : inputElements) {
            String key = inputElement.attr(Html.Attribute.NAME);
            String value = inputElement.attr(Html.Attribute.VALUE);
            switch (key) {
                case "session[username_or_email]":
                    value = username;
                    break;
                case "session[password]":
                    value = password;
                    break;
            }
            try {
                paramList.add(URLEncoder.encode(key, SCommon.CHARSET_UTF_8) + "=" + URLEncoder.encode(value, SCommon.CHARSET_UTF_8));
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

    public void crawl(String profileUrl) {

        if (!isLogin) {
            LOG.warn(twError.toString());
            returnCrawledResult(twError, profileUrl);
        } else {
            CrawledResult result = new CrawledResult();
            TwitterObject twObj = new TwitterObject();

            long startTime = System.currentTimeMillis();
            try {
                Gson gson = new GsonBuilder().registerTypeAdapter(Date.class, new DateSerizlier()).create();
                String screenName = getScreenName(profileUrl);

                List<Profile> listProfiles = new ArrayList<>();
                List<Tweet> listTweets = new ArrayList<>();
                List<Reply> listReplies = new ArrayList<>();

                // parse profile
                Profile profile = parseProfileInfo(screenName, profileUrl, gson);
                listProfiles.add(profile);

                // decide to crawl profile if profile is protected
                if (profile.isProfileProtected()) {
                    // cannot get tweets
                } else {
                    TimelineWrapper retTimelineWrapper = parseProfileTweets(screenName, profileUrl, gson);

                    listProfiles.addAll(retTimelineWrapper.profiles);
                    listTweets.addAll(retTimelineWrapper.tweets);
                    listReplies.addAll(retTimelineWrapper.replies);
                }

                List<Profile> followingProfiles = parseProfileFollowings(screenName, profileUrl, gson);
                listProfiles.addAll(followingProfiles);

                twObj.setProfiles(listProfiles);
                twObj.setReplies(listReplies);
                twObj.setTweets(listTweets);

            } catch (IOException | InterruptedException | JsonParseException ex) {
                LOG.error("Error crawling URL: {}", profileUrl);
                LOG.error(ex.getMessage(), ex);
            } catch (Exception ex) {
                LOG.error("Error crawling URL: {}", profileUrl);
                LOG.error(ex.getMessage(), ex);
            } finally {

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                LOG.info(TwitterMessage.PROCESS_PARSER_TOTAL_TIME, totalTime);

                result.setErrorCode(TwitterError.CRAWL_PROFILE_OK.getCode()); // success
                result.setErrorDescription(TwitterError.CRAWL_PROFILE_OK.getDescription());
                result.setFoundProfileUrls(foundProfileUrls);
                result.setStartTime(startTime);
                result.setEndTime(endTime);
                result.setCrawledTime(totalTime);
                result.setAccountCrawl(loginAccTwitterUsername);
                result.setCrawledProfile(twObj);
                setCrawledResult(result);
            }
        }
    }

    /**
     * Return crawler result
     *
     * @param error error type
     * @param profileUrl profile URL
     */
    private void returnCrawledResult(TwitterError error, String profileUrl) {
        CrawledResult result = new CrawledResult();
        result.setErrorCode(error.getCode());
        result.setErrorDescription(error.getDescription() + " - " + profileUrl);
        result.setAccountCrawl(loginAccTwitterUsername);
        result.setCrawledProfile(null);
        setCrawledResult(result);
    }

    private Profile parseProfileInfo(String screenName, String profileUrl, Gson gson) throws InterruptedException, IOException, JsonParseException {

        // get profile info
        Map<String, String> profileParamMap = new HashMap<>();
        profileParamMap.put("user_id", screenName);
        profileParamMap.put("m5_csrf_tkn", getAuthenticityToken());
        String profileParam = buildFormParams(profileParamMap);

        Thread.sleep(randomize.nextInt(200 * 6));
        HttpResponseInfo responseProfile = http.post(TwitterURL.API_PROFILE, profileParam,
                HttpRequest.SOCIAL_TYPE_TWITTER, Header.Value.CONTENT_TYPE_JSON, profileUrl, proxy);
        LOG.info("status: {}", responseProfile.getStatus());
        LOG.debug("body: {}", responseProfile.getBody());

        JsonObject jsonObject;
        JsonParser parser = new JsonParser();
        JsonElement jsonElement = parser.parse(responseProfile.getBody().trim());
        jsonObject = jsonElement.getAsJsonObject();
        JsonElement eProfile = jsonObject.get("profile");
        JSONProfile jsonProfile = gson.fromJson(eProfile, JSONProfile.class);
        LOG.debug("Profile info: {}", jsonProfile.toString());

        Profile retProfile = convertJSONProfile(jsonProfile);
        return retProfile;
    }

    private TimelineWrapper parseProfileTweets(String screenName, String profileUrl, Gson gson) throws InterruptedException, IOException, JsonParseException {
        Map<String, String> userTimelineParamMap = new HashMap<>();
        userTimelineParamMap.put("uid", screenName);
        userTimelineParamMap.put("m5_csrf_tkn", getAuthenticityToken());
        userTimelineParamMap.put("include_rt", "true");
        userTimelineParamMap.put("pc", "true");
        String userTimelineParam = buildFormParams(userTimelineParamMap);

        Thread.sleep(randomize.nextInt(200 * 6));
        HttpResponseInfo responseTimeline = http.post(TwitterURL.API_USER_TIMELINE, userTimelineParam, HttpRequest.SOCIAL_TYPE_TWITTER,
                Header.Value.CONTENT_TYPE_JSON, String.format(TwitterURL.FORMAT_TWEETS, profileUrl), proxy);
        LOG.debug("status: " + responseTimeline.getStatus());
        LOG.debug("body: " + responseTimeline.getBody());

        List<Tweet> finalTweets = new ArrayList<>();
        List<Reply> finalReplies = new ArrayList<>();
        List<Profile> finalProfiles = new ArrayList<>();

        List<JSONTweet> tweets = gson.fromJson(responseTimeline.getBody().trim(), listTypeTweet);
        int ic = 0;
        for (JSONTweet tweet : tweets) {
            LOG.debug(tweet.toString());
            // push to big data
            Tweet iTweet = convertJSONTweet(tweet);
            finalTweets.add(iTweet);

            // get status details
            Map<String, String> statusActivityParamMap = new HashMap<>();
            statusActivityParamMap.put("m5_csrf_tkn", getAuthenticityToken());
            statusActivityParamMap.put("replyTo", tweet.getId());
            String statusActivityParam = buildFormParams(statusActivityParamMap);

            Thread.sleep(randomize.nextInt(200 * 6));
            HttpResponseInfo responseStatusActivty = http.post(TwitterURL.API_STATUS_ACTIVITY, statusActivityParam, HttpRequest.SOCIAL_TYPE_TWITTER,
                    Header.Value.CONTENT_TYPE_JSON, String.format(TwitterURL.FORMAT_STATUS_ACTIVITY, profileUrl, tweet.getId()), proxy);
            LOG.debug("status: " + responseStatusActivty.getStatus());
            LOG.debug("body: " + responseStatusActivty.getBody());

            JsonObject tweetJsonObject;
            JsonParser tweetJsonParser = new JsonParser();
            JsonElement tweetJsonElement = tweetJsonParser.parse(responseStatusActivty.getBody().trim());
            tweetJsonObject = tweetJsonElement.getAsJsonObject();
            // get replies
            JsonElement repliesElement = tweetJsonObject.get("replies");

            List<JSONReply> replies = gson.fromJson(repliesElement, listTypeReply);
            if (replies != null && replies.size() > 0) {
                for (JSONReply reply : replies) {
                    LOG.debug(reply.toString());
                    // push to big data
                    Reply iReply = convertJSONReply(reply);
                    finalReplies.add(iReply);
                    // add found profile url
                    LOG.info("Screen Name: {}", reply.getUser().getScreenName());
                    addFoundProfileUrl(reply.getUser().getScreenName());
                }
            }

            // get activities
            JsonElement activitiesElement = tweetJsonObject.get("activities");
            JsonObject activitiesObject;
            activitiesObject = activitiesElement.getAsJsonObject();

            // get favoriters
            JsonElement favoritersElement = activitiesObject.get("favoriters");
            List<String> favoriters = gson.fromJson(favoritersElement, listTypeString);
            String listFavoritersId = "";
            if (favoriters != null && favoriters.size() > 0) {
                listFavoritersId = StringUtils.join(favoriters, ',');
            }

            // get retweeters
            JsonElement retweetersElement = activitiesObject.get("retweeters");
            List<String> retweeters = gson.fromJson(retweetersElement, listTypeString);
            String listRetweetersId = "";
            if (retweeters != null && retweeters.size() > 0) {
                listRetweetersId = StringUtils.join(retweeters, ',');
            }

            // get status retweets
            if (StringUtils.isNotEmpty(listRetweetersId.trim())) {
                Map<String, String> statusActivityRetweetsParamMap = new HashMap<>();
                statusActivityRetweetsParamMap.put("m5_csrf_tkn", getAuthenticityToken());
                statusActivityRetweetsParamMap.put("user_id", listRetweetersId);
                String statusActivityRetweetsParam = buildFormParams(statusActivityRetweetsParamMap);

                Thread.sleep(randomize.nextInt(200 * 6));
                HttpResponseInfo responseStatusActivityRetweets = http.post(TwitterURL.API_USERS, statusActivityRetweetsParam,
                        HttpRequest.SOCIAL_TYPE_TWITTER, Header.Value.CONTENT_TYPE_JSON,
                        String.format(TwitterURL.FORMAT_STATUS_ACTIVITY_RETWEETS, profileUrl, tweet.getId()), proxy);
                LOG.debug("status: " + responseStatusActivityRetweets.getStatus());
                LOG.debug("body: " + responseStatusActivityRetweets.getBody());
                List<JSONProfile> retweetersP = gson.fromJson(responseStatusActivityRetweets.getBody().trim(), listTypeProfile);
                for (JSONProfile retweeterP : retweetersP) {
                    LOG.debug(retweeterP.toString());
                    LOG.info("Screen Name: {}", retweeterP.getScreenName());
                    Profile iProfile = convertJSONProfile(retweeterP);
                    finalProfiles.add(iProfile);
                    addFoundProfileUrl(retweeterP.getScreenName());
                }
            }

            // get status favorites
            if (StringUtils.isNotEmpty(listFavoritersId.trim())) {
                Map<String, String> statusActivityFavoritesParamMap = new HashMap<>();
                statusActivityFavoritesParamMap.put("m5_csrf_tkn", getAuthenticityToken());
                statusActivityFavoritesParamMap.put("user_id", listFavoritersId);
                String statusActivityFavoritesParam = buildFormParams(statusActivityFavoritesParamMap);

                Thread.sleep(randomize.nextInt(200 * 6));
                HttpResponseInfo responseStatusActivityFavorites = http.post(TwitterURL.API_USERS, statusActivityFavoritesParam,
                        HttpRequest.SOCIAL_TYPE_TWITTER, Header.Value.CONTENT_TYPE_JSON,
                        String.format(TwitterURL.FORMAT_STATUS_ACTIVITY_FAVORITES, profileUrl, tweet.getId()), proxy);
                LOG.debug("status: " + responseStatusActivityFavorites.getStatus());
                LOG.debug("body: " + responseStatusActivityFavorites.getBody());
                List<JSONProfile> favoritersP = gson.fromJson(responseStatusActivityFavorites.getBody().trim(), listTypeProfile);
                for (JSONProfile favoriterP : favoritersP) {
                    LOG.debug(favoriterP.toString());
                    LOG.info("Screen Name: {}", favoriterP.getScreenName());
                    Profile iProfile = convertJSONProfile(favoriterP);
                    finalProfiles.add(iProfile);
                    addFoundProfileUrl(favoriterP.getScreenName());
                }
            }

            if (ic > 4) {
                break;
            }
            ic++;
        }

        TimelineWrapper retTimelineWrapper = new TimelineWrapper();
        retTimelineWrapper.profiles = finalProfiles;
        retTimelineWrapper.replies = finalReplies;
        retTimelineWrapper.tweets = finalTweets;

        return retTimelineWrapper;
    }

    private class TimelineWrapper {

        public List<Profile> profiles;
        public List<Tweet> tweets;
        public List<Reply> replies;

        public TimelineWrapper() {
        }

    }

    private List<Profile> parseProfileFollowings(String screenName, String profileUrl, Gson gson) throws InterruptedException, IOException, JsonParseException {
        // get followings
        Map<String, String> userFollowingsParamMap = new HashMap<>();
        userFollowingsParamMap.put("id", screenName);
        userFollowingsParamMap.put("m5_csrf_tkn", getAuthenticityToken());
        String userFollowingsParam = buildFormParams(userFollowingsParamMap);

        Thread.sleep(randomize.nextInt(200 * 6));
        HttpResponseInfo responseFollowings = http.post(TwitterURL.API_FOLLOWINGS, userFollowingsParam,
                HttpRequest.SOCIAL_TYPE_TWITTER, Header.Value.CONTENT_TYPE_JSON,
                String.format(TwitterURL.FORMAT_FOLLOWINGS, profileUrl), proxy);
        LOG.debug("status: " + responseFollowings.getStatus());
        LOG.debug("body: " + responseFollowings.getBody());

        JsonObject jsonObjectFollowings;
        JsonParser parserFollowings = new JsonParser();
        JsonElement jsonElementFollowings = parserFollowings.parse(responseFollowings.getBody().trim());
        jsonObjectFollowings = jsonElementFollowings.getAsJsonObject();
        JsonElement eProfileFollowings = jsonObjectFollowings.get("users");

        List<Profile> finalProfiles = new ArrayList<>();
        List<JSONProfile> followingsP = gson.fromJson(eProfileFollowings, listTypeProfile);
        for (JSONProfile followingP : followingsP) {
            LOG.debug(followingP.toString());
            LOG.info("Screen Name: {}", followingP.getScreenName());
            Profile iProfile = convertJSONProfile(followingP);
            finalProfiles.add(iProfile);
            // add found profile url
            addFoundProfileUrl(followingP.getScreenName());
        }
        return finalProfiles;
    }

    public String getScreenName(String profileUrl) {
        int index = profileUrl.lastIndexOf("/");
        String screenName = profileUrl.substring(index + 1);
        return screenName;
    }

    public String buildFormParams(Map<String, String> paramMap) throws UnsupportedEncodingException {

        List<String> paramList = new ArrayList<>();

        for (Map.Entry<String, String> entry : paramMap.entrySet()) {
            String param = entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), SCommon.CHARSET_UTF_8);
            paramList.add(param);
        }

        StringBuilder result = new StringBuilder();
        for (String param : paramList) {
            if (result.length() == 0) {
                result.append(param);
            } else {
                result.append("&").append(param);
            }
        }

        return result.toString();
    }

    public String getAuthenticityToken(String html) {
        String token = "";
        Document doc = Jsoup.parse(html);

        Elements forms = doc.select("form[class=signin-form]");
        if (!forms.isEmpty()) {
            Element loginform = forms.get(0);
            Elements inputElements = loginform.getElementsByTag(Html.Tag.INPUT);
            for (Element inputElement : inputElements) {
                String key = inputElement.attr(Html.Attribute.NAME);
                String value = inputElement.attr(Html.Attribute.VALUE);
                if (key.equals("authenticity_token")) {
                    token = value;
                    LOG.info("authenticity_token = {}", token);
                }
            }
        }
        return token;
    }

    public String getAuthenticityToken() {
        return authenticityToken;
    }

    public void setAuthenticityToken(String authenticityToken) {
        this.authenticityToken = authenticityToken;
    }

    public CrawledResult getCrawledResult() {
        return crawledResult;
    }

    public void setCrawledResult(CrawledResult crawledResult) {
        this.crawledResult = crawledResult;
    }

    private static Profile convertJSONProfile(JSONProfile jsonProfile) {

        Profile profile = new Profile();
        profile.setCrawledTime(new Date());
        profile.setDescription(jsonProfile.getDescription());
        profile.setFavouritesCount(jsonProfile.getFavouritesCount());
        profile.setFollowersCount(jsonProfile.getFollowersCount());
        profile.setFriendsCount(jsonProfile.getFriendsCount());
        profile.setId(jsonProfile.getId());
        profile.setLang(jsonProfile.getLang());
        profile.setListedCount(jsonProfile.getListedCount());
        profile.setLocation(jsonProfile.getLocation());
        profile.setMediaCount(jsonProfile.getMediaCount());
        profile.setName(jsonProfile.getName());
        profile.setProfileLocation(jsonProfile.getProfileLocation());
        profile.setProfileProtected(jsonProfile.isProfileProtected());
        profile.setScreenName(jsonProfile.getScreenName());
        profile.setStatusesCount(jsonProfile.getStatusesCount());
        return profile;
    }

    private static Tweet convertJSONTweet(JSONTweet jsonTweet) {

        Tweet tweet = new Tweet();
        tweet.setConversationId(jsonTweet.getConversationId());
        tweet.setCrawledTime(new Date());
        tweet.setCreatedAt(jsonTweet.getCreatedAt());
        tweet.setFavoriteCount(jsonTweet.getFavoriteCount());
        tweet.setId(jsonTweet.getId());
        tweet.setLang(jsonTweet.getLang());
        tweet.setPossiblySensitive(jsonTweet.isPossiblySensitive());
        tweet.setRetweetCount(jsonTweet.getRetweetCount());
        tweet.setSource(jsonTweet.getSource());
        tweet.setText(jsonTweet.getText());
        tweet.setUser(convertJSONProfile(jsonTweet.getUser()));
        tweet.setUserId(jsonTweet.getUserId());
        return tweet;
    }

    private static Reply convertJSONReply(JSONReply jsonReply) {

        Reply reply = new Reply();
        reply.setCrawledTime(new Date());
        reply.setCreatedAt(jsonReply.getCreatedAt());
        reply.setId(jsonReply.getId());
        reply.setInReplyToScreenName(jsonReply.getInReplyToScreenName());
        reply.setInReplyToStatusId(jsonReply.getInReplyToStatusId());
        reply.setInReplyToUserId(jsonReply.getInReplyToUserId());
        reply.setLang(jsonReply.getLang());
        reply.setSource(jsonReply.getSource());
        reply.setText(jsonReply.getText());
        reply.setUser(convertJSONProfile(jsonReply.getUser()));
        reply.setUserId(jsonReply.getUserId());
        return reply;
    }
}
