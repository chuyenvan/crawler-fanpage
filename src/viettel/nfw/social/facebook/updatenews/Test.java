package viettel.nfw.social.facebook.updatenews;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import viettel.nfw.social.facebook.updatenews.repo.ProfilePostsRepository;
import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.Version;
import com.restfb.types.Group;
import com.restfb.types.Page;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.graph.FacebookGraphActions;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectType;
import viettel.nfw.social.facebook.updatenews.graph.entities.SocialType;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.model.facebook.Like;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.social.utils.HttpResponseInfo;
import vn.viettel.utils.SerializeObjectUtils;
import vn.viettel.utils.SimpleTimer;

/**
 *
 * @author ralph
 */
public class Test {

    private static final Logger LOG = LoggerFactory.getLogger(Test.class);
    private static final int MAX_SIZE = 1000000;
    private static final String FORMAT_POST_LIKES = "https://graph.facebook.com/v2.3/%s/likes?format=json&access_token=%s&limit=%s"; // post_id/accesstoken/limit

    public static void main(String[] args) {
       testGraph();
//        LOG.info("{}", new Date().toString());
//        testDB();
//        testRunUpdate();

//        crawlLikesOfPost("108018461806_10153544861091807", "1449953991987089|4B3T7sCQVF4bMUQDiAhRUHpIaP0", 25);
//        String str1 = "facebook|giaitrionline|page|3h";
////        String[] parts = StringUtils.split(str.trim(), "|");
////        LOG.info("length {}", parts.length);
////        LOG.info(JSON.encode(str));
//        Set<String> data = new HashSet<>();
//        data.add(str1);
//        String str = JSON.encode(data);
//        LOG.info(str);
//        if (StringUtils.contains(str, "\"")) {
//            str = StringUtils.replace(str, "\"", "");
//        }
//        if (StringUtils.contains(str, "[")) {
//            str = StringUtils.replace(str, "[", "");
//        }
//        if (StringUtils.contains(str, "]")) {
//            str = StringUtils.replace(str, "]", "");
//        }
//        LOG.info(str);

//        ObjectRequestRepository repo = ObjectRequestRepository.getInstance();
//        String key = "FACEBOOK_giaitrionline";
//        ObjectRequest value1 = new ObjectRequest();
//        value1.socialType = SocialType.FACEBOOK;
//        value1.objectID = "giaitrionline";
//        value1.objectType = ObjectType.PAGE;
//        value1.loopTimeTimeMillis = 2 * 60 * 60 * 1000;
//        try {
//            repo.write(key.getBytes(), SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(value1));
//        } catch (IOException ex) {
//            LOG.error(ex.getMessage(), ex);
//        }
//        Map<String, ObjectRequest> data1 = repo.getAllData();
//        LOG.info("data1");
//        for (Map.Entry<String, ObjectRequest> entrySet : data1.entrySet()) {
//            String key1 = entrySet.getKey();
//            ObjectRequest value = entrySet.getValue();
//            LOG.info("{} - {}", key1, value);
//        }
//
//        ObjectRequest value2 = new ObjectRequest();
//        value2.socialType = SocialType.FACEBOOK;
//        value2.objectID = "giaitrionline";
//        value2.objectType = ObjectType.PAGE;
//        value2.loopTimeTimeMillis = 5 * 60 * 60 * 1000;
//        try {
//            repo.write(key.getBytes(), SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(value2));
//        } catch (IOException ex) {
//            LOG.error(ex.getMessage(), ex);
//        }
//        Map<String, ObjectRequest> data2 = repo.getAllData();
//        LOG.info("data2");
//        for (Map.Entry<String, ObjectRequest> entrySet : data2.entrySet()) {
//            String key1 = entrySet.getKey();
//            ObjectRequest value = entrySet.getValue();
//            LOG.info("{} - {}", key1, value);
//        }
    }

