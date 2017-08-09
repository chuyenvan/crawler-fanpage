package viettel.nfw.social.facebook.updatenews.graph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.restfb.types.CategorizedFacebookType;
import com.restfb.types.Comment;
import com.restfb.types.Group;
import com.restfb.types.NamedFacebookType;
import com.restfb.types.Page;
import com.restfb.types.Post;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.Pair;

/**
 * Do some convert from RestFb Object to my own Object: Profile, Post, Comment,
 * Like
 *
 * @author duongth5
 */
public class FacebookGraphObjectConvert {

    private static final Logger LOG = LoggerFactory.getLogger(FacebookGraphObjectConvert.class);

    private static final SimpleDateFormat SDF_FACEBOOK = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");

    public static viettel.nfw.social.model.facebook.Profile convertRestFbPage(Page page) {
        viettel.nfw.social.model.facebook.Profile profile = new viettel.nfw.social.model.facebook.Profile();
        String pageId = page.getId();
        if (StringUtils.isNotEmpty(pageId)) {
            try {
                // set page ID
                profile.setId(pageId);
                // set page Type
                profile.setType("Page");

                // set page Username
                String pageUsername = page.getUsername();
                if (StringUtils.isNotEmpty(pageUsername)) {
                    profile.setUsername(pageUsername);
                }

                // set page Name
                String pageName = page.getName();
                if (StringUtils.isNotEmpty(pageName)) {
                    profile.setFullname(pageName);
                }

                // set page Link
                String pageLink = page.getLink();
                if (StringUtils.isNotEmpty(pageLink)) {
                    profile.setUrl(pageLink);
                } else {
                    profile.setUrl("https://www.facebook.com/profile.php?id=" + pageId);
                }

                // set page about
                StringBuilder sbAbout = new StringBuilder();
                String pageMission = page.getMission();
                if (StringUtils.isNotEmpty(pageMission)) {
                    sbAbout.append(pageMission);
                }
                String pageGeneralInfo = page.getGeneralInfo();
                if (StringUtils.isNotEmpty(pageGeneralInfo)) {
                    sbAbout.append("\n");
                    sbAbout.append(pageGeneralInfo);
                }
                String pageDescription = page.getDescription();
                if (StringUtils.isNotEmpty(pageDescription)) {
                    sbAbout.append("\n");
                    sbAbout.append(pageDescription);
                }
                String pageAbout = page.getAbout();
                if (StringUtils.isNotEmpty(pageAbout)) {
                    sbAbout.append("\n");
                    sbAbout.append(pageAbout);
                }
                String pageWebsite = page.getWebsite();
                if (StringUtils.isNotEmpty(pageWebsite)) {
                    sbAbout.append("\n");
                    sbAbout.append(pageWebsite);
                }
                String pagePhone = page.getPhone();
                if (StringUtils.isNotEmpty(pagePhone)) {
                    sbAbout.append("\n");
                    sbAbout.append("phone: ").append(pagePhone);
                }
                if (StringUtils.isNotEmpty(sbAbout.toString())) {
                    profile.setBio(sbAbout.toString());
                }

				// set page likes
                //long pageLikes = page.getLikes();
                //profile.setTotalFriends(pageLikes);
                // set crawled time
                profile.setCreateTime(new Date());

            } catch (Exception ex) {
                LOG.error("error with page ID {}", pageId);
                LOG.error(ex.getMessage(), ex);
            }
        }
        return profile;
    }

