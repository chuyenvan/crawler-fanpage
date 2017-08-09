package viettel.nfw.forums;

import java.net.URI;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.ocpsoft.prettytime.nlp.PrettyTimeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.utils.TCrawler;
import vn.viettel.utils.SerializeObjectUtils;

/**
 *
 * @author duongth5
 */
public class ForumProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(ForumProcessor.class);

    private final String rootUrl;
    private String template;
    private ForumsRepository forumsRepository;

    public ForumProcessor(String rootUrl, String template, ForumsRepository forumsRepository) {
        this.rootUrl = rootUrl;
        this.template = template;
        this.forumsRepository = forumsRepository;
    }

    public List<String> listPages(int numberPage) {
        List<String> pages = new ArrayList<>();
        pages.add(rootUrl);
        for (int i = 2; i <= numberPage; i++) {
            String nextUrl = String.format(template, i);
            pages.add(nextUrl);
        }

        return pages;
    }

    public List<String> process(String pageUrl) {
        List<String> toCrawlUrls = new ArrayList<>();
        String response = TCrawler.getContentFromUrl(pageUrl);
        if (StringUtils.isNotEmpty(response)) {
            // parse
            try {
                URI baseUri = new URI(pageUrl);
                Document doc = Jsoup.parse(response);
                Elements threadElements = doc.select("table#threadslist > tbody#threadbits_forum_17 > tr");
                if (!threadElements.isEmpty()) {
                    for (Element threadElement : threadElements) {

                        ThreadInfo threadInfo = new ThreadInfo();
                        // do parsing
                        try {
                            Elements tds = threadElement.select("tr > td");
                            if (!tds.isEmpty()) {
                                int length = tds.size();
                                if (length == 5) {
                                    // get Thread id, get Thread paging
                                    Element titleEl = tds.get(1);
                                    Elements aTitles = titleEl.select("a[id^=thread_title_]");
                                    if (!aTitles.isEmpty()) {
                                        Element aTitle = aTitles.get(0);
                                        String href = aTitle.attr("href");
                                        URI threadUri = baseUri.resolve(href);
                                        threadInfo.threadUrl = threadUri.toString();
                                        Map<String, List<String>> map = Parser.splitQuery(threadUri);
                                        String threadId = map.get("t").get(0);
                                        if (StringUtils.isNotEmpty(threadId)) {
                                            threadInfo.threadId = threadId;
                                        } else {
                                            continue;
                                        }
                                    }
                                    int lastPageNum = 0;
                                    Elements aPagings = titleEl.select("span.smallfont > a[href]");
                                    if (!aPagings.isEmpty()) {
                                        Element threadLastPage = aPagings.last();
                                        String href = threadLastPage.attr("href");
                                        URI lastPageUri = baseUri.resolve(href);

                                        try {
                                            Map<String, List<String>> map = Parser.splitQuery(lastPageUri);
                                            String lastPageNumStr = map.get("page").get(0);
                                            lastPageNum = Integer.parseInt(lastPageNumStr);
                                        } catch (Exception e) {
                                            LOG.error(e.getMessage(), e);
                                        }
                                    }
                                    threadInfo.lastPage = lastPageNum;

                                    // get last post
                                    long lastUpdate;
                                    Element lastPostEl = tds.get(2);
                                    String lastPostText = lastPostEl.text();
                                    if (StringUtils.isNotEmpty(lastPostText)) {
                                        int indexBy = StringUtils.indexOf(lastPostText.toLowerCase(), "by");
                                        if (indexBy != -1) {
                                            lastPostText = StringUtils.substring(lastPostText.toLowerCase(), indexBy);
                                        }
                                        List<Date> date = new PrettyTimeParser().parse(lastPostText);
                                        if (date.isEmpty()) {
                                            lastUpdate = System.currentTimeMillis();
                                        } else {
                                            lastUpdate = date.get(0).getTime();
                                        }
                                        threadInfo.lastUpdate = lastUpdate;
                                    }

                                    // get number replies
                                    int numReplies = 0;
                                    Element numRepliesEl = tds.get(3);
                                    String numRepliesStr = numRepliesEl.text();
                                    if (StringUtils.isNotEmpty(numRepliesStr)) {
                                        try {
                                            numRepliesStr = StringUtils.replace(numRepliesStr, ",", "");
                                            numReplies = Integer.parseInt(numRepliesStr);
                                        } catch (Exception ex) {
                                            LOG.error(ex.getMessage(), ex);
                                        }
                                        threadInfo.numberReplies = numReplies;
                                    }

                                    // get number views
                                    int numViews = 0;
                                    Element numViewsEl = tds.get(4);
                                    String numViewsStr = numViewsEl.text();
                                    if (StringUtils.isNotEmpty(numViewsStr)) {
                                        try {
                                            numViewsStr = StringUtils.replace(numViewsStr, ",", "");
                                            numViews = Integer.parseInt(numViewsStr);
                                        } catch (Exception ex) {
                                            LOG.error(ex.getMessage(), ex);
                                        }
                                        threadInfo.numberViews = numViews;
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }

                        // do check condition
                        try {
                            String checkThreadId = threadInfo.threadId;
                            if (StringUtils.isNotEmpty(checkThreadId)) {
                                byte[] value = forumsRepository.get(checkThreadId.getBytes());
                                LOG.info("last page {} - {}", checkThreadId, threadInfo.lastPage);
                                if (value == null) {
                                    // write to db
                                    forumsRepository.write(checkThreadId.getBytes(), SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(threadInfo));
                                    // get toCrawlUrls
                                    int lastPage = threadInfo.lastPage;
                                    toCrawlUrls.add(threadInfo.threadUrl);
                                    if (lastPage != 0) {
                                        int numBackPage = 50;
                                        if (numBackPage > lastPage) {
                                            for (int i = 0; i <= lastPage; i++) {
                                                if (i != 0) {
                                                    String url = "http://vozforums.com/showthread.php?t=" + threadInfo.threadId + "&page=" + String.valueOf(i);
                                                    toCrawlUrls.add(url);
                                                }
                                            }
                                        } else {
                                            for (int i = lastPage - numBackPage; i <= lastPage; i++) {
                                                if (i != 0) {
                                                    String url = "http://vozforums.com/showthread.php?t=" + threadInfo.threadId + "&page=" + String.valueOf(i);
                                                    toCrawlUrls.add(url);
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    // get toCrawlUrls
                                    ThreadInfo dbThreadInfo = (ThreadInfo) SerializeObjectUtils.readingObjectFromByteArrayUsingGzip(value);
                                    LOG.info("from db {}", dbThreadInfo);
                                    if (dbThreadInfo.lastUpdate < threadInfo.lastUpdate
                                            || dbThreadInfo.lastPage < threadInfo.lastPage
                                            || dbThreadInfo.numberReplies < threadInfo.numberReplies
                                            || dbThreadInfo.numberViews < threadInfo.numberViews) {
                                        for (int i = dbThreadInfo.lastPage; i <= threadInfo.lastPage; i++) {
                                            if (i != 0) {
                                                String url = "http://vozforums.com/showthread.php?t=" + threadInfo.threadId + "&page=" + String.valueOf(i);
                                                toCrawlUrls.add(url);
                                            }
                                        }
                                    }
                                    // update to db
                                    forumsRepository.write(checkThreadId.getBytes(), SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(threadInfo));
                                }
                            }
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }

        return toCrawlUrls;
    }
}
