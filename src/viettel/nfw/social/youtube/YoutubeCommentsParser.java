package viettel.nfw.social.youtube;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.ocpsoft.prettytime.nlp.PrettyTimeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.model.youtube.ReplyComment;
import viettel.nfw.social.model.youtube.VideoComment;
import viettel.nfw.social.model.youtube.VideoInfo;
import viettel.nfw.social.model.youtube.YouTubeObject;
import viettel.nfw.social.utils.TCrawler;
import vn.viettel.social.gp.utils.GooglePlusURL;
import vn.viettel.social.utils.HttpResponseInfo;
import vn.viettel.social.utils.Utils;
import vn.viettel.social.utils.consts.SCommon;
import vn.viettel.social.utils.urlbuilder.UrlBuilder;

/**
 * This class support parse comments of YouTube video
 *
 * @author duongth5
 */
public class YoutubeCommentsParser {

    private static final Logger LOG = LoggerFactory.getLogger(YoutubeCommentsParser.class);
    private static final Map<String, String> timeMap = new HashMap<>();

    static {
        timeMap.put("giây", "second");
        timeMap.put("phút", "minute");
        timeMap.put("giờ", "hour");
        timeMap.put("ngày", "day");
        timeMap.put("tuần", "week");
        timeMap.put("tháng", "month");
        timeMap.put("năm", "year");
    }