    private static Like crawlLikesOfPost(String postId, String accessToken, long limit) {

        Random randomize = new Random();
        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("192.168.4.13", 3128));
        List<String> likedProfileIds = new ArrayList<>();
        // build URL
        String url = String.format(FORMAT_POST_LIKES, postId, accessToken, String.valueOf(limit));
        List<String> likeUrls = new ArrayList<>();
        likeUrls.add(url);
        while (likeUrls.size() > 0) {
            String nextUrl = likeUrls.remove(0);
            Funcs.sleep(randomize.nextInt(600));
            HttpResponseInfo response = vn.viettel.social.utils.Utils.singleGet(nextUrl, proxy);
            if (response.getStatus() == 200) {
                String responseBody = response.getBody();
                if (StringUtils.isNotEmpty(responseBody)) {
                    try {
                        Gson gson = new Gson();
                        JsonParser jsonParser = new JsonParser();
                        JsonElement jsonElement = jsonParser.parse(responseBody);
                        JsonObject jsonObject = jsonElement.getAsJsonObject();
                        JsonElement dataElement = jsonObject.get("data");

                        Type listType = new TypeToken<List<Wrapper>>() {
                        }.getType();
                        List<Wrapper> users = (List<Wrapper>) gson.fromJson(dataElement, listType);
                        for (Wrapper user : users) {
                            String userId = user.id;
                            String userName = user.name;
                            likedProfileIds.add(userId);
                        }

                        JsonElement pagingElement = jsonObject.get("paging");
                        JsonObject pagingJsonObject = pagingElement.getAsJsonObject();
                        JsonElement nextElement = pagingJsonObject.get("next");
                        if (nextElement != null) {
                            String nextPageUrl = nextElement.getAsString();
                            if (StringUtils.isNotEmpty(nextPageUrl)
                                    && StringUtils.startsWith(nextPageUrl, "https://graph.facebook.com")) {
                                likeUrls.add(nextPageUrl);
                            }
                        }

                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
            }
        }

        Like like = new Like();
        like.setId(postId);
        like.setLikedObjectType("post");
        like.setLikedProfileIds(likedProfileIds);
        like.setCreateTime(new Date());

        return like;
    }

    private static class Wrapper {

        public String id;
        public String name;
    }

    private static void testGraph() {
        FacebookClient facebookClient = new DefaultFacebookClient(
                "1449953991987089|4B3T7sCQVF4bMUQDiAhRUHpIaP0",
                "ad6376294925f0a80e38eeb7c0283380", Version.VERSION_2_3);

         Page page = facebookClient.fetchObject("giaitrionline", Page.class);
         String pageId = page.getId();
         System.out.println(pageId);
         System.out.println(page.getName());
        SimpleTimer st = new SimpleTimer();
//        Post post = facebookClient.fetchObject("108018461806_10153544861091807",
//                Post.class,
//                Parameter.with("fields", "likes.summary(true),comments.summary(true)"));
//
//        LOG.info("Total time: {} ms", st.getTimeAndReset());
//        LOG.info("Likes count: {}", post.getLikesCount());
//        LOG.info("Likes count (from Likes): {}", post.getLikes().getTotalCount());
//        LOG.info("Comments count: {}", post.getCommentsCount());
//        LOG.info("Comments count (from Comments): {}", post.getComments().getTotalCount());
//        Connection<Comment> connectionComments = facebookClient.fetchConnection("108018461806_10153544861091807/comments", Comment.class, Parameter.with("limit", 100));
//        List<Comment> comments = connectionComments.getData();
//        LOG.info("size {}", comments.size());
//
//        for (Comment comment : comments) {
//            String commentId = comment.getId();
//            String commentType = comment.getType();
//            String commentContent = comment.getMessage();
//            String commentUserId = comment.getFrom().getId();
//            String commentUsername = comment.getFrom().getName();
//            String commentUserType = comment.getFrom().getType();
//            long likeCount = comment.getLikeCount();
//            // String attachment = comment.getAttachment().getUrl();
//            Date createTime = comment.getCreatedTime();
//            LOG.info("aaaa");
//        }
//        LOG.info("next page {}", connectionComments.getNextPageUrl());

//        Connection<Post> pageFeeds = facebookClient.fetchConnection("498390110294358/feed", Post.class);
//        List<Post> posts = pageFeeds.getData();
//        for (Post post : posts) {
//            String postId = post.getId();
//            if (StringUtils.isNotEmpty(postId)) {
//                String postContent = post.getMessage();
//                String fromID = post.getFrom().getId();
//                String toID = post.getTo().get(0).getId();
//
//                Likes postLikes = post.getLikes();
//                List<NamedFacebookType> dataLikes = postLikes.getData();
//                int numLikes = dataLikes.size();
//                StringBuilder sbLikeIDs = new StringBuilder();
//                for (NamedFacebookType dataLike : dataLikes) {
//                    sbLikeIDs.append(dataLike.getId());
//                    sbLikeIDs.append("-");
//                }
//                String numLikesStr = sbLikeIDs.toString();
//
//                Comments postComments = post.getComments();
//                List<Comment> dataComments = postComments.getData();
//                int numComments = dataComments.size();
//                for (Comment dataComment : dataComments) {
//                    dataComment.getId();
//                    dataComment.getFrom().getId();
//                    dataComment.getFrom().getType();
//                    dataComment.getCreatedTime();
//                    dataComment.getMessage();
//                }
//            }
//        }
//
//        List<String> nextPages = new ArrayList<>();
//        String nextpage = pageFeeds.getNextPageUrl();
//        if (StringUtils.isNotEmpty(nextpage)) {
//            nextPages.add(nextpage);
//        }
//
        Group group = facebookClient.fetchObject("498390110294358", Group.class);
        String groupId = group.getId();
        String groupUrl = group.getLink();
        LOG.info(group.getName());
    }

    private static void testDB() {
        ProfilePostsRepository profilePostsRepo = ProfilePostsRepository.getInstance();
        if (profilePostsRepo == null) {
            LOG.info("init failed");
        } else {
            String profileId = "giaitrionline";
            String appId = "1449953991987089";
            ProfilePostsRepository.LastestProfilePostList posts = new ProfilePostsRepository.LastestProfilePostList(MAX_SIZE);
            posts.addPost(new ProfilePostsRepository.ProfilePost("858856007541100", appId, System.currentTimeMillis()));
            posts.addPost(new ProfilePostsRepository.ProfilePost("858828154210552", appId, System.currentTimeMillis()));
            posts.addPost(new ProfilePostsRepository.ProfilePost("858819814211386", appId, System.currentTimeMillis()));
            posts.addPost(new ProfilePostsRepository.ProfilePost("858770620882972", appId, System.currentTimeMillis()));

            try {
                profilePostsRepo.write(profileId.getBytes(), SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(posts));
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            Funcs.sleep(1000);

            // read from db
            byte[] value = profilePostsRepo.get(profileId.getBytes());
            if (value == null) {
                LOG.info("null");
            } else {
                ProfilePostsRepository.LastestProfilePostList giaitrionlinePosts = (ProfilePostsRepository.LastestProfilePostList) SerializeObjectUtils.readingObjectFromByteArrayUsingGzip(value);
                LOG.info("size {}", giaitrionlinePosts.getPosts().size());

                // add more post
                Funcs.sleep(1000);
                giaitrionlinePosts.addPost(new ProfilePostsRepository.ProfilePost("858759080884126", appId, System.currentTimeMillis()));
                giaitrionlinePosts.addPost(new ProfilePostsRepository.ProfilePost("858819814211386", appId, System.currentTimeMillis()));
                try {
                    profilePostsRepo.write(profileId.getBytes(), SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(giaitrionlinePosts));
                } catch (IOException ex) {
                    LOG.error(ex.getMessage(), ex);
                }

                // re-read
                byte[] value2 = profilePostsRepo.get(profileId.getBytes());
                ProfilePostsRepository.LastestProfilePostList giaitrionlinePosts2 = (ProfilePostsRepository.LastestProfilePostList) SerializeObjectUtils.readingObjectFromByteArrayUsingGzip(value2);
                LOG.info("size {}", giaitrionlinePosts2.getPosts().size());
            }
        }
    }

//    private static void testRunUpdate() {
//        // init repo
//        ProfilePostsRepository profiePostsRepo = ProfilePostsRepository.getInstance();
//        FacebookApp appInfo = new FacebookApp("980001591", "980001591", "Tong hop tin tuc", "1449953991987089", "ad6376294925f0a80e38eeb7c0283380", "1449953991987089|4B3T7sCQVF4bMUQDiAhRUHpIaP0", "");
//        FacebookGraphActions graphActions = new FacebookGraphActions(appInfo, profiePostsRepo);
//        boolean isInitOK = graphActions.initApp();
//        if (isInitOK) {
//            LOG.info("Init OK");
//            SimpleTimer st = new SimpleTimer();
//            ObjectRequest objectRequest = new ObjectRequest();
//            objectRequest.socialType = SocialType.FACEBOOK;
//            objectRequest.objectType = ObjectType.PAGE;
//            objectRequest.objectID = "419512004898020";
//            Pair<FacebookObject, Integer> profileResult = graphActions.doFetchProfile(objectRequest);
//            FacebookObject profileObject = profileResult.first;
//            if (profileObject != null) {
//                LOG.info("JSON - {}", JSON.encode(profileObject));
//            }
//            int profileRequest = profileResult.second;
//            LOG.info("number request {}", profileRequest);
//            LOG.info("time {} ms", st.getTimeAndReset());
//        } else {
//            LOG.info("Init FAILED");
//        }
//    }
}
