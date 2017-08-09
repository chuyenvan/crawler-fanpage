package viettel.nfw.social.google.core;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.google.crawler.GooglePlusCrawlerJob;
import viettel.nfw.social.google.entity.ActivityHolder;
import viettel.nfw.social.google.entity.CrawledResult;
import viettel.nfw.social.google.entity.TimelineWrapper;

import viettel.nfw.social.model.googleplus.Activity;
import viettel.nfw.social.model.googleplus.Actor;
import viettel.nfw.social.model.googleplus.Comment;
import viettel.nfw.social.model.googleplus.GooglePlusObject;
import viettel.nfw.social.model.googleplus.Person;
import vn.itim.detector.InputType;
import vn.itim.detector.Language;
import vn.itim.detector.LanguageDetector;
import viettel.nfw.social.google.utils.GooglePlusError;
import viettel.nfw.social.google.utils.GooglePlusMessage;
import viettel.nfw.social.google.utils.GooglePlusURL;
import vn.viettel.social.utils.Utils;
import vn.viettel.social.utils.consts.SCommon;

/**
 * Parse Google Plus person detail info and post
 *
 * @author duongth5
 * @version 1.0
 * @since 1.0
 */
public class PersonParser {

    /**
     * Logger for PersonParser class
     */
    private static final Logger LOG = LoggerFactory.getLogger(PersonParser.class);

    private final String currentGoogleAccount;
    private final String currentUrl;
    private final String currentId;
    private final String currentType;
    private final long startTime;
    private final HttpRequest http;
    private final Proxy proxy;
    private final List<String> crawledUrls;
    private final Set<String> listFoundProfileURLs;
    private GooglePlusError gpError;
    private LanguageDetector languageDetector;

    public LanguageDetector getLanguageDetector() {
        return languageDetector;
    }

    public void setLanguageDetector(LanguageDetector languageDetector) {
        this.languageDetector = languageDetector;
    }

    public PersonParser(String accountGoogle, String url, String id, String type, long startTime, HttpRequest http, Proxy proxy) {

        this.currentGoogleAccount = accountGoogle;
        this.currentUrl = url;
        this.currentId = id;
        this.currentType = type;
        this.startTime = startTime;
        this.http = http;
        this.proxy = proxy;
        crawledUrls = new ArrayList<>();
        listFoundProfileURLs = new HashSet<>();
    }

    public CrawledResult parse(int showMorePost) throws InterruptedException {

        GooglePlusObject gpObj = new GooglePlusObject();
        CrawledResult result = new CrawledResult();
        try {

            this.crawledUrls.add(currentUrl);

            if (StringUtils.isEmpty(currentId)) {
                gpError = GooglePlusError.PARSER_ERROR_CURRENT_PROFILE_ID_EMPTY;
                result.setErrorCode(gpError.getCode());
                result.setErrorDescription(gpError.getDescription() + " - " + currentUrl);
                result.setAccountCrawl(currentGoogleAccount);
                result.setCrawledProfile(null);
                return result;
            }

            // process crawl this person
            // get this person information
            Person person = parsePersonInfo();
            // get this person timeline
            TimelineWrapper retTimeline = processTimeline(showMorePost);

            gpObj.setPersonInfo(person);
            gpObj.setActivities(retTimeline.getActivities());
            gpObj.setComments(retTimeline.getComments());

        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        } finally {

            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            LOG.info(GooglePlusMessage.PROCESS_PARSER_TOTAL_TIME, this.crawledUrls.size(), totalTime);

            result.setErrorCode(GooglePlusError.CRAWL_PROFILE_OK.getCode());
            result.setErrorDescription(GooglePlusError.CRAWL_PROFILE_OK.getDescription());
            result.setFoundProfileUrls(listFoundProfileURLs);
            result.setStartTime(startTime);
            result.setEndTime(endTime);
            result.setCrawledTime(totalTime);
            result.setAccountCrawl(currentGoogleAccount);
            result.setCrawledProfile(gpObj);
        }
        return result;
    }