    public static viettel.nfw.social.model.facebook.Profile convertRestFbGroup(Group group) {
        viettel.nfw.social.model.facebook.Profile profile = new viettel.nfw.social.model.facebook.Profile();
        String groupId = group.getId();
        if (StringUtils.isNotEmpty(groupId)) {
            try {
                // set group id
                profile.setId(groupId);
                // set group type
                profile.setType("Group");

                // set group description
                StringBuilder sbAbout = new StringBuilder();
                String description = group.getDescription();
                if (StringUtils.isNotEmpty(description)) {
                    sbAbout.append(description);
                }
                // TODO: recheck api, not sure it is group url
                String groupWebsite = group.getLink();
                if (StringUtils.isNotEmpty(groupWebsite)) {
                    sbAbout.append("\n");
                    sbAbout.append(groupWebsite);
                }
                if (StringUtils.isNotEmpty(sbAbout.toString())) {
                    profile.setBio(sbAbout.toString());
                }

                // set group name
                String groupName = group.getName();
                if (StringUtils.isNotEmpty(groupName)) {
                    profile.setFullname(groupName);
                }

				// set group privacy
                // in this graph, only query group OPEN
                String groupPrivacy = group.getPrivacy();
                if (StringUtils.isNotEmpty(groupPrivacy)) {
                    profile.setGroupPrivacy(groupPrivacy);
                }

                // set group owner
                NamedFacebookType groupOwner = group.getOwner();
                if (groupOwner != null) {
                    String ownerId = groupOwner.getId();
                    String ownerName = groupOwner.getName();
                    profile.setGroupOwnerId(ownerId);
                    profile.setGroupOwnerName(ownerName);
                }

                // set group url
                profile.setUrl("https://www.facebook.com/groups/" + groupId);

                // set crawled time
                profile.setCreateTime(new Date());

            } catch (Exception ex) {
                LOG.error("Error with group ID {}", groupId);
                LOG.error(ex.getMessage(), ex);
            }
        }
        return profile;
    }

    public static viettel.nfw.social.model.facebook.Post convertRestFbPost(Post post, String wallProfileId) {
        viettel.nfw.social.model.facebook.Post vPost = new viettel.nfw.social.model.facebook.Post();
        String postId = post.getId();
        if (StringUtils.isNotEmpty(postId)) {
            try {
                // set post ID
                vPost.setId(postId);

                // set post actorProfileId
                CategorizedFacebookType from = post.getFrom();
                if (from != null) {
                    String fromId = from.getId();
                    vPost.setActorProfileId(fromId);
                    String fromName = from.getName();
                    vPost.setActorProfileDisplayName(fromName);

                    // set post wallProfileId
                    try {
                        List<NamedFacebookType> tos = post.getTo();
                        if (!tos.isEmpty()) {
                            String toId = tos.get(0).getId();
                            vPost.setWallProfileId(toId);
                        } else {
                            vPost.setWallProfileId(fromId);
                        }
                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                        vPost.setWallProfileId(wallProfileId);
                    }

                }

                // set post Type
                String statusType = post.getStatusType();
                if (StringUtils.isNotEmpty(statusType)) {
                    vPost.setType(statusType);
                }

                // set post Content
                StringBuilder sbContent = new StringBuilder();
                String message = post.getMessage();
                if (StringUtils.isNotEmpty(message)) {
                    sbContent.append(message);
                }
                String linkDescription = post.getDescription();
                if (StringUtils.isNotEmpty(linkDescription)) {
                    sbContent.append("\n");
                    sbContent.append(linkDescription);
                }
                if (StringUtils.isNotEmpty(sbContent.toString())) {
                    vPost.setContent(sbContent.toString());
                }

                // set Post share Link
                String link = post.getLink();
                List<String> links = new ArrayList<>();
                if (StringUtils.isNotEmpty(link)) {
                    if (StringUtils.startsWith(link, "https://www.facebook.com/")) {
                        links.add(link);
                        vPost.setInsideUrl(JSON.encode(links));
                    } else {
                        links.add(link);
                        vPost.setOutsideUrl(JSON.encode(links));
                    }
                }

                // set Post Time
                Date createdTime = post.getCreatedTime();
                if (createdTime != null) {
                    vPost.setPostTime(createdTime);
                }

                // set crawled time
                vPost.setCreateTime(new Date());

                // set number post Likes
                Post.Likes likes = post.getLikes();
                if (likes != null) {
                    List<NamedFacebookType> currentLikes = likes.getData();
                    if (!currentLikes.isEmpty()) {
                        vPost.setLikesCount(currentLikes.size());
                    }
                }

                // set number post comments
                Post.Comments comments = post.getComments();
                if (comments != null) {
                    List<Comment> currentComments = comments.getData();
                    if (!currentComments.isEmpty()) {
                        vPost.setCommentsCount(currentComments.size());
                    }
                }

                // TODO: missing post url
                vPost.setUrl("https://www.facebook.com/" + postId);

            } catch (Exception ex) {
                LOG.error("Error with post id {}", postId);
                LOG.error(ex.getMessage(), ex);
            }
        }

        return vPost;
    }