    /**
     * Get video id from input URL or from embed.
     *
     * Support 3 types of URL:
     *
     * Type 1: https://www.youtube.com/watch?v=e-ORhEE9VVg
     *
     * Type 2: http://youtu.be/e-ORhEE9VVg
     *
     * Type 3: https://www.youtube.com/embed/wgYmWEZAtDw?wmode=opaque
     *
     * @param youtubeVideoUrl URL to YouTube video
     * @return YouTube video id
     */
    public static String getVideoIdFromUrl(String youtubeVideoUrl) {
        String videoId = "";

        try {
            URL url = new URL(youtubeVideoUrl);

            String host = url.getHost();
            String path = url.getPath();
            LOG.info("{} - {}", host, path);

            if (host.contains("youtube.com")) {
                if (path.startsWith("/watch")) {
                    Map<String, List<String>> query_pairs = Utils.splitQuery(url);
                    if (query_pairs.containsKey("v")) {
                        videoId = query_pairs.get("v").get(0);
                    }
                } else if (path.startsWith("/embed/")) {
                    videoId = StringUtils.remove(path, "/embed/");
                }
            } else if (host.contains("youtu.be")) {
                videoId = path;
            }

        } catch (MalformedURLException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        return videoId;
    }

    public static void main(String[] args) {
//        String videoId1 = "e-ORhEE9VVg";
//        String videoId2 = "PYVACUD6w6w";
//        String PROXY_HOSTNAME = "192.168.4.13";
//        int PROXY_PORT = 3128;
//        Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOSTNAME, PROXY_PORT));
//        process(videoId2, proxy);

//        List<String> urls = new ArrayList<>();
//        urls.add("https://www.youtube.com/watch?v=e-ORhEE9VVg");
//        urls.add("http://youtu.be/e-ORhEE9VVg");
//        urls.add("https://www.youtube.com/embed/wgYmWEZAtDw?wmode=opaque");
//
//        for (String url : urls) {
//            String videoId = getVideoIdFromUrl(url);
//            LOG.info("video id: {}", videoId);
//        }
//        doGetGooglePlusIds();
        doPushGooglePlusUrls();
    }

    /**
     * Process parse YouTube video info and it's comments
     *
     * @param videoId id of YouTube video
     * @param proxy proxy
     * @return
     */
    public static YouTubeObject process(String videoId, Proxy proxy) {
        YouTubeObject ytObj = new YouTubeObject();
        // get video info
        VideoInfo videoInfo = parseVideoInfo(videoId, proxy);
        ytObj.setVideoInfo(videoInfo);
        // get video comments (2 times, 50 results each time)
        List<VideoComment> videoComments = parseVideoComments(videoId, proxy);
        ytObj.setVideoComments(videoComments);
        return ytObj;
    }

    private static String generateURLGetRepliesOfComment(String activityId) {
        UrlBuilder url = UrlBuilder.empty().withScheme("https")
                .withHost("plus.google.com")
                .withPath("/app/basic/stream/" + activityId);
        return url.toString();
    }

    private static String generateURLVideoComments(String videoId) {
        UrlBuilder url = UrlBuilder.empty().withScheme("https")
                .withHost("gdata.youtube.com")
                .withPath("/feeds/api/videos/" + videoId + "/comments")
                .addParameter("v", "2")
                .addParameter("alt", "json")
                .addParameter("max-results", "50");
        return url.toString();
    }

    private static String generateURLVideoInfo(String videoId) {
        UrlBuilder url = UrlBuilder.empty().withScheme("https")
                .withHost("gdata.youtube.com")
                .withPath("/feeds/api/videos/" + videoId)
                .addParameter("v", "2")
                .addParameter("alt", "json");
        return url.toString();
    }

    /**
     * Convert human time in Vietnamese to readable time.
     *
     * @param createTime input string of human time
     * @return date
     */
    private static Date humanTimeViParser(String createTime) {
        String time = null;
        String period = null;
        int num = 0;
        boolean isContinue = true;
        String[] words = StringUtils.split(createTime.trim(), " ");
        if (words.length != 3) {
            LOG.info("words length is not equal 3. {}", createTime);
            return null;
        }
        try {
            num = Integer.valueOf(words[0]);
        } catch (NumberFormatException ex) {
            LOG.error(ex.getMessage(), ex);
            isContinue = false;
        }

        if (timeMap.containsKey(words[1].trim())) {
            if (num > 1) {
                time = timeMap.get(words[1].trim()) + "s";
            } else {
                time = timeMap.get(words[1].trim());
            }
        } else {
            isContinue = false;
        }

        if (StringUtils.equalsIgnoreCase(words[2], "trước")) {
            period = "ago";
        }

        if (isContinue) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.valueOf(num));
            sb.append(" ");
            sb.append(time);
            sb.append(" ");
            sb.append(period);
            LOG.debug("time vi to en: {}", sb.toString());
            List<Date> date = new PrettyTimeParser().parse(sb.toString());
            if (date.isEmpty()) {
                return null;
            }
            return date.get(0);
        } else {
            return null;
        }
    }

    private static List<ReplyComment> parseRepliesOfComment(String commentId, Proxy proxy) {

        List<ReplyComment> replies = new ArrayList<>();
        String urlStream = generateURLGetRepliesOfComment(commentId);
        try {
            // get page of activity
            HttpResponseInfo responseStream = Utils.singleGet(urlStream, proxy);
            int status = responseStream.getStatus();
            if (status == 200) {
                Document streamDoc = Jsoup.parse(responseStream.getBody());
                Elements repliesElement = streamDoc.select("span[data-itemid^=" + commentId + "#]");
                if (!repliesElement.isEmpty()) {
                    for (Element element : repliesElement) {

                        String replyId = element.attr("data-itemid");
                        String replyContent = element.text();
                        LOG.debug("reply - content: {}", replyContent);
                        String replyUrl = String.format(GooglePlusURL.FORMAT_COMMENT, URLEncoder.encode(element.attr("data-itemid"), SCommon.CHARSET_UTF_8));

                        // check if have translate
                        Elements div_translate = element.parent().select("div > a:contains(Dịch)");
                        Elements userCommentElement;
                        Elements timeCommentElement;
                        if (!div_translate.isEmpty()) {
                            userCommentElement = element.parent().parent().parent().select("div:eq(0) > a");
                            timeCommentElement = element.parent().parent().parent().select("div:eq(0) > span");
                        } else {
                            userCommentElement = element.parent().parent().select("div:eq(0) > a");
                            timeCommentElement = element.parent().parent().select("div:eq(0) > span");
                        }

                        // get user who comments
                        String userId = "";
                        if (!userCommentElement.isEmpty()) {
                            String link = GooglePlusURL.BASE_URL + userCommentElement.get(0).attr("href");
                            URL url = new URL(link);
                            Pattern pattern = Pattern.compile("^(/app/basic/)(\\d++)(/posts)$");
                            Matcher matcher = pattern.matcher(url.getPath());
                            if (matcher.matches()) {
                                userId = matcher.group(2);
                            }
                        }

                        // get time of comment
                        Date publishedTime = null;
                        if (!timeCommentElement.isEmpty()) {
                            String createdTime = timeCommentElement.get(0).ownText();
                            LOG.debug("reply - human time  : " + createdTime);
                            publishedTime = humanTimeViParser(createdTime);
                        }

                        // push to big data
                        ReplyComment reply = new ReplyComment();
                        reply.setReplyId(replyId);
                        reply.setCommentId(commentId);
                        reply.setGooglePlusUserId(userId);
                        reply.setContent(replyContent);
                        reply.setPublishedTime(publishedTime);
                        reply.setUrl(replyUrl);
                        reply.setCrawledTime(new Date());

                        replies.add(reply);
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return replies;
    }

    private static void doPushGooglePlusUrls() {
        String filename = "out.txt";
        Set<String> googleUrls = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    googleUrls.add(temp);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (String googleUrl : googleUrls) {
            try {
                String ret = TCrawler.getContentFromUrl("http://192.168.6.81:1125/priority/?url=" + URLEncoder.encode(googleUrl, "UTF-8") + "&isForced=true");
                LOG.info(ret);
            } catch (UnsupportedEncodingException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    private static void doGetGooglePlusIds() {

        String PROXY_HOSTNAME = "192.168.4.13";
        int PROXY_PORT = 3128;
        final Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOSTNAME, PROXY_PORT));

        while (true) {
            List<String> youtubeUrls = new ArrayList<>();
            String filename = "list-youtube-urls.txt";
            try {
                try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String temp = line.trim();
                        youtubeUrls.add(temp);
                    }
                }
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            Collections.shuffle(youtubeUrls);
            final ConcurrentHashMap<String, String> mapGooglePlusIds = new ConcurrentHashMap<>();

            ThreadPoolExecutor excutor = new ThreadPoolExecutor(40, 40, 1, TimeUnit.DAYS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(1000));
            for (final String youtubeUrl : youtubeUrls) {
                try {
                    excutor.execute(new Runnable() {

                        @Override
                        public void run() {
                            String videoId = getVideoIdFromUrl(youtubeUrl);
                            LOG.info("video id: {}", videoId);
                            Set<String> retIds = parseGooglePlusIds(videoId, proxy);
                            for (String retId : retIds) {
                                mapGooglePlusIds.put(retId, retId);
                            }
                        }
                    });
                } catch (RejectedExecutionException ex) {
                    while (excutor.getQueue().size() > 100) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ex1) {
                            LOG.error(ex1.getMessage(), ex1);
                        }
                    }
                }
            }

            try {
                Thread.sleep(60 * 1000);
            } catch (InterruptedException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            excutor.shutdown();

            try {
                while (!excutor.awaitTermination(60, TimeUnit.MINUTES)) {
                }
            } catch (InterruptedException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            LOG.info("DONE 1.");

            Set<String> googlePlusIds = new HashSet<>();

            for (Map.Entry<String, String> entrySet : mapGooglePlusIds.entrySet()) {
                String key = entrySet.getKey();
                String value = entrySet.getValue();
                googlePlusIds.add(key);
            }

            if (googlePlusIds.isEmpty()) {
                LOG.info("EMPTY list");
                return;
            }

            try {
                File outFile = new File("out.txt");
                if (!outFile.exists()) {
                    outFile.createNewFile();
                }

                FileWriter fw = new FileWriter(outFile.getAbsoluteFile());
                try (BufferedWriter bw = new BufferedWriter(fw)) {
                    for (String googlePlusId : googlePlusIds) {
                        String rwurl = "https://plus.google.com/app/basic/" + googlePlusId;
                        String ret = TCrawler.getContentFromUrl("http://192.168.6.81:1125/priority/?url=" + URLEncoder.encode(rwurl, "UTF-8") + "&isForced=false");
                        LOG.info("{} - {}", ret, rwurl);
                        bw.write(rwurl);
                        bw.write("\n");
                    }
                }

                LOG.info("DONE!!");

            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            try {
                Thread.sleep(2 * 60 * 1000);
            } catch (InterruptedException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }

    }

    private static Set<String> parseGooglePlusIds(String videoId, Proxy proxy) {

        Set<String> googlePlusIds = new HashSet<>();

        List<String> urlToCrawl = new ArrayList<>();
        String urlGetVideoCommentsFirstTime = generateURLVideoComments(videoId);
        urlToCrawl.add(urlGetVideoCommentsFirstTime);
        boolean isFirst = true;

        while (urlToCrawl.size() > 0) {
            String nextUrl = urlToCrawl.remove(0);
            HttpResponseInfo responseGetVideoCommentsFirstTime = Utils.singleGet(nextUrl, proxy);
            int status = responseGetVideoCommentsFirstTime.getStatus();
            if (status == 200) {
                try {
                    String resVideoCommentsFirst = responseGetVideoCommentsFirstTime.getBody();
                    JsonElement jelement = new com.google.gson.JsonParser().parse(resVideoCommentsFirst);
                    JsonObject jobject = jelement.getAsJsonObject();
                    JsonObject jfeed = jobject.getAsJsonObject("feed");

                    if (isFirst) {
                        JsonObject jupdated = jfeed.getAsJsonObject("updated");
                        String updatedTime = jupdated.get("$t").getAsString();
                        LOG.info("updated time: {}", updatedTime);
                    }

                    // parse comments and get replies of comments
                    JsonArray jentry = jfeed.getAsJsonArray("entry");
                    for (int i = 0; i < jentry.size(); i++) {
                        try {
                            JsonObject itemEntry = jentry.get(i).getAsJsonObject();
                            JsonObject jytGooglePlusUserId = itemEntry.getAsJsonObject("yt$googlePlusUserId");
                            String ytGooglePlusUserId = jytGooglePlusUserId.get("$t").getAsString();
                            googlePlusIds.add(ytGooglePlusUserId);
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    }

                    // crawl next link to get full 100 comments
                    JsonArray jlink = jfeed.getAsJsonArray("link");
                    for (int i = 0; i < jlink.size(); i++) {
                        JsonObject itemLink = jlink.get(i).getAsJsonObject();
                        String rel = itemLink.get("rel").getAsString();
                        if (StringUtils.equalsIgnoreCase(rel, "next")) {
                            String nextLink = itemLink.get("href").getAsString();
                            LOG.info("next link: {}", nextLink);
                            if (isFirst) {
                                urlToCrawl.add(nextLink);
                                LOG.info("next link: ADDED");
                                // if want to get all comments of video, comment the line below.
                                // isFirst = false;
                            }
                            break;
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            } else {
                LOG.info("VIDEO COMMENTS 1st - Status is {}", status);
            }
        }

        return googlePlusIds;
    }

    private static List<VideoComment> parseVideoComments(String videoId, Proxy proxy) {

        List<VideoComment> videoComments = new ArrayList<>();
        List<String> urlToCrawl = new ArrayList<>();
        String urlGetVideoCommentsFirstTime = generateURLVideoComments(videoId);
        urlToCrawl.add(urlGetVideoCommentsFirstTime);
        boolean isFirst = true;

        while (urlToCrawl.size() > 0) {
            String nextUrl = urlToCrawl.remove(0);
            HttpResponseInfo responseGetVideoCommentsFirstTime = Utils.singleGet(nextUrl, proxy);
            int status = responseGetVideoCommentsFirstTime.getStatus();
            if (status == 200) {
                String resVideoCommentsFirst = responseGetVideoCommentsFirstTime.getBody();
                JsonElement jelement = new com.google.gson.JsonParser().parse(resVideoCommentsFirst);
                JsonObject jobject = jelement.getAsJsonObject();
                JsonObject jfeed = jobject.getAsJsonObject("feed");

                if (isFirst) {
                    JsonObject jupdated = jfeed.getAsJsonObject("updated");
                    String updatedTime = jupdated.get("$t").getAsString();
                    LOG.info("updated time: {}", updatedTime);
                }

                // parse comments and get replies of comments
                JsonArray jentry = jfeed.getAsJsonArray("entry");
                for (int i = 0; i < jentry.size(); i++) {
                    JsonObject itemEntry = jentry.get(i).getAsJsonObject();

                    JsonObject jId = itemEntry.getAsJsonObject("id");
                    String jIdStr = jId.get("$t").getAsString();
                    String commentId = "";
                    String regex = "(:comment:)(.+)";
                    Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
                    Matcher matcher = pattern.matcher(jIdStr);
                    while (matcher.find()) {
                        commentId = matcher.group(2);
                        LOG.info("comment id: {}", commentId);
                    }
                    JsonObject jPublished = itemEntry.getAsJsonObject("published");
                    String jPublishedStr = jPublished.get("$t").getAsString();

                    JsonObject jContent = itemEntry.getAsJsonObject("content");
                    String content = jContent.get("$t").getAsString();

                    // info of author and chanel
                    JsonObject jytChannelId = itemEntry.getAsJsonObject("yt$channelId");
                    String ytChannelId = jytChannelId.get("$t").getAsString();
                    JsonObject jytGooglePlusUserId = itemEntry.getAsJsonObject("yt$googlePlusUserId");
                    String ytGooglePlusUserId = jytGooglePlusUserId.get("$t").getAsString();

                    // get reply count
                    JsonObject jytReplyCount = itemEntry.getAsJsonObject("yt$replyCount");
                    int replyCount = jytReplyCount.get("$t").getAsInt();

                    // push to big data
                    VideoComment videoComment = new VideoComment();

                    if (StringUtils.isNotEmpty(commentId)) {
                        videoComment.setCommentId(commentId);
                        videoComment.setVideoId(videoId);
                        Date publishedTime = convertTime(jPublishedStr);
                        videoComment.setPublishedTime(publishedTime);
                        videoComment.setContent(content);
                        videoComment.setChannelId(ytChannelId);
                        videoComment.setGooglePlusUserId(ytGooglePlusUserId);
                        videoComment.setReplyCount(String.valueOf(replyCount));
                        videoComment.setCrawledTime(new Date());
                    }

                    // parse reply of this comment
                    if (replyCount > 0 && StringUtils.isNotEmpty(commentId)) {
                        List<ReplyComment> replies = parseRepliesOfComment(commentId, proxy);
                        videoComment.setListReplies(replies);
                    }
                    videoComments.add(videoComment);
                }

                // crawl next link to get full 100 comments
                JsonArray jlink = jfeed.getAsJsonArray("link");
                for (int i = 0; i < jlink.size(); i++) {
                    JsonObject itemLink = jlink.get(i).getAsJsonObject();
                    String rel = itemLink.get("rel").getAsString();
                    if (StringUtils.equalsIgnoreCase(rel, "next")) {
                        String nextLink = itemLink.get("href").getAsString();
                        LOG.info("next link: {}", nextLink);
                        if (isFirst) {
                            urlToCrawl.add(nextLink);
                            LOG.info("next link: ADDED");
                            // if want to get all comments of video, comment this line.
                            // isFirst = false;
                        }
                        break;
                    }
                }
            } else {
                LOG.info("VIDEO COMMENTS 1st - Status is {}", status);
            }
        }
        return videoComments;
    }

    private static VideoInfo parseVideoInfo(String videoId, Proxy proxy) {
        String urlGetVideoInfo = generateURLVideoInfo(videoId);
        HttpResponseInfo responseGetVideoInfo = Utils.singleGet(urlGetVideoInfo, proxy);
        int status = responseGetVideoInfo.getStatus();
        if (status == 200) {
            String resVideoInfoStr = responseGetVideoInfo.getBody();
            JsonElement jelement = new com.google.gson.JsonParser().parse(resVideoInfoStr);
            JsonObject jobject = jelement.getAsJsonObject();
            JsonObject jentry = jobject.getAsJsonObject("entry");

            JsonObject jmediaGroup = jentry.getAsJsonObject("media$group");
            JsonObject jmediaDescription = jmediaGroup.getAsJsonObject("media$description");
            String description = jmediaDescription.get("$t").getAsString();
            JsonObject jmediaTitle = jmediaGroup.getAsJsonObject("media$title");
            String title = jmediaTitle.get("$t").getAsString();
            JsonObject jytDuration = jmediaGroup.getAsJsonObject("yt$duration");
            String duration = jytDuration.get("seconds").getAsString();
            JsonObject jytUploaded = jmediaGroup.getAsJsonObject("yt$uploaded");
            String uploaded = jytUploaded.get("$t").getAsString();
            JsonObject jytUploaderId = jmediaGroup.getAsJsonObject("yt$uploaderId");
            String uploaderId = jytUploaderId.get("$t").getAsString();

            JsonObject jytstatistics = jentry.getAsJsonObject("yt$statistics");
            String viewCount = jytstatistics.get("viewCount").getAsString();
            JsonObject jytrating = jentry.getAsJsonObject("yt$rating");
            String numDislikes = jytrating.get("numDislikes").getAsString();
            String numLikes = jytrating.get("numLikes").getAsString();

            LOG.info("\ndescription: {} \ntitle: {} \nduration: {} \nuploaded: {} \nuploaderId: {} \nviewCount: {} \nnumDisLikes: {} \nnumLikes {}",
                    new Object[]{description, title, duration, uploaded, uploaderId, viewCount, numDislikes, numLikes});

            if (StringUtils.isNotEmpty(videoId)) {
                VideoInfo videoInfo = new VideoInfo();
                videoInfo.setId(videoId);
                videoInfo.setTitle(title);
                videoInfo.setDescription(description);
                videoInfo.setDuration(duration);
                Date uploadedTime = convertTime(uploaded);
                videoInfo.setUploadedTime(uploadedTime);
                videoInfo.setUploaderId(uploaderId);
                videoInfo.setViewCount(viewCount);
                videoInfo.setNumDislikes(numDislikes);
                videoInfo.setNumLikes(numLikes);
                videoInfo.setCrawledTime(new Date());
                return videoInfo;
            }
        } else {
            LOG.info("VIDEO INFO - Status is {}", status);
        }
        return null;
    }

    private static Date convertTime(String input) {
        try {
            String pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
            SimpleDateFormat sdf = new SimpleDateFormat(pattern);
            Date date = sdf.parse(input);
            return date;
        } catch (ParseException ex) {
            LOG.error(ex.getMessage(), ex);
            return null;
        }
    }

    private static class YtVideoInfo {

        public static final byte[] VIDEO_ID = "vid".getBytes();
        public static final byte[] TITLE = "vtt".getBytes();
        public static final byte[] DESCRIPTION = "vdc".getBytes();
        public static final byte[] DURATION = "vdr".getBytes();
        public static final byte[] UPLOADED_TIME = "pt".getBytes();
        public static final byte[] UPLOADER_ID = "vuid".getBytes();
        public static final byte[] VIEW_COUNT = "vvc".getBytes();
        public static final byte[] NUM_DISLIKES = "vndl".getBytes();
        public static final byte[] NUM_LIKES = "vnl".getBytes();
        public static final byte[] CREATE_TIME = "ct".getBytes();
        public static final byte[] UPDATE_TIME = "ut".getBytes();
    }

    private static class YtVideoComment {

        public static final byte[] COMMENT_ID = "cid".getBytes();
        public static final byte[] VIDEO_ID = "vid".getBytes();
        public static final byte[] PUBLISHED_TIME = "pt".getBytes();
        public static final byte[] CONTENT = "ct".getBytes();
        public static final byte[] CHANNEL_ID = "ccid".getBytes();
        public static final byte[] GOOGLE_PLUS_USER_ID = "cgpid".getBytes();
        public static final byte[] REPLY_COUNT = "crc".getBytes();
        public static final byte[] CREATE_TIME = "ct".getBytes();
        public static final byte[] UPDATE_TIME = "ut".getBytes();
    }

    private static class YtReplyComment {

        public static final byte[] REPLY_ID = "rid".getBytes();
        public static final byte[] COMMENT_ID = "cid".getBytes();
        public static final byte[] PUBLISHED_TIME = "pt".getBytes();
        public static final byte[] GOOGLE_PLUS_USER_ID = "cgpid".getBytes();
        public static final byte[] CONTENT = "ct".getBytes();
        public static final byte[] REPLY_URL = "url".getBytes();
        public static final byte[] CREATE_TIME = "ct".getBytes();
        public static final byte[] UPDATE_TIME = "ut".getBytes();
    }
}
