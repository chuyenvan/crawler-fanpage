package viettel.nfw.social.facebook.nologin;

import com.viettel.nfw.im.facebookparser.FacebookParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.gfw.vn.producer.producer.MessageInfo;
import viettel.gfw.vn.producer.producer.ProducerORMWeb;
import viettel.gfw.vn.social.FaceBookProto;
import viettel.gfw.vn.social.FaceBookProtoTrans;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.utils.AsyncFileWriter;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class RunNoLogin {

    private static final Logger LOG = LoggerFactory.getLogger(RunNoLogin.class);

    private static final String CONF_FILE_PATH = "conf/app-crawler-facebook-nologin.properties";
    private static final int MAX_CAPACITY = 3000000;
    private static final String PREFIX_CRAWLER_THREAD = "Crawler_";

    public static final String USER_AGENT_DF = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:33.0) Gecko/20100101 Firefox/33.0";
    public static final String USERNAME_DF = "NO-LOGIN";
    public static final String FORMAT_DATA_FILENAME = "%s_%s.data";
    public static final String FORMAT_CRAWLED_URLS_FILENAME = "crawled-url_%s.txt";
    public static final String DIR_STORAGE = "storage/webnologin/";
    public static final String DIR_CRAWLED = "result/crawled/";

    public static String proxyString = null;
    public static int numberPhantomjsDriver = 5; // default is 5 phantomjs drivers
    public static long phantomjsTimeToLive = 4 * 60 * 60 * 1000; // default is 4 hours
    public static ConcurrentHashMap<String, String> allCrawledUrls = new ConcurrentHashMap<>();
    public static BlockingQueue<String> crawledUrlsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
    public static BlockingQueue<String> toCrawlUrl = new ArrayBlockingQueue<>(MAX_CAPACITY);
    public static BlockingQueue<String> toSendOutlinks = new ArrayBlockingQueue<>(MAX_CAPACITY);
    public static BlockingQueue<String> crawledDataQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);

    public static void main(String[] args) {

        if (!checkConfig()) {
            return;
        }

        initCrawledUrls();
        LOG.info("Loaded crawled urls");

        GetOutlinksImpl getOutlinksImpl = new GetOutlinksImpl();
        new Thread(getOutlinksImpl).start();

        WriteCrawledUrlsImpl writerImple = new WriteCrawledUrlsImpl();
        new Thread(writerImple).start();

        SendOutlinksImpl sendOutlinksImpl = new SendOutlinksImpl();
        new Thread(sendOutlinksImpl).start();

        ParsePushCrawledDataImpl parsePushCrawledDataImpl = new ParsePushCrawledDataImpl();
        new Thread(parsePushCrawledDataImpl).start();

        doJob(proxyString);
    }

    private static boolean checkConfig() {
        boolean isOK = true;

        // check required things
        File confFile = new File(CONF_FILE_PATH);
        if (!confFile.exists()) {
            isOK = false;
            LOG.info("Check config file: {} - {}", CONF_FILE_PATH, "FAILED");
        } else {
            LOG.info("Check config file: {} - {}", CONF_FILE_PATH, "OK");
        }

        // check dir storage is existed
        try {
            File storageDir = new File(DIR_STORAGE);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }
            if (storageDir.exists()) {
                LOG.info("Check storage {} - {}", DIR_STORAGE, "OK");
            } else {
                isOK = false;
                LOG.info("Check storage {} - {}", DIR_STORAGE, "FAILED");
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
            isOK = false;
            LOG.info("Check storage {} - {}", DIR_STORAGE, "FAILED");
        }

        // check dir result
        try {
            File crawledDir = new File(DIR_CRAWLED);
            if (!crawledDir.exists()) {
                crawledDir.mkdirs();
            }
            if (crawledDir.exists()) {
                LOG.info("Check crawled dir {} - {}", DIR_CRAWLED, "OK");
            } else {
                isOK = false;
                LOG.info("Check crawled dir {} - {}", DIR_CRAWLED, "FAILED");
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
            isOK = false;
            LOG.info("Check crawled dir {} - {}", DIR_CRAWLED, "FAILED");
        }

        if (isOK) {
            // read config file
            try {
                ApplicationConfiguration.getInstance().initilize(CONF_FILE_PATH);

                String proxyMode = ApplicationConfiguration.getInstance().getConfiguration("proxy");
                if (StringUtils.equals(proxyMode, "true")) {
                    String proxyHostname = ApplicationConfiguration.getInstance().getConfiguration("proxy.hostname");
                    String proxyPortStr = ApplicationConfiguration.getInstance().getConfiguration("proxy.port");
                    proxyString = proxyHostname + ":" + proxyPortStr;
                }

                String numberPhantomjsStr = ApplicationConfiguration.getInstance().getConfiguration("phantomjs.thread");
                numberPhantomjsDriver = Integer.valueOf(numberPhantomjsStr);

                String timeToLiveStr = ApplicationConfiguration.getInstance().getConfiguration("phantomjs.timetolive");
                phantomjsTimeToLive = Long.valueOf(timeToLiveStr) * 60 * 60 * 1000;

            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
                isOK = false;
            }
        }

        return isOK;
    }

    private static void doJob(String proxyString) {
        for (int i = 0; i < numberPhantomjsDriver; i++) {
            String threadName = PREFIX_CRAWLER_THREAD + i;
            WorkerThread worker = new WorkerThread(proxyString, USER_AGENT_DF, threadName);
            new Thread(worker).start();
        }
    }

    private static void initCrawledUrls() {
        toCrawlUrl.add("https://www.facebook.com/giaitrionline");
        toCrawlUrl.add("https://www.facebook.com/cuhiep");
        toCrawlUrl.add("https://www.facebook.com/thientoi2");
        try {
            File folder = new File(DIR_CRAWLED);
            File[] listOfFiles = folder.listFiles();
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    LOG.info("File {}", file.getAbsolutePath());

                    List<String> urls = new ArrayList<>();
                    try {
                        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                String temp = line.trim();
                                urls.add(temp);
                            }
                        }
                    } catch (IOException ex) {
                        LOG.error("Failed to read " + file.getAbsolutePath() + " file", ex);
                    }

                    for (String url : urls) {
                        allCrawledUrls.put(url, url);
                    }
                } else if (file.isDirectory()) {
                    LOG.warn("Directory {}", file.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static class WriteCrawledUrlsImpl implements Runnable {

        public static AsyncFileWriter afwCrawledUrls;

        @Override
        public void run() {
            Thread.currentThread().setName("WriteCrawledUrlsImpl");
            try {
                String filename = DIR_CRAWLED
                        + String.format(FORMAT_CRAWLED_URLS_FILENAME, String.valueOf(System.currentTimeMillis()));
                afwCrawledUrls = new AsyncFileWriter(new File(filename));
                afwCrawledUrls.open();
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            while (true) {
                try {
                    String url = crawledUrlsQueue.poll();
                    if (StringUtils.isEmpty(url)) {
                        Thread.sleep(1000);
                    } else {
                        allCrawledUrls.put(url, url);
                        afwCrawledUrls.append(url + "\n");
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private static class GetOutlinksImpl implements Runnable {

        @Override
        public void run() {
            Thread.currentThread().setName("GetOutlinksImpl");
            while (true) {
                try {
                    if (toCrawlUrl.size() < 5) {
                        for (int i = 0; i < 5; i++) {
                            String queryWWWUrl = ServiceOutlinks.getNextUrl("www.facebook.com", USERNAME_DF);
                            if (StringUtils.isNotEmpty(queryWWWUrl)) {
                                toCrawlUrl.add(queryWWWUrl);
                            }
                            String queryViVnUrl = ServiceOutlinks.getNextUrl("vi-vn.facebook.com", USERNAME_DF);
                            if (StringUtils.isNotEmpty(queryViVnUrl)) {
                                toCrawlUrl.add(queryViVnUrl);
                            }
                        }
                    } else {
                        Thread.sleep(60 * 1000);
                    }
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private static class SendOutlinksImpl implements Runnable {

        @Override
        public void run() {
            Thread.currentThread().setName("SendOutlinksImpl");
            while (true) {
                try {
                    String url = toSendOutlinks.poll();
                    if (StringUtils.isEmpty(url)) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    } else {
                        LOG.info("Outlink - {}", url);
                        WorkerThread.FacebookUrlType urlType = WorkerThread.filterUrl(url, null);
                        if (urlType.equals(WorkerThread.FacebookUrlType.PROFILE)) {
                            String normUrl = WorkerThread.normProfileUrl(url);
                            if (!RunNoLogin.allCrawledUrls.containsKey(normUrl)) {
                                LOG.info("Added - {}", normUrl);
                                ServiceOutlinks.addOutLink(RunNoLogin.USERNAME_DF, normUrl, normUrl);
                            }
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }

    }

    private static class ParsePushCrawledDataImpl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(ParsePushCrawledDataImpl.class);
        private static final ProducerORMWeb producer = new ProducerORMWeb("orm_web");

        @Override
        public void run() {
            Thread.currentThread().setName("ParsePushCrawledDataImpl");
            while (true) {
                try {
                    String pageSource = crawledDataQueue.poll();
                    if (StringUtils.isEmpty(pageSource)) {
                        Funcs.sleep(700);
                    } else {
//                        com.viettel.nfw.im.facebookparser.FacebookParser fbParser = new FacebookParser();
//                        FacebookObject fbObject = fbParser.parseHtmlToObjectForWebNoLogin(pageSource);
//                        if (fbObject != null) {
//                            MessageInfo message = new MessageInfo();
//                            FaceBookProto.FBSocialObject fbSocialObject = FaceBookProtoTrans.facebookObjectTo(fbObject);
//                            message.setDataSocial(fbSocialObject, MessageInfo.MESSAGE_TYPE_SOCIAL_FACEOOK);
//                            producer.sendMessageORMWeb(message);
//                            Funcs.sleep(700);
//                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }

    }

}