    public static viettel.nfw.social.model.facebook.Comment convertRestFbComment(Comment comment, String postId) {
        viettel.nfw.social.model.facebook.Comment vComment = new viettel.nfw.social.model.facebook.Comment();
        String commentId = comment.getId();
        if (StringUtils.isNotEmpty(commentId)) {
            try {
                // set comment Id 
                vComment.setId(commentId);
                // set post Id
                vComment.setPostId(postId);

                // set commenter
                CategorizedFacebookType from = comment.getFrom();
                if (from != null) {
                    String fromId = from.getId();
                    String fromName = from.getName();
                    vComment.setActorProfileId(fromId);
                    vComment.setActorProfileDisplayName(fromName);
                }

                // set comment content
                String commentContent = comment.getMessage();
                if (StringUtils.isNotEmpty(commentContent)) {
                    vComment.setContent(commentContent);
                }

                // set comment time
                Date commentTime = comment.getCreatedTime();
                if (commentTime != null) {
                    vComment.setCommentTime(commentTime);
                }

                // set likes count
                long likesCount = comment.getLikeCount();
                vComment.setLikesCount(likesCount);

                // set crawled time
                vComment.setCreateTime(new Date());

                // set url attachment
                Comment.Attachment attachment = comment.getAttachment();
                if (attachment != null) {
                    String attachmentUrl = attachment.getUrl();
                    List<String> links = new ArrayList<>();
                    links.add(attachmentUrl);
                    vComment.setAttachUrl(JSON.encode(links));
                }

            } catch (Exception ex) {
                LOG.error("Error with comment id {} in post id {}", commentId, postId);
                LOG.error(ex.getMessage(), ex);
            }
        }

        return vComment;
    }