    /**
     * Parse person information
     *
     * @throws InterruptedException
     */
    private Person parsePersonInfo() {

        Person person = new Person();
        try {
            String urlProfile = String.format(GooglePlusURL.FORMAT_PROFILE, currentId);
            String urlAbout = String.format(GooglePlusURL.FORMAT_ABOUT, currentId);

            if (!shouldCrawlUrl(urlAbout)) {
                return null;
            }
            this.crawledUrls.add(urlAbout);

            String crawlAboutJob = GooglePlusCrawlerJob.crawl(urlAbout, http, proxy);
            Document aboutDoc = Jsoup.parse(crawlAboutJob);

            person.setId(currentId);
            person.setUpdateTime(new Date());
            person.setCreateTime(new Date());

            person.setType(currentType);
            person.setUrl(urlProfile);

            Element div_data_owner = aboutDoc.select("div[data-owner]").get(0);
            if (div_data_owner.attr("data-owner").equals(currentId)) {

                // Get Fullname
                String fullName = div_data_owner.attr("data-ownername");
                LOG.debug("Get from div - id: {}", div_data_owner.attr("data-owner"));
                LOG.debug("Get from div - display name: {}", fullName);
                if (StringUtils.isNotEmpty(fullName)) {
                    person.setFullname(fullName);
                }

                // get gender
                String gender = div_data_owner.attr("data-ownergender");
                LOG.debug("Get from div - gender: {}", gender);
                if (StringUtils.isNotEmpty(gender)) {
                    person.setGender(gender);
                }
            }

            Elements info = aboutDoc.select("div > h3");
            if (!info.isEmpty()) {
                for (Element element : info) {

                    // get date of birth
                    if (element.text().equalsIgnoreCase("Date of birth")) {
                        String dob = element.parents().select("h3 ~ div").get(0).text();
                        LOG.debug("Get from div - birthday: {}", dob);
                        if (StringUtils.isNotEmpty(dob)) {
                            person.setBirthday(dob);
                        }
                    }

                    // get currently
                    if (element.text().equalsIgnoreCase("Currently")) {
                        String currentLocation = element.parents().select("h3 ~ div").get(0).text();
                        LOG.debug("Get from div - current location: {}", currentLocation);
                        if (StringUtils.isNotEmpty(currentLocation)) {
                            person.setCurrentLocation(currentLocation);
                        }
                    }
                }
            }

            Elements infoPeople = aboutDoc.select("div > h1");
            if (!infoPeople.isEmpty()) {
                for (Element element : infoPeople) {

                    // get People section
                    if (element.text().equalsIgnoreCase("People")) {
                        Elements divs = element.parent().select("div");
                        for (Element div : divs) {
                            if (StringUtils.contains(div.ownText(), "in circles")) {
                                String inOtherCircles = div.select("span").get(0).text();
                                long count = Long.valueOf(inOtherCircles);
                                if (count != 0) {
                                    person.setFollowersCount(count);
                                }
                            }
                        }
                    }
                }
            }
        } catch (NumberFormatException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        return person;
    }

    /**
     * Crawl Timeline of this profile
     */
    private TimelineWrapper processTimeline(int showMorePost) {

        String urlPosts = String.format(GooglePlusURL.FORMAT_POSTS, currentId);

        // extract post id then process it
        int countMorePost = 0;
        List<String> morePostsUrlToCrawls = new ArrayList<>();
        morePostsUrlToCrawls.add(urlPosts);

        List<DumpWrapper> listDumpWrappers = new ArrayList<>();
        List<ActivityHolder> listActivityHolders = new ArrayList<>();
        while (morePostsUrlToCrawls.size() > 0) {
            String nextUrl = morePostsUrlToCrawls.remove(0);
            if (!shouldCrawlUrl(nextUrl)) {
                continue; // skip this URL
            }
            this.crawledUrls.add(nextUrl);

            LOG.debug("Object: {} --- post url: {}", currentId, nextUrl);

            String crawlPostsJob = GooglePlusCrawlerJob.crawl(nextUrl, http, proxy);
            Document postsDoc = Jsoup.parse(crawlPostsJob);

            // extract postId
            Elements div_cardscontainer_postItem = postsDoc.select("div#cardscontainer > div[data-itemid]");
            for (Element postItem : div_cardscontainer_postItem) {
                String postId = postItem.attr("data-itemid");
                LOG.debug("find post id: {}", postId);
                // do crawl post
                DumpWrapper dumpWrapper = processActivity(postId);
                listDumpWrappers.add(dumpWrapper);
            }

            // get Show more link
            Elements morePostsElement = postsDoc.select("a:contains(More posts)");
            if (morePostsElement.size() > 0) {
                // int showMorePost = Utils.getIntValue("gp.post.showmore", 0);
                String strLink = morePostsElement.get(0).attr("href");
                String morePostsUrl = GooglePlusURL.BASE_URL + strLink;
                LOG.info("More posts url: " + morePostsUrl);
                if (countMorePost < showMorePost) {
                    morePostsUrlToCrawls.add(morePostsUrl);
                }
                countMorePost++;
            }
        }

        int countVietnames = 0;
        Set<String> listIds = new HashSet<>();
        for (DumpWrapper dumpWrapper : listDumpWrappers) {
            listActivityHolders.add(dumpWrapper.activityHolder);
            try {
                if (!dumpWrapper.outlinkIds.isEmpty()) {
                    listIds.addAll(dumpWrapper.outlinkIds);
                }
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
            if (dumpWrapper.isVietnamese) {
                countVietnames++;
            }
        }
        if (countVietnames > 0) {
            LOG.info("countVietnamese {}", countVietnames);
            for (String listId : listIds) {
                addFoundProfileUrl(listId);
            }
        }

        // collect data to return
        TimelineWrapper timelineWrapper = new TimelineWrapper();
        List<Activity> activities = new ArrayList<>();
        List<Comment> comments = new ArrayList<>();
        for (ActivityHolder holder : listActivityHolders) {
            activities.add(holder.getActivityInfo());
            comments.addAll(holder.getComments());
        }
        timelineWrapper.setActivities(activities);
        timelineWrapper.setComments(comments);
        return timelineWrapper;
    }

    private static class DumpWrapper {

        public ActivityHolder activityHolder;
        public Set<String> outlinkIds;
        public boolean isVietnamese;
    }

    /**
     * Crawl post detail
     *
     * @param postId
     */
    private DumpWrapper processActivity(String postId) {

        boolean isVietnamese = false;
        Set<String> listFoundIds = new HashSet<>();

        String urlStream = String.format(GooglePlusURL.FORMAT_STREAM, postId);
        String urlPostActivities = String.format(GooglePlusURL.FORMAT_STREAM_ACTIVITES, postId);

        if (!shouldCrawlUrl(urlStream)) {
            // skip this URL
            return null;
        }
        this.crawledUrls.add(urlStream);
        LOG.debug("Object: " + postId + " --- stream url: " + urlStream);

        try {
            // get page of activity
            String crawlStream = GooglePlusCrawlerJob.crawl(urlStream, http, proxy);
            Document streamDoc = Jsoup.parse(crawlStream);

            Activity activity = new Activity();
            activity.setId(postId);
            activity.setUpdateTime(new Date());
            activity.setCreateTime(new Date());
            activity.setUrl(urlStream);

            // get user who post this
            Actor ownerPost = new Actor();
            Elements userElement = streamDoc.select("div[data-updateid] > div:eq(1) > div:eq(1) > div > div > a");
            if (!userElement.isEmpty()) {
                String link = GooglePlusURL.BASE_URL + userElement.get(0).attr("href");
                URL url = new URL(link);

                Pattern pattern = Pattern.compile("^(/app/basic/)(\\d++)(/posts)$");
                Matcher matcher = pattern.matcher(url.getPath());
                if (matcher.matches()) {
                    ownerPost.setDisplayName(userElement.get(0).text());
                    ownerPost.setId(matcher.group(2));
                    ownerPost.setUrl(String.format(GooglePlusURL.FORMAT_PROFILE, matcher.group(2)));
//                    if (isVietnamese) {
//                        addFoundProfileUrl(matcher.group(2));
//                    }
                    if (StringUtils.isNotEmpty(matcher.group(2))) {
                        listFoundIds.add(matcher.group(2));
                    }

                    activity.setOwnerProfile(ownerPost);
                }
            }

            // get time
            Elements createdTimeElement = streamDoc.select("div[data-updateid] > div:eq(1) > div:eq(1) > div > div > div");
            if (!createdTimeElement.isEmpty()) {
                String nlTime;
                if (createdTimeElement.size() < 2) {
                    nlTime = createdTimeElement.get(0).ownText();
                } else {
                    nlTime = createdTimeElement.get(1).ownText();
                }
                Date parseDate = Utils.humanTimeParser(nlTime);
                activity.setPostTime(parseDate);
            }

            // get content
            Elements contentElement = streamDoc.select("span[data-itemid=" + postId + "]");
            if (!contentElement.isEmpty()) {
                String content = contentElement.get(0).text();
                LOG.debug("activity content: {}", content);
                activity.setContent(content);
                if (StringUtils.isNotEmpty(content.trim())) {
                    try {
                        Language language = getLanguageDetector().detect(content, null, InputType.PLAIN);
                        LOG.info("LANGUAGE {} - url {}", language.toString(), urlStream);
                        if (language == Language.VIETNAMESE || language == Language.MIXED) {
                            isVietnamese = true;
                        }
                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
            }

            // Temporaty story raw HTML to field TYPE
            Elements rawHtmlElement = streamDoc.select("div[data-updateid]");
            if (!rawHtmlElement.isEmpty()) {
                String rawHtml = rawHtmlElement.get(0).outerHtml();
                LOG.debug("rawHtml size: {}", rawHtml.length());
                activity.setType(rawHtml);
            }

            // get comments (extract personId)
            Elements commentsElement = streamDoc.select("span[data-itemid^=" + postId + "#]");
            List<Comment> comments = new ArrayList<>();
            if (!commentsElement.isEmpty()) {
                for (Element element : commentsElement) {

                    Comment comment = new Comment();
                    String commentId = element.attr("data-itemid");
                    comment.setId(commentId);
                    comment.setUpdateTime(new Date());
                    comment.setCreatedTime(new Date());
                    comment.setActivityId(postId);
                    comment.setContent(element.text());
                    LOG.debug("comment content: {}", comment.getContent());
                    comment.setUrl(String.format(GooglePlusURL.FORMAT_COMMENT, URLEncoder.encode(element.attr("data-itemid"), SCommon.CHARSET_UTF_8)));

                    // check if have translate
                    Elements div_translate = element.parent().select("div > a:contains(Translate)");
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
                    Actor userComment = new Actor();
                    if (!userCommentElement.isEmpty()) {
                        String link = GooglePlusURL.BASE_URL + userCommentElement.get(0).attr("href");
                        URL url = new URL(link);
                        Pattern pattern = Pattern.compile("^(/app/basic/)(\\d++)(/posts)$");
                        Matcher matcher = pattern.matcher(url.getPath());
                        if (matcher.matches()) {
                            userComment.setDisplayName(userCommentElement.get(0).text());
                            userComment.setId(matcher.group(2));
                            userComment.setUrl(String.format(GooglePlusURL.FORMAT_PROFILE, matcher.group(2)));
//                            if (isVietnamese) {
//                                addFoundProfileUrl(matcher.group(2));
//                            }
                            if (StringUtils.isNotEmpty(matcher.group(2))) {
                                listFoundIds.add(matcher.group(2));
                            }
                            comment.setUser(userComment);
                        }
                    }

                    // get time of comment
                    if (!timeCommentElement.isEmpty()) {
                        String createdTime = timeCommentElement.get(0).ownText();
                        LOG.info("comment - created time: " + createdTime);
                        Date parseDate = Utils.humanTimeParser(createdTime);
                        comment.setCommentTime(parseDate);
                    }

                    // push to big data
                    comments.add(comment);
                }
            }
            long commentsCount = comments.size();
            activity.setCommentsCount(commentsCount);

            // get post activities to get +1 and reshare (extract personId)
            LOG.info("Post activities url: " + urlPostActivities);
            this.crawledUrls.add(urlPostActivities);
            String crawlPostActivities = GooglePlusCrawlerJob.crawl(urlPostActivities, http, proxy);
            Document postActivitiesDoc = Jsoup.parse(crawlPostActivities);
            List<Actor> plusOnes = new ArrayList<>();
            List<Actor> reShares = new ArrayList<>();

            Elements activities = postActivitiesDoc.select("div#PAGE > div:eq(3) > div");
            for (Element element : activities) {
                String text = element.ownText();
                if (text.contains("+1'd")) {
                    Actor actorPlusOne = new Actor();
                    Elements actorPlusOneElement = element.select("a[href]");
                    if (!actorPlusOneElement.isEmpty()) {
                        String link = GooglePlusURL.BASE_URL + actorPlusOneElement.get(0).attr("href");
                        URL url = new URL(link);
                        Pattern pattern = Pattern.compile("^(/app/basic/)(\\d++)(/posts)$");
                        Matcher matcher = pattern.matcher(url.getPath());
                        if (matcher.matches()) {
                            actorPlusOne.setDisplayName(actorPlusOneElement.get(0).text());
                            actorPlusOne.setId(matcher.group(2));
                            actorPlusOne.setUrl(String.format(GooglePlusURL.FORMAT_PROFILE, matcher.group(2)));
//                            if (isVietnamese) {
//                                addFoundProfileUrl(matcher.group(2));
//                            }
                            if (StringUtils.isNotEmpty(matcher.group(2))) {
                                listFoundIds.add(matcher.group(2));
                            }
                            plusOnes.add(actorPlusOne);
                        }
                    }
                }

                if (text.contains("reshared")) {
                    Actor actorReshare = new Actor();
                    Elements actorPlusOneElement = element.select("a[href]");
                    if (!actorPlusOneElement.isEmpty()) {
                        String link = GooglePlusURL.BASE_URL + actorPlusOneElement.get(0).attr("href");
                        URL url = new URL(link);
                        Pattern pattern = Pattern.compile("^(/app/basic/)(\\d++)(/posts)$");
                        Matcher matcher = pattern.matcher(url.getPath());
                        if (matcher.matches()) {
                            actorReshare.setDisplayName(actorPlusOneElement.get(0).text());
                            actorReshare.setId(matcher.group(2));
                            actorReshare.setUrl(String.format(GooglePlusURL.FORMAT_PROFILE, matcher.group(2)));
//                            if (isVietnamese) {
//                                addFoundProfileUrl(matcher.group(2));
//                            }
                            if (StringUtils.isNotEmpty(matcher.group(2))) {
                                listFoundIds.add(matcher.group(2));
                            }
                            reShares.add(actorReshare);
                        }
                    }
                }
            }

            activity.setListPeoplePlusOne(plusOnes);
            activity.setListPeopleReshare(reShares);

            long plusOnesCount = plusOnes.size();
            long resharesCount = reShares.size();
            activity.setPlusOnesCount(plusOnesCount);
            activity.setResharesCount(resharesCount);

            ActivityHolder activityHolder = new ActivityHolder();
            activityHolder.setActivityInfo(activity);
            activityHolder.setComments(comments);

            DumpWrapper dumpWrapper = new DumpWrapper();
            dumpWrapper.activityHolder = activityHolder;
            dumpWrapper.isVietnamese = isVietnamese;
            try {
                if (!listFoundIds.isEmpty()) {
                    dumpWrapper.outlinkIds = listFoundIds;
                }
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }

            return dumpWrapper;
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
        return null;
    }

    private boolean shouldCrawlUrl(String nextUrl) {
        return !this.crawledUrls.contains(nextUrl);
    }

    private void addFoundProfileUrl(String profileId) {
        String urlProfile = String.format(GooglePlusURL.FORMAT_PROFILE, profileId);
        listFoundProfileURLs.add(urlProfile);
    }
}
