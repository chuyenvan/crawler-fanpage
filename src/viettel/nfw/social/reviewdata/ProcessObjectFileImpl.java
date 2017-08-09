package viettel.nfw.social.reviewdata;

import com.viettel.naviebayes.ClassifierResult;
import com.viettel.naviebayes.NaiveBayes;
import com.viettel.nfw.im.facebookparser.FacebookParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.controller.ControllerReporter;
import viettel.nfw.social.controller.SensitiveProfile;
import viettel.nfw.social.model.facebook.Comment;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.model.facebook.Post;
import viettel.nfw.social.model.googleplus.Activity;
import viettel.nfw.social.model.googleplus.GooglePlusObject;
import viettel.nfw.social.model.twitter.Tweet;
import viettel.nfw.social.model.twitter.TwitterObject;
import viettel.nfw.social.utils.FileUtils;
import vn.viettel.social.fb.test.FbUrlToHtml;

/**
 *
 * @author duongth5
 */
public class ProcessObjectFileImpl implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessObjectFileImpl.class);
    private final String filePath;
    private final int filePathType;
    private static final Set<String> allBlackSites;
    private final ControllerReporter reporter;

    static {
        allBlackSites = new HashSet<>();
        try {
            allBlackSites.addAll(FileUtils.readList(new FileInputStream("data/black_sites/black_site_current.txt"), false));
        } catch (IOException ex) {
            LOG.error("Error in loading black sites {}", ex);
        }
    }

    public ProcessObjectFileImpl(String filePath, int filePathType, ControllerReporter reporter) {
        this.filePath = filePath;
        this.filePathType = filePathType;
        this.reporter = reporter;
    }

    public double calculateObjectScore(FacebookObject fbObject) throws Exception {
        StringBuilder fullContent = new StringBuilder();
        List<Post> posts = fbObject.getPosts();
        if (posts != null && !posts.isEmpty()) {
            for (Post post : posts) {
                fullContent.append(post.getContent()).append(" . ");
            }
            try {
                for (Comment comment : fbObject.getComments()) {
                    fullContent.append(comment.getContent()).append(" . ");
                }
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
            LOG.info("Full content: {}", fullContent.toString());
            ClassifierResult classifierByContentWithArticle = NaiveBayes.getInstance().getClassifierByContentWithUserMode(fullContent.toString(), -50.0f);
            if (classifierByContentWithArticle.score > 0) {
                return classifierByContentWithArticle.score;
            }
        }

        double score = 0.0;
        if (posts != null && !posts.isEmpty()) {
            for (Post post : posts) {
                String content = post.getContent();
                if (content != null) {
//                    double additional = Math.max(CommentClassifier.getInstance().classiferComment(content).score,
//                            NaiveBayes.getInstance().getClassifierComment(content).score) * 0.3;
//                    LOG.info("{} = {}", content.replaceAll("\n", " "), additional);
//                    score += additional;
                }
            }
            if (score > 2.0) {
                score = 2.0;
            }
        }

        return score;
    }

    public double calculateWebObjectScore(FacebookObject fbObject) throws Exception {
        double score = 0.0;
        for (Post post : fbObject.getPosts()) {
            String content = post.getContent();
            if (StringUtils.isNotEmpty(content)) {
                ClassifierResult classifierByContentWithArticle = NaiveBayes.getInstance().getClassifierByContentWithUserMode(content, -50.0f);
                score += classifierByContentWithArticle.score;
            }
        }

        return score;
    }

    public double calculateObjectScore(GooglePlusObject gpObject) throws Exception {
        StringBuilder fullContent = new StringBuilder();
        for (Activity activity : gpObject.getActivities()) {
            fullContent.append(activity.getContent()).append(" . ");
        }
        ClassifierResult classifierByContentWithArticle = NaiveBayes.getInstance().getClassifierByContentWithUserMode(fullContent.toString(), -50.0f);
        if (classifierByContentWithArticle.score > 0) {
            return classifierByContentWithArticle.score;
        }
        double score = 0.0;
        for (Activity activity : gpObject.getActivities()) {
            String content = activity.getContent();
            if (content != null) {

            }
        }
        if (score > 2.0) {
            score = 2.0;
        }
        return score;
    }

    public double calculateObjectScore(TwitterObject twObject) throws Exception {
        StringBuilder fullContent = new StringBuilder();
        for (Tweet tweet : twObject.getTweets()) {
            fullContent.append(tweet.getText()).append(" . ");
        }
        ClassifierResult classifierByContentWithArticle = NaiveBayes.getInstance().getClassifierByContentWithUserMode(fullContent.toString(), -50.0f);
        if (classifierByContentWithArticle.score > 0) {
            return classifierByContentWithArticle.score;
        }
        double score = 0.0;
        for (Tweet tweet : twObject.getTweets()) {
            String content = tweet.getText();
            if (content != null) {

            }
        }
        if (score > 2.0) {
            score = 2.0;
        }
        return score;
    }

    /**
     * calculate outlink score . detect what's host
     *
     * @param aTags
     * @param base
     * @return
     */
    private double getOutLinkScore(Elements aTags, URI base) {
        double linkScore = 0.0;
        for (Element aTag : aTags) {
            try {
                String href = aTag.attr("href");
                if (href == null) {
                    continue;
                }
                URI uri = base.resolve(href);
                String host = uri.getHost();
                if (allBlackSites.contains(host)) {
                    linkScore++;
                } else {
                    String url = uri.toString();
                    if (!url.contains("facebook.com/l.php?")) {
                        continue;
                    }
                    String sharedUrl = Parser.splitQuery(uri.toURL()).get("u").get(0);
                    String sharedHost = new URI(sharedUrl).getHost().toLowerCase();
                    if (allBlackSites.contains(sharedHost)) {
                        linkScore++;
                    }
                    LOG.info("{} shared {}", base.toString(), sharedUrl);
                }
            } catch (Exception ex) {
                LOG.warn("Error in resolving {}", aTag.html());
            }
        }
        return linkScore;
    }

    @Override
    public void run() {
        File file = new File(filePath);
        String filename = file.getName();
        Thread.currentThread().setName(filename);

        switch (filePathType) {
            case 1:
                processFacebookObject(file);
                break;
            case 2:
                processWebFacebookObj(file);
                break;
            case 3:
                processGoogleObject(file);
                break;
            case 4:
                processTwitterObject(file);
                break;
        }

    }

    private void processFacebookObject2(File file) {
        LOG.debug("Start File {}", file.getAbsolutePath());
        try {
            List<FbUrlToHtml> htmls = (List<FbUrlToHtml>) FileUtils.readObjectFromFile(file, false);
            if (!htmls.isEmpty()) {
                FbUrlToHtml firstRecord = htmls.get(0);
                String firstRecordRawHtml = firstRecord.getRawHtml();
                Document firstRecordDoc = Jsoup.parse(firstRecordRawHtml);
                String firstRecordTitle = firstRecordDoc.title().trim();
                String firstRecordUrl = firstRecord.getRawUrl();
                long firstRecordTime = firstRecord.getCrawledTime();

                if (StringUtils.isEmpty(firstRecordTitle) || StringUtils.equals(firstRecordTitle, "Facebook")) {
                    LOG.info("FAILED url {} - time {}", firstRecordUrl, new Date(firstRecordTime).toString());
                } else {
                    double score = 0.0;
                    double objectScore = 0.0;
                    try {
                        // Page or Group
                        FacebookObject facebookObject2 = ParsingUtils.fromHtmltoFacebookObject(htmls);
                        objectScore = calculateObjectScore(facebookObject2);
                        if (objectScore > 0) {
                            score = objectScore;
                        }

                    } catch (Exception ex) {
                        LOG.error("Error in scoring facebook object {}", htmls.get(0).getRawUrl());
                    }
                }
            } else {
                LOG.info("File {} is empty", file.getAbsolutePath());
            }

        } catch (Exception ex) {
            LOG.error("Cannot read file " + file.getAbsolutePath() + "\n" + ex.getMessage(), ex);
        }
        LOG.debug("End File {}", file.getAbsolutePath());
    }

    private void processGoogleObject(File file) {
        LOG.debug("Start File {}", file.getAbsoluteFile());
        try {
            RunReviewData.parsedFilesQueue.add(file.getAbsolutePath());
            GooglePlusObject gpObj = (GooglePlusObject) FileUtils.readObjectFromFile(file, false);
            if (gpObj != null) {
                double score = 0.0;
                double objectScore = 0.0;
                try {
                    // Page or Group
                    objectScore = calculateObjectScore(gpObj);
                    if (objectScore > 0) {
                        score = objectScore;
                    }

                } catch (Exception ex) {
                    LOG.error("Error in scoring google plus object {}", gpObj.getPersonInfo().getUrl());
                }

                for (Activity activity : gpObj.getActivities()) {
                    String rawHtml = activity.getType();
                    if (StringUtils.isEmpty(rawHtml)) {

                    } else {
                        try {
                            Document doc = Jsoup.parse(rawHtml);
                            Elements allOutLinks = doc.getElementsByTag("a");
                            score += getOutLinkScore(allOutLinks, new URI(gpObj.getPersonInfo().getUrl()));
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    }
                }

                if (score > 0) {
                    LOG.info("OK url {} - score {}", gpObj.getPersonInfo().getUrl(), score);
                    addSensitiveUrl(gpObj.getPersonInfo().getUrl(), score);
                }
            }
        } catch (IOException | ClassNotFoundException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        LOG.debug("End File {}", file.getAbsoluteFile());
    }

    private void processFacebookTitle(File file) {
        try {
            List<FbUrlToHtml> htmls = (List<FbUrlToHtml>) FileUtils.readObjectFromFile(file, false);
            if (!htmls.isEmpty()) {
                FbUrlToHtml firstRecord = htmls.get(0);
                String firstRecordRawHtml = firstRecord.getRawHtml();
                Document firstRecordDoc = Jsoup.parse(firstRecordRawHtml);
                String firstRecordTitle = firstRecordDoc.title().trim();
                String firstRecordUrl = firstRecord.getRawUrl();
                long firstRecordTime = firstRecord.getCrawledTime();

                if (StringUtils.isEmpty(firstRecordTitle)
                        || StringUtils.equals(firstRecordTitle, "Facebook")
                        || StringUtils.contains(firstRecordTitle, "Access Restricted")
                        || StringUtils.contains(firstRecordTitle, "Truy cập bị hạn chế")) {
                    LOG.info("FAILED url {} - title {} - time {}", new Object[]{firstRecordUrl, firstRecordTitle, new Date(firstRecordTime).toString()});
                    // addErrorUrl(firstRecordUrl);
                    // addErrorFile(file.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            LOG.error("Cannot read file " + file.getAbsolutePath() + "\n" + ex.getMessage(), ex);
        }
    }

    private void processFacebookObject(File file) {
        LOG.debug("Start File {}", file.getAbsolutePath());
        try {
            RunReviewData.parsedFilesQueue.add(file.getAbsolutePath());
            List<FbUrlToHtml> htmls = (List<FbUrlToHtml>) FileUtils.readObjectFromFile(file, false);
            if (!htmls.isEmpty()) {
                FbUrlToHtml firstRecord = htmls.get(0);
                String firstRecordRawHtml = firstRecord.getRawHtml();
                Document firstRecordDoc = Jsoup.parse(firstRecordRawHtml);
                String firstRecordTitle = firstRecordDoc.title().trim();
                String firstRecordUrl = firstRecord.getRawUrl();
                long firstRecordTime = firstRecord.getCrawledTime();

                if (StringUtils.isEmpty(firstRecordTitle) || StringUtils.equals(firstRecordTitle, "Facebook")) {
                    LOG.info("FAILED url {} - time {}", firstRecordUrl, new Date(firstRecordTime).toString());
                } else {
                    double score = 0.0;
                    double objectScore = 0.0;
                    try {
                        // User or Page or Group
                        FacebookObject facebookObject2 = ParsingUtils.fromHtmltoFacebookObject(htmls);
                        objectScore = calculateObjectScore(facebookObject2);
                        if (objectScore > 0) {
                            score = objectScore;
                        }

                    } catch (Exception ex) {
                        LOG.error("Error in scoring facebook object {}", htmls.get(0).getRawUrl());
                    }
                    double linksScore = 0.0;
                    for (FbUrlToHtml html : htmls) {
                        String rawHtml = html.getRawHtml();
                        Document doc = Jsoup.parse(rawHtml);
                        Elements allOutLinks = doc.getElementsByTag("a");
                        linksScore += getOutLinkScore(allOutLinks, new URI(html.getRawUrl()));
                        score += getOutLinkScore(allOutLinks, new URI(html.getRawUrl()));
//                        if (objectScore < 0.1) {
//                            try {
//                                Elements mains = doc.select("div#objects_container");
//                                if (!mains.isEmpty()) {
//                                    Element main = mains.get(0);
//                                    main.select("a:contains(Timeline)").remove();
//                                    main.select("a:contains(About)").remove();
//                                    main.select("a:contains(Photos)").remove();
//                                    main.select("a:contains(Likes)").remove();
//                                    main.select("a:contains(Following)").remove();
//                                    main.select("a:contains(Friends)").remove();
//                                    main.select("a:contains(Message)").remove();
//                                    main.select("a:contains(Like)").remove();
//                                    main.select("a:contains(Comment)").remove();
//                                    main.select("a:contains(Share)").remove();
//                                    main.select("a:contains(Full Story)").remove();
//                                    main.select("a:contains(Show more)").remove();
//                                    main.select("a:contains(View Full Size)").remove();
//                                    main.select("a:contains(Attach a Photo)").remove();
//                                    main.select("a:contains(Mention Friends)").remove();
//                                    main.select("a:contains(See Friendship)").remove();
//                                    main.select("a:contains(Poke)").remove();
//                                    main.select("a:contains(Block this person)").remove();
//                                    main.select("a:contains(Report)").remove();
//                                    main.select("a:contains(SuggestFriends)").remove();
//                                    main.select("a:contains(Unfriend)").remove();
//                                    main.select("a:contains(Unfollow)").remove();
//                                    main.select("a:contains(See Friend Lists)").remove();
//                                    main.select("a:contains(Add Friend)").remove();
//                                    main.select("a:contains(mutual friend)").remove();
//                                    main.select("a:contains(See More Friends)").remove();
//                                    String content = main.text();
//                                    LOG.debug("content {}", content);
//                                    ClassifierResult result = NaiveBayes.getInstance().getClassifierByContentWithKeepEverything(content);
//                                    double i = result.score;
//                                    score += i;
//                                }
//                            } catch (Exception ex) {
//                                LOG.error("Error in classifing with dummy mode");
//                            }
//                        }
                    }
                    LOG.debug("OK url {} - score {}", firstRecordUrl, score);
                    LOG.info("Score - file {} - post {} - links {}", new Object[]{file.getName(), score, linksScore});
                    if (score > 0) {
                        LOG.info("OK url {} - score {}", firstRecordUrl, score);
                        addSensitiveUrl(firstRecordUrl, score);
                    }
                }

            } else {
                LOG.info("File {} is empty", file.getAbsolutePath());
            }

        } catch (Exception ex) {
            LOG.error("Cannot read file " + file.getAbsolutePath() + "\n" + ex.getMessage(), ex);
            try {
                FacebookObject fbObject = (FacebookObject) FileUtils.readObjectFromFile(file, false);
                double score = 0.0;
                double objectScore = 0.0;
                objectScore = calculateObjectScore(fbObject);
                if (objectScore > 0) {
                    score = objectScore;
                }
                if (score > 0) {
                    LOG.info("OK url {} - score {}", fbObject.getInfo().getUrl(), score);
                    addSensitiveUrl(fbObject.getInfo().getUrl(), score);
                }
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        }
        LOG.debug("End File {}", file.getAbsolutePath());
    }

    private void processTwitterObject(File file) {
        LOG.debug("Start File {}", file.getAbsoluteFile());
        try {
            RunReviewData.parsedFilesQueue.add(file.getAbsolutePath());
            TwitterObject twObj = (TwitterObject) FileUtils.readObjectFromFile(file, false);
            if (twObj != null) {
                String profileUrl = twObj.getProfiles().get(0).getScreenName();
                if (StringUtils.isNotEmpty(profileUrl)) {
                    if (!StringUtils.startsWith(profileUrl, "https://")) {
                        profileUrl = "https://mobile.twitter.com/" + profileUrl;
                    }
                    double score = 0.0;
                    try {
                        score = calculateObjectScore(twObj);
                        if (score > 0) {
                            LOG.info("OK url {} - score {}", profileUrl, score);
                            addSensitiveUrl(profileUrl, score);
                        }
                    } catch (Exception ex) {
                        LOG.error("Error in scoring twitter object {}", profileUrl);
                    }
                }
            }
        } catch (IOException | ClassNotFoundException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        LOG.debug("End File {}", file.getAbsoluteFile());
    }

    private void processWebFacebookObj(File file) {
        try {
            RunReviewData.parsedFilesQueue.add(file.getAbsolutePath());
            com.viettel.nfw.im.facebookparser.FacebookParser fbParser = new FacebookParser();
            FacebookObject fbObject = fbParser.parseFileToObject(file);

            if (fbObject == null) {
                LOG.error("File failed - NULL: {}", filePath);
                return;
            }

            double score = 0.0;
            try {
                score = calculateObjectScore(fbObject);

                List<String> sharedUrls = new ArrayList<>();
                for (Post post : fbObject.getPosts()) {
                    try {
                        String outsideUrl = post.getOutsideUrl();
                        if (StringUtils.isNotEmpty(outsideUrl)) {
                            sharedUrls.add(outsideUrl);
                        }
                    } catch (Exception e) {
                    }
                }
                double linkScore = 0.0;
                for (String sharedUrl : sharedUrls) {
                    URI uri;
                    try {
                        uri = new URI(sharedUrl);
                    } catch (Exception ex) {
                        continue;
                    }
                    String host = uri.getHost();
                    if (allBlackSites.contains(host)) {
                        linkScore++;
                    }
                }
                LOG.info("Score - file {} - post {} - links {}", new Object[]{file.getName(), score, linkScore});
                score += linkScore;
                if (score > 0) {
                    LOG.info("OK url {} - score {}", fbObject.getInfo().getUrl(), score);
                    addSensitiveUrl(fbObject.getInfo().getUrl(), score);
                }
            } catch (Exception ex) {
                LOG.error("Error in scoring facebook object {}", fbObject.getInfo().getUrl());
                LOG.error(ex.getMessage(), ex);
            }
        } catch (Exception ex) {
            LOG.error("failed: {}", filePath);
            LOG.error(ex.getMessage(), ex);
        }

    }

    public void addSensitiveUrl(String url, double score) {
        try {
            String scoreStr = String.valueOf(score);
            String norm = normalizeProfileUrl(new URI(url));
            String normUrl;
            if (StringUtils.isEmpty(norm)) {
                normUrl = url;
            } else {
                String[] parts = StringUtils.split(norm, "|");
                normUrl = parts[1];
            }

            if (StringUtils.isNotEmpty(normUrl)) {
                // change m.facebook.com to facebook.com when send to controller
                String webUrl = StringUtils.replaceEach(url,
                        new String[]{"//m.facebook.com/", "//www.facebook.com/", "//vi-vn.facebook.com", "//plus.google.com/app/basic/", "//mobile.twitter.com/"},
                        new String[]{"//facebook.com/", "//facebook.com/", "//facebook.com/", "//plus.google.com/", "//twitter.com/"});

                RunReviewData.sensitiveQueue.add(webUrl + "\t" + scoreStr);

                SensitiveProfile sp = new SensitiveProfile(webUrl, score);
                LOG.info("JSON {}", JSON.encode(sp));
                reporter.offer(sp);
            }

        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
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
            if (path.startsWith("/pages/")) {
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

}