    public static viettel.nfw.social.model.facebook.Post jsonParserFbPostInfo(String jsonStr, String wallProfileId) {
        viettel.nfw.social.model.facebook.Post vPost = null;
        try {
            JsonParser jsonParser = new JsonParser();
            JsonElement jsonElement = jsonParser.parse(jsonStr);
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            // get ID
            JsonElement idElement = jsonObject.get("id");
            if (idElement != null) {
                String postId = idElement.getAsString();
                if (StringUtils.isNotEmpty(postId)) {
                    vPost = new viettel.nfw.social.model.facebook.Post();
                    // set ID
                    vPost.setId(postId);

                    JsonElement itemJsonElement;
                    JsonObject itemJsonObject;
                    // get From
                    itemJsonElement = jsonObject.get("from");
                    if (itemJsonElement != null) {
                        itemJsonObject = itemJsonElement.getAsJsonObject();
                        JsonElement e = itemJsonObject.get("id");
                        if (e != null) {
                            String fromId = e.getAsString();
                            vPost.setActorProfileId(fromId);
                        }
                        e = itemJsonObject.get("name");
                        if (e != null) {
                            String fromName = e.getAsString();
                            vPost.setActorProfileDisplayName(fromName);
                        }
                    }

                    // get To
                    itemJsonElement = jsonObject.get("to");
                    if (itemJsonElement != null) {
                        try {
                            itemJsonObject = itemJsonElement.getAsJsonObject();
                            JsonElement e = itemJsonObject.get("id");
                            if (e != null) {
                                String toId = e.getAsString();
                                vPost.setActorProfileId(toId);
                            } else {
                                vPost.setActorProfileId(wallProfileId);
                            }
                        } catch (IllegalStateException e) {
                            LOG.error("Error while parsing json of postId " + postId, e);
                            vPost.setWallProfileId(wallProfileId);
                        }
                    } else {
                        vPost.setWallProfileId(wallProfileId);
                    }

                    // get status_type
                    itemJsonElement = jsonObject.get("status_type");
                    if (itemJsonElement != null) {
                        String statusType = itemJsonElement.getAsString();
                        vPost.setType(statusType);
                    }

                    // get message(content)
                    StringBuilder sbContent = new StringBuilder();
                    itemJsonElement = jsonObject.get("message");
                    if (itemJsonElement != null) {
                        String message = itemJsonElement.getAsString();
                        sbContent.append(message);
                    }
                    // get link descripton
                    itemJsonElement = jsonObject.get("description");
                    if (itemJsonElement != null) {
                        String linkDescription = itemJsonElement.getAsString();
                        sbContent.append("\n").append(linkDescription);
                    }
                    if (StringUtils.isNotEmpty(sbContent.toString())) {
                        vPost.setContent(sbContent.toString());
                    }

                    // get share_link
                    itemJsonElement = jsonObject.get("link");
                    if (itemJsonElement != null) {
                        String link = itemJsonElement.getAsString();
                        List<String> links = new ArrayList<>();
                        if (StringUtils.isNotEmpty(link)) {
                            if (StringUtils.startsWith(link, "https://www.facebook.com/")) {
                                links.add(link);
                                vPost.setInsideUrl(JSON.encode(links));
                            } else {
                                links.add(link);
                                vPost.setOutsideUrl(JSON.encode(links));
                            }
                        }
                    }

                    // get post Time
                    itemJsonElement = jsonObject.get("created_time");
                    if (itemJsonElement != null) {
                        String createTimeStr = itemJsonElement.getAsString();
                        if (StringUtils.isNoneEmpty(createTimeStr)) {
                            Date createdTime = new Date();
                            try {
                                createdTime = SDF_FACEBOOK.parse(createTimeStr.trim());
                            } catch (ParseException | NumberFormatException e) {
                                LOG.error("Error while parsing date " + createTimeStr, e);
                            }
                            vPost.setPostTime(createdTime);
                        } else {
                            vPost.setPostTime(new Date());
                        }
                    }

                    // get crawled time
                    vPost.setCreateTime(new Date());

                    // get counter likes
                    itemJsonElement = jsonObject.get("likes");
                    if (itemJsonElement != null) {
                        itemJsonObject = itemJsonElement.getAsJsonObject();
                        JsonElement e = itemJsonObject.get("data");
                        if (e != null) {
                            long likesCount = 0;
                            try {
                                JsonArray likesArray = e.getAsJsonArray();
                                likesCount = likesArray.size();
                            } catch (Exception ex) {
                                LOG.error("Error while getting list likes", ex);
                            }
                            vPost.setLikesCount(likesCount);
                        }
                    }

                    // get counter comments
                    itemJsonElement = jsonObject.get("comments");
                    if (itemJsonElement != null) {
                        itemJsonObject = itemJsonElement.getAsJsonObject();
                        JsonElement e = itemJsonObject.get("data");
                        if (e != null) {
                            long commentsCount = 0;
                            try {
                                JsonArray commentsArray = e.getAsJsonArray();
                                commentsCount = commentsArray.size();
                            } catch (Exception ex) {
                                LOG.error("Error while getting list comments", ex);
                            }
                            vPost.setCommentsCount(commentsCount);
                        }
                    }

					// TODO get counter for shares
                    // get postUrl
                    vPost.setUrl("https://www.facebook.com/" + postId);
                } else {
                    LOG.error("Error while getting postId from JSON {}", jsonStr);
                }
            } else {
                LOG.error("Error while getting postId from JSON {}", jsonStr);
            }
        } catch (Exception ex) {
            LOG.error("Error while parsing JSON " + jsonStr, ex);
        }

        return vPost;
    }

    public static Map<String, String> jsonParserFbGroupInfo(String jsonStr) {
        Map<String, String> id2values = new HashMap<>();
        try {
            JsonParser jsonParser = new JsonParser();
            JsonElement jsonElement = jsonParser.parse(jsonStr);
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            JsonElement itemJsonElement = jsonObject.get("id");
            if (itemJsonElement != null) {
                String id = itemJsonElement.getAsString();
                if (StringUtils.isNotEmpty(id)) {
                    id2values.put("id", id);
                }
            }

            itemJsonElement = jsonObject.get("name");
            if (itemJsonElement != null) {
                String name = itemJsonElement.getAsString();
                if (StringUtils.isNotEmpty(name)) {
                    id2values.put("name", name);
                }
            }

            itemJsonElement = jsonObject.get("email");
            if (itemJsonElement != null) {
                String email = itemJsonElement.getAsString();
                if (StringUtils.isNotEmpty(email)) {
                    id2values.put("email", email);
                }
            }

            itemJsonElement = jsonObject.get("privacy");
            if (itemJsonElement != null) {
                String privacy = itemJsonElement.getAsString();
                if (StringUtils.isNotEmpty(privacy)) {
                    id2values.put("privacy", privacy);
                }
            }

        } catch (Exception e) {
            LOG.error("Error while parsing JSON " + jsonStr, e);
        }
        return id2values;
    }

