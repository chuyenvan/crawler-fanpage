package viettel.nfw.forums;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.planner.ManualRequest;

/**
 *
 * @author chuyennd
 */
public class SendCrawlerService {

    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(SendCrawlerService.class);
    private static List<ManualRequest> listMr = new ArrayList<>();
    private static int totalMr;
    private static String[] listHostCrawler;
    private static String[] listPortCrawler;
    public static final long TIME_HOUR = 60 * 60 * 1000;

    static {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Thread.currentThread().setName("FlusingCrawler");
                while (true) {
                    // tao ket noi voi Crawler
                    String hostCrawler = "203.113.152.23,203.113.152.25,203.113.152.25,203.113.152.1,203.113.152.2,203.113.152.3,203.113.152.4,203.113.152.5";
                    String portCrawler = "50002,50002,50002,50002,50002,50002,50002,50002";
                    listHostCrawler = hostCrawler.split(",");
                    listPortCrawler = portCrawler.split(",");
                    if (!"".equals(hostCrawler)) {
                        for (int i = 0; i < listPortCrawler.length; i++) {
                            String host = listHostCrawler[i];
                            try {
                                int port = Integer.parseInt(listPortCrawler[i]);
                                listMr.add(new ManualRequest(host, port));
                                break;
                            } catch (Exception e) {
                            }
                        }
                    }
                    totalMr = listMr.size();
                    LOG.info("Thread sleep 24 hours.");
                    try {
                        Thread.sleep(24 * TIME_HOUR);
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }).start();
    }

    public static void sendCrawlerWithAServer(int index, String host, List<String> listSend) {
        boolean result = false;
        ManualRequest mr = listMr.get(index);
        result = mr.sendRequest(host, listSend);
        if (!result) {
            for (ManualRequest mrTemp : listMr) {
                result = mrTemp.sendRequest(host, listSend);
                if (result) {
                    break;
                }
            }
        }
    }

    public static boolean sendCrawler(String host, List<String> listUrl) {
        boolean result = false;
        int numCrawler = totalMr;
        int bucketSize = listUrl.size() / totalMr;
        for (int i = 0; i < numCrawler; i++) {
            List<String> forCrawler = listUrl.subList(i * bucketSize, (i == numCrawler - 1) ? listUrl.size() : (i + 1) * bucketSize);
            sendCrawlerWithAServer(i, host, forCrawler);
        }
        return result;

    }

    public static void main(String[] args) {

        String dbFile = "vozforum_threads.fb";
        ForumsRepository vozRepo = ForumsRepository.getInstance(dbFile);
        String rootUrl = "http://vozforums.com/forumdisplay.php?f=17";
        String template = "http://vozforums.com/forumdisplay.php?f=17&order=desc&page=%s";

        ForumProcessor processor = new ForumProcessor(rootUrl, template, vozRepo);
        while (true) {

            try {
                List<String> listPages = processor.listPages(10);
                for (String listPage : listPages) {
                    LOG.info("Start crawl url {}", listPage);
                    List<String> toCrawlUrls = processor.process(listPage);
                    LOG.info("Size of toCrawlUrls {}", toCrawlUrls.size());
                    LOG.info("To crawl urls {}", StringUtils.join(toCrawlUrls, ","));
                    sendCrawler("vozforums.com", toCrawlUrls);

                    long delay = Funcs.randInt(5 * 1000, 10 * 1000);
                    LOG.info("Sleep for {} ms", delay);
                    Funcs.sleep(delay);
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }

            long delayTurn = Funcs.randInt(12 * 60 * 1000, 16 * 60 * 1000);
            LOG.info("Sleep turn for {} ms", delayTurn);
            Funcs.sleep(delayTurn);
        }
    }
}
