package viettel.nfw.social.facebook.updatenews;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import static org.fusesource.leveldbjni.JniDBFactory.asString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectType;
import viettel.nfw.social.facebook.updatenews.graph.entities.SocialType;
import viettel.nfw.social.utils.TParser;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.utils.SerializeObjectUtils;

/**
 *
 * @author duongth5
 */
public class StoreObjectRequestToDbImpl {

    private static final Logger LOG = LoggerFactory.getLogger(StoreObjectRequestToDbImpl.class);
    private static final int MAX_CAPACITY = 2000000;
    private static final int NUMBER_THREAD = 3;
    private static final int NUMBER_ITEMS = 100;
    private static final long DEFAULT_LOOP_TIME_PROFILE = 6L * 60L * 60L * 1000L;
    private static final long DEFAULT_LOOP_TIME_POST = 12L * 60L * 60L * 1000L;
    private static final String FORMAT_COMPOSITE_POST_ID = "%s_%s"; // profileId_postID

    private static final BlockingQueue<ObjectRequest> receivedObjectRequestsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
    private static final BlockingQueue<String> receivedUrlsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);

    private static final BlockingQueue<ObjectRequest> finalQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
    private ScheduledExecutorService executor = null;

    public StoreObjectRequestToDbImpl() {

        executor = Executors.newScheduledThreadPool(NUMBER_THREAD);

        PreProcessObjectRequestsImpl preProcessObjsImpl = new PreProcessObjectRequestsImpl();
        executor.scheduleAtFixedRate(preProcessObjsImpl, 1, 10, TimeUnit.SECONDS);

        PreProcessUrlsImpl preProcessUrlsImpl = new PreProcessUrlsImpl();
        executor.scheduleAtFixedRate(preProcessUrlsImpl, 1, 15, TimeUnit.SECONDS);

        StoreToRepoImpl storeToRepoImpl = new StoreToRepoImpl();
        executor.scheduleAtFixedRate(storeToRepoImpl, 1, 5, TimeUnit.SECONDS);
    }

    public BlockingQueue<ObjectRequest> getReceivedObjectRequestsQueue() {
        return receivedObjectRequestsQueue;
    }

    public BlockingQueue<String> getReceivedUrlsQueue() {
        return receivedUrlsQueue;
    }

    private static StoreObjectRequestToDbImpl instance = null;

    public static StoreObjectRequestToDbImpl getInstance() {
        try {
            return instance == null ? instance = new StoreObjectRequestToDbImpl() : instance;
        } catch (Exception ex) {
            return null;
        }
    }

    private static class StoreToRepoImpl implements Runnable {

        @Override
        public void run() {
            Thread.currentThread().setName("StoreToRepoImpl");
            List<ObjectRequest> objectRequests = new ArrayList<>();
            for (int i = 0; i < NUMBER_ITEMS; i++) {
                ObjectRequest objectRequest = finalQueue.poll();
                if (objectRequest == null) {
                    break;
                } else {
                    objectRequests.add(objectRequest);
                }
            }

            if (objectRequests.isEmpty()) {
                return;
            }

            for (ObjectRequest objectRequest : objectRequests) {
                // write to db
                try {
                    String key = String.format(RunUpdateNews.FORMAT_COMPOSITE_KEY,
                            objectRequest.socialType, objectRequest.objectID, objectRequest.objectType);
                    byte[] keyByteArr = key.getBytes();
                    byte[] valueByteArr = SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(objectRequest);
                    RunUpdateNews.objRequestRepository.write(keyByteArr, valueByteArr);
                    LOG.info("Saved {} to db with key {}", objectRequest.toString(), key);
                } catch (IOException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }

        }
    }

    private static class PreProcessObjectRequestsImpl implements Runnable {

        @Override
        public void run() {
            Thread.currentThread().setName("PreProcessObjectRequestImpl");
            List<ObjectRequest> takenObjectRequests = new ArrayList<>();
            for (int i = 0; i < NUMBER_ITEMS; i++) {
                ObjectRequest objectRequest = receivedObjectRequestsQueue.poll();
                if (objectRequest == null) {
                    break;
                } else {
                    takenObjectRequests.add(objectRequest);
                }
            }

            if (takenObjectRequests.isEmpty()) {
                return;
            }

            for (ObjectRequest objReq : takenObjectRequests) {
                try {
                    if (objReq.socialType.equals(SocialType.FACEBOOK)) {
                        boolean willAddToFinalQueue = false;
                        if (objReq.objectType.equals(ObjectType.PAGE)
                                || objReq.objectType.equals(ObjectType.GROUP)) {
                            // case object request is Profile: Page or Group
                            if (objReq.objectID.matches("^[0-9]+$")) {
                                // this is ID
                                willAddToFinalQueue = true;
                            } else {
                                // this is username, have to check db to get id
                                // if not found id in db, must go to crawl immediately
                                String username = objReq.objectID.toLowerCase();
                                String mappingId = tryToFindProfileId(username);
                                if (StringUtils.isNotEmpty(mappingId)) {
                                    // update mapping username-id to db
                                    RunUpdateNews.mappingUsername2IdRepositpory.write(username.getBytes(), mappingId.getBytes());
                                    // update profileID to object request
                                    objReq.objectID = mappingId;
                                    willAddToFinalQueue = true;
                                }
                            }
                        } else if (objReq.objectType.equals(ObjectType.POST)) {
                            // case object request is Post
                            String compositePostId = objReq.objectID;
                            String[] parts = StringUtils.split(compositePostId, "_");
                            if (parts.length == 2) {
                                String usernameOrId = parts[0];
                                String postId = parts[1];
                                if (StringUtils.isEmpty(usernameOrId)) {
                                    continue;
                                }
                                if (usernameOrId.matches("^[0-9]+$")) {
                                    // this is ID
                                    willAddToFinalQueue = true;
                                } else {
                                    // this is username, have to check db to get id
                                    // if not found id in db, must go to crawl immediately
                                    String username = usernameOrId.toLowerCase();
                                    String mappingId = tryToFindProfileId(username);
                                    if (StringUtils.isNotEmpty(mappingId)) {
                                        // update mapping username-id to db
                                        RunUpdateNews.mappingUsername2IdRepositpory.write(username.getBytes(), mappingId.getBytes());
                                        // update postId then add object request to queue
                                        String rewritePostId = String.format(FORMAT_COMPOSITE_POST_ID, mappingId, postId);
                                        objReq.objectID = rewritePostId;
                                        willAddToFinalQueue = true;
                                    }
                                }
                            }
                        }
                        if (willAddToFinalQueue) {
                            try {
                                finalQueue.put(objReq);
                            } catch (InterruptedException ex) {
                                LOG.error(ex.getMessage(), ex);
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
                Funcs.sleep(Funcs.randInt(300, 500));
            }
        }

    }

    private static class PreProcessUrlsImpl implements Runnable {

        @Override
        public void run() {
            Thread.currentThread().setName("PreProcessUrlsImpl");
            List<String> takenUrls = new ArrayList<>();
            for (int i = 0; i < NUMBER_ITEMS; i++) {
                String url = receivedUrlsQueue.poll();
                if (StringUtils.isEmpty(url)) {
                    break;
                } else {
                    takenUrls.add(url);
                }
            }

            if (takenUrls.isEmpty()) {
                return;
            }

            for (String url : takenUrls) {
                try {
                    URI uri = new URI(url);
                    List<ObjectRequest> objReqs = extractUsernameOrId(uri);
                    if (objReqs == null || objReqs.isEmpty()) {
                        LOG.warn("REVIEW URL: {}", url);
                        continue;
                    }
                    for (ObjectRequest objReq : objReqs) {
                        try {
                            receivedObjectRequestsQueue.put(objReq);
                        } catch (InterruptedException ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    }
                } catch (URISyntaxException ex) {
                    LOG.error(ex.getMessage(), ex);
                    LOG.warn("REVIEW URL: {}", url);
                }
            }
        }
    }

    private static String tryToFindProfileId(String username) {
        String id = "";
        try {
            // check in db
            boolean isExistedInDB = false;
            byte[] idBytes = RunUpdateNews.mappingUsername2IdRepositpory.get(username.getBytes());
            if (idBytes != null) {
                String idStr = asString(idBytes);
                if (StringUtils.isNotEmpty(idStr)) {
                    id = idStr;
                    isExistedInDB = true;
                }
            }
            // if not found in db, go to crawl
            if (!isExistedInDB) {
//                String idStr = immediatelyCrawl(username);
//                if (StringUtils.isNotEmpty(idStr)) {
//                    id = idStr;
//                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return id;
    }

//    private static String immediatelyCrawl(String username) {
//        String returnId = "";
//        try {
//            List<FacebookApp> facebookApps = FacebookProcessImpl.loadFacebookApp(FacebookProcessImpl.APP_INFO_FILENAME);
//            int size = facebookApps.size();
//            Random randomize = new Random();
//            int randomApp = randomize.nextInt(size);
//
//            FacebookApp appInfo = facebookApps.get(randomApp);
//            FacebookGraphActions graphActions = new FacebookGraphActions(appInfo, RunUpdateNews.profiePostsRepository);
//            boolean isInitOK = graphActions.initApp();
//            if (isInitOK) {
//                LOG.info("Init app for immediately {} OK", appInfo.appID);
//                Pair<FacebookObject, Integer> profileResult = graphActions.doFetchProfileInfo(
//                        new ObjectRequest(SocialType.FACEBOOK, username, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
//                FacebookObject fbObj = profileResult.first;
//                if (fbObj != null) {
//                    returnId = fbObj.getInfo().getId();
//                }
//            }
//        } catch (Exception ex) {
//            LOG.error(ex.getMessage(), ex);
//        }
//        return returnId;
//    }

    private static List<ObjectRequest> extractUsernameOrId(URI uri) {
        List<ObjectRequest> result = new ArrayList<>();

        String host = uri.getHost();
        // check hostname contains facebook.com
        if (StringUtils.containsIgnoreCase(host, "facebook.com")) {
            String path = uri.getPath();
            String query = uri.getQuery();

            if (StringUtils.startsWithIgnoreCase(path, "/groups/")) {
                // case groups
                String usernameOrId = StringUtils.replace(path, "/groups/", "");
                usernameOrId = StringUtils.replace(usernameOrId, "/", "");
                if (StringUtils.isNotEmpty(usernameOrId)) {
                    if (usernameOrId.matches("^[0-9]+$")) {
                        // this group url has ID
                        result.add(new ObjectRequest(SocialType.FACEBOOK, usernameOrId, ObjectType.GROUP, DEFAULT_LOOP_TIME_PROFILE));
                    } else {
                        // this group url has username
                    }
                }
            } else if (StringUtils.startsWithIgnoreCase(path, "/pages/")) {
                // case pages with URL https://www.facebook.com/pages/
                String id = TParser.getOneInGroup(path, "[0-9]{10,}");
                if (StringUtils.isNotEmpty(id)) {
                    result.add(new ObjectRequest(SocialType.FACEBOOK, id, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
                }
            } else if (StringUtils.equalsIgnoreCase(path, "/profile.php")) {
                // case pages with URL contains ID
                String id = TParser.getOneInGroup(query, "[0-9]{10,}");
                if (StringUtils.isNotEmpty(id)) {
                    result.add(new ObjectRequest(SocialType.FACEBOOK, id, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
                }
            } else if (path.matches("^/[0-9a-zA-Z.]+$")) {
                // case pages with URL contains Username
                String username = StringUtils.replace(path, "/", "");
                if (StringUtils.isNotEmpty(username)) {
                    result.add(new ObjectRequest(SocialType.FACEBOOK, username, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
                }
            } else if (path.matches("^/[0-9a-zA-Z.]+/posts/[0-9]+/?$")) {
                // case post with URL https://vi-vn.facebook.com/zeddvietnam/posts/807057936074391
                String[] parts = StringUtils.split(path, "/");
                if (parts.length == 3) {
                    String profileIdOrUn = parts[0];
                    String postId = parts[2];
                    if (StringUtils.isNotEmpty(profileIdOrUn) && StringUtils.isNotEmpty(postId)) {
                        result.add(new ObjectRequest(SocialType.FACEBOOK, profileIdOrUn, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
                        String compositePostId = String.format(FORMAT_COMPOSITE_POST_ID, profileIdOrUn, postId);
                        result.add(new ObjectRequest(SocialType.FACEBOOK, compositePostId, ObjectType.POST, DEFAULT_LOOP_TIME_POST));
                    }
                }
            } else if (path.matches("^/[0-9a-zA-Z.]+/videos/[0-9]+/?$")) {
                // case post with URL https://www.facebook.com/taychaytanhiepphat/videos/1580225455528404/
                String[] parts = StringUtils.split(path, "/");
                if (parts.length == 3) {
                    String profileIdOrUn = parts[0];
                    String postId = parts[2];
                    if (StringUtils.isNotEmpty(profileIdOrUn) && StringUtils.isNotEmpty(postId)) {
                        result.add(new ObjectRequest(SocialType.FACEBOOK, profileIdOrUn, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
                        String compositePostId = String.format(FORMAT_COMPOSITE_POST_ID, profileIdOrUn, postId);
                        result.add(new ObjectRequest(SocialType.FACEBOOK, compositePostId, ObjectType.POST, DEFAULT_LOOP_TIME_POST));
                    }
                }
            } else if (StringUtils.equalsIgnoreCase(path, "/permalink.php")) {
                // case post with URL https://www.facebook.com/permalink.php?story_fbid=341293776069362&id=318099571722116
                Map<String, List<String>> params = Parser.splitQuery(uri);
                if (MapUtils.isNotEmpty(params)) {
                    String postId = "";
                    String profileId = "";
                    List<String> storyFbids = params.get("story_fbid");
                    if (storyFbids != null && !storyFbids.isEmpty()) {
                        postId = storyFbids.get(0);
                    }
                    List<String> profileIds = params.get("id");
                    if (profileIds != null && !profileIds.isEmpty()) {
                        profileId = profileIds.get(0);
                    }
                    if (StringUtils.isNotEmpty(profileId) && StringUtils.isNotEmpty(postId)) {
                        result.add(new ObjectRequest(SocialType.FACEBOOK, profileId, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
                        String compositePostId = String.format(FORMAT_COMPOSITE_POST_ID, profileId, postId);
                        result.add(new ObjectRequest(SocialType.FACEBOOK, compositePostId, ObjectType.POST, DEFAULT_LOOP_TIME_POST));
                    }
                }
            } else if (path.matches("^/[0-9a-zA-Z.]+/photos/[0-9a-zA-Z.]+/[0-9]+/?$")) {
                // case post is photo with URL https://vi-vn.facebook.com/tran.thanh.ne/photos/a.682505288445462.1073741829.488035214559138/1086281714734482/
                String[] parts = StringUtils.split(path, "/");
                if (parts.length == 4) {
                    String profileUsername = parts[0];
                    int lastDot = StringUtils.lastIndexOf(parts[2], ".");
                    String profileId = StringUtils.substring(parts[2], lastDot + 1);
                    String postId = parts[3];

                    if (StringUtils.isNotEmpty(profileId) && StringUtils.isNotEmpty(postId)) {

                        try {
                            if (!StringUtils.equalsIgnoreCase(profileUsername, profileId)) {
                                RunUpdateNews.mappingUsername2IdRepositpory.write(profileUsername.getBytes(), profileId.getBytes());
                            }
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }

                        result.add(new ObjectRequest(SocialType.FACEBOOK, profileId, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
                        String compositePostId = String.format(FORMAT_COMPOSITE_POST_ID, profileId, postId);
                        result.add(new ObjectRequest(SocialType.FACEBOOK, compositePostId, ObjectType.POST, DEFAULT_LOOP_TIME_POST));
                    }
                }
            }
        }
        return result;
    }

    public static void main(String[] args) {
        try {
            String url = "https://www.facebook.com/permalink.php?story_fbid=341293776069362&id=318099571722116";
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path.matches("^/[0-9a-zA-Z.]+/posts/[0-9]+/?$")) {
                // case https://vi-vn.facebook.com/zeddvietnam/posts/807057936074391/
                System.out.println("true");
                String[] parts = StringUtils.split(path, "/");
                System.out.println(Arrays.toString(parts));
            } else if (StringUtils.equalsIgnoreCase(path, "/permalink.php")) {
                // case post with URL https://www.facebook.com/permalink.php?story_fbid=341293776069362&id=318099571722116
                Map<String, List<String>> params = Parser.splitQuery(uri);
                if (MapUtils.isNotEmpty(params)) {
                    String postId = "";
                    String profileId = "";
                    List<String> storyFbids = params.get("story_fbid");
                    if (storyFbids != null && !storyFbids.isEmpty()) {
                        postId = storyFbids.get(0);
                    }
                    List<String> profileIds = params.get("id");
                    if (profileIds != null && !profileIds.isEmpty()) {
                        profileId = profileIds.get(0);
                    }
                    if (StringUtils.isNotEmpty(profileId) && StringUtils.isNotEmpty(postId)) {
                        System.out.println(profileId + "_" + postId);
                    }
                }
            } else {
                System.out.println("false");
            }
        } catch (URISyntaxException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

}