    public static Pair<Long, Long> jsonParserFbPostLikesCommentsCounter(String jsonStr) {
        long likesCount = 0;
        long commentsCount = 0;

        try {
            JsonParser jsonParser = new JsonParser();
            JsonElement jsonElement = jsonParser.parse(jsonStr);
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            JsonObject itemJsonObject;
            // get likes count
            JsonElement itemJsonElement = jsonObject.get("likes");
            if (itemJsonElement != null) {
                itemJsonObject = itemJsonElement.getAsJsonObject();
                JsonElement e = itemJsonObject.get("summary");
                if (e != null) {
                    itemJsonObject = e.getAsJsonObject();
                    e = itemJsonObject.get("total_count");
                    if (e != null) {
                        String totalCountStr = e.getAsString();
                        if (StringUtils.isNotEmpty(totalCountStr)) {
                            likesCount = Long.parseLong(totalCountStr);
                        }
                    }
                }
            }

            // get comments count
            itemJsonElement = jsonObject.get("comments");
            if (itemJsonElement != null) {
                itemJsonObject = itemJsonElement.getAsJsonObject();
                JsonElement e = itemJsonObject.get("summary");
                if (e != null) {
                    itemJsonObject = e.getAsJsonObject();
                    e = itemJsonObject.get("total_count");
                    if (e != null) {
                        String totalCountStr = e.getAsString();
                        if (StringUtils.isNotEmpty(totalCountStr)) {
                            commentsCount = Long.parseLong(totalCountStr);
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error("Error while parsing JSON " + jsonStr, ex);
        }

        return new Pair<>(likesCount, commentsCount);
    }

    public static Pair<Map<String, Long>, String> jsonParserFeed(String jsonStr) {
        Map<String, Long> postId2Time = new HashMap<>();
        String nextUrl = null;

        try {
            JsonParser jsonParser = new JsonParser();
            JsonElement jsonElement = jsonParser.parse(jsonStr);
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            JsonElement itemJsonElement = jsonObject.get("data");
            JsonElement e;
            JsonObject o;
            if (itemJsonElement != null) {
                try {
                    JsonArray postArray = itemJsonElement.getAsJsonArray();
                    if (postArray != null) {
                        String postId = null;
                        String createdTimeStr;
                        long createdTime = -1;
                        for (int i = 0; i < postArray.size(); i++) {
                            try {
                                e = postArray.get(i);
                                if (e != null) {
                                    o = e.getAsJsonObject();
                                    JsonElement je = o.get("id");
                                    if (je != null) {
                                        postId = je.getAsString();
                                    }
                                    je = o.get("created_time");
                                    if (je != null) {
                                        createdTimeStr = je.getAsString();
                                        if (StringUtils.isNotEmpty(createdTimeStr)) {
                                            try {
                                                createdTime = SDF_FACEBOOK.parse(createdTimeStr.trim()).getTime();
                                            } catch (ParseException | NumberFormatException ex) {
                                                LOG.error("Error while parsing time", ex);
                                            }
                                        }
                                    }
                                    if (StringUtils.isNotEmpty(postId)) {
                                        if (createdTime == -1) {
                                            createdTime = System.currentTimeMillis();
                                        }
                                        postId2Time.put(postId, createdTime);
                                    }
                                }
                            } catch (Exception ex) {
                                LOG.error("Error while parsing post item in array", ex);
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOG.error("Error while getting list post", ex);
                }
            }

            itemJsonElement = jsonObject.get("paging");
            if (itemJsonElement != null) {
                o = itemJsonElement.getAsJsonObject();
                e = o.get("next");
                if (e != null) {
                    nextUrl = e.getAsString();
                }
            }

        } catch (Exception ex) {
            LOG.error("Error while parsing JSON " + jsonStr, ex);
        }

        return new Pair<>(postId2Time, nextUrl);
    }

    public static Pair<Map<String, String>, String> jsonParserListLikesOfPost(String jsonStr) {
        Map<String, String> id2names = new HashMap<>();
        String nextUrl = null;

        try {
            JsonParser jsonParser = new JsonParser();
            JsonElement jsonElement = jsonParser.parse(jsonStr);
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            JsonElement itemJsonElement = jsonObject.get("data");
            JsonElement e;
            JsonObject o;
            if (itemJsonElement != null) {
                try {
                    JsonArray postArray = itemJsonElement.getAsJsonArray();
                    if (postArray != null) {
                        String profileId = null;
                        String profileName = null;
                        for (int i = 0; i < postArray.size(); i++) {
                            try {
                                e = postArray.get(i);
                                if (e != null) {
                                    o = e.getAsJsonObject();
                                    JsonElement je = o.get("id");
                                    if (je != null) {
                                        profileId = je.getAsString();
                                    }
                                    je = o.get("name");
                                    if (je != null) {
                                        profileName = je.getAsString();
                                    }
                                    if (StringUtils.isNotEmpty(profileId)) {
                                        id2names.put(profileId, profileName);
                                    }
                                }
                            } catch (Exception ex) {
                                LOG.error("Error while parsing profile item in array", ex);
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOG.error("Error while getting list likes", ex);
                }
            }

            itemJsonElement = jsonObject.get("paging");
            if (itemJsonElement != null) {
                o = itemJsonElement.getAsJsonObject();
                e = o.get("next");
                if (e != null) {
                    nextUrl = e.getAsString();
                }
            }

        } catch (Exception ex) {
            LOG.error("Error while parsing JSON " + jsonStr, ex);
        }

        return new Pair(id2names, nextUrl);
    }

    public static Pair<Map<Map<String, String>,String>, String> jsonParserListReactionsOfPost(String jsonStr) {
        Map<String, String> id2names = new HashMap<>();
        Map<Map<String, String>,String> idnames2type=new HashMap<>();
        String nextUrl = null;

        try {
            JsonParser jsonParser = new JsonParser();
            JsonElement jsonElement = jsonParser.parse(jsonStr);
            JsonObject jsonObject = jsonElement.getAsJsonObject();

            JsonElement itemJsonElement = jsonObject.get("data");
            JsonElement e;
            JsonObject o;
            if (itemJsonElement != null) {
                try {
                    JsonArray postArray = itemJsonElement.getAsJsonArray();
                    if (postArray != null) {
                        String profileId = null;
                        String profileName = null;
                        String profileType=null;
                        for (int i = 0; i < postArray.size(); i++) {
                            try {
                                e = postArray.get(i);
                                if (e != null) {
                                    o = e.getAsJsonObject();
                                    JsonElement je = o.get("id");
                                    if (je != null) {
                                        profileId = je.getAsString();
                                    }
                                    je = o.get("name");
                                    if (je != null) {
                                        profileName = je.getAsString();
                                    }
                                    je = o.get("type");
                                    if (je != null) {
                                        profileType = je.getAsString();
                                    }
                                    if (StringUtils.isNotEmpty(profileId)) {
                                        id2names.put(profileId, profileName);
                                        idnames2type.put(id2names,profileType);
                                    }
                                }
                            } catch (Exception ex) {
                                LOG.error("Error while parsing profile item in array", ex);
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOG.error("Error while getting list likes", ex);
                }
            }

            itemJsonElement = jsonObject.get("paging");
            if (itemJsonElement != null) {
                o = itemJsonElement.getAsJsonObject();
                e = o.get("next");
                if (e != null) {
                    nextUrl = e.getAsString();
                }
            }

        } catch (Exception ex) {
            LOG.error("Error while parsing JSON " + jsonStr, ex);
        }

        return new Pair(idnames2type, nextUrl);
    }
}
