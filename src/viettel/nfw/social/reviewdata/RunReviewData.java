package viettel.nfw.social.reviewdata;

import com.viettel.naviebayes.NaiveBayes;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.controller.ControllerReporter;
import viettel.nfw.social.utils.AsyncFileWriter;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.utils.CustomizedFixedThreadPool;

/**
 *
 * @author duongth5
 */
public class RunReviewData {

    private static final Logger LOG = LoggerFactory.getLogger(RunReviewData.class);

    private static final String TOPIC_NAME = "Black_Profile";
    private static final String CONF_FILE_PATH = "conf/app-review-data.properties";
    private static final int QUEUE_CAPACITY = 3000000;

    public static String storageFacebookMobilePath = null; // type 1
    public static String storageFacebookWebPath = null; // type 2
    public static String storageGooglePlusPath = null; // type 3
    public static String storageTwitterPath = null; // type 4

    public static ConcurrentHashMap<String, String> allParsedFiles = new ConcurrentHashMap<>();
    public static BlockingQueue<String> parsedFilesQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    public static BlockingQueue<Pair<String, Integer>> bigQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    public static BlockingQueue<String> sensitiveQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);

    public static final String DIR_PARSED = "result/reviewdata/parsed/";
    public static final String DIR_SENSITIVE = "result/reviewdata/sensitive/";
    public static final String FORMAT_PARSED_FILENAME = "parsed_files_%s.txt";
    public static final String FORMAT_SENSITIVE_FILENAME = "sensitive_profile_%s.txt";

    public static String activeMQip = "10.30.154.103";
    public static int activeMQport = 61616;

    public static ControllerReporter reporter;

    public static void main(String[] args) throws Exception {

        init();

        // Sleep for a while
        Funcs.sleep(2000);

        WriteParsedFilesImpl writeParsedFilesImpl = new WriteParsedFilesImpl();
        new Thread(writeParsedFilesImpl).start();

        WriteSensitiveProfileImpl writeSensitiveImpl = new WriteSensitiveProfileImpl();
        new Thread(writeSensitiveImpl).start();

        // Sleep for a while
        Funcs.sleep(2000);

        // init NaiveBayes
        NaiveBayes.getInstance();
        LOG.info("Finished getting naive bayes instance");

        // query list folders
        QueryStorageDirs queryFbMobileJob = new QueryStorageDirs(RunReviewData.storageFacebookMobilePath, 1, "QueryStorageFacebookMobile");
        new Thread(queryFbMobileJob).start();

        QueryStorageDirs queryFbWebJob = new QueryStorageDirs(RunReviewData.storageFacebookWebPath, 2, "QueryStorageFacebookWeb");
        new Thread(queryFbWebJob).start();

        QueryStorageDirs queryGoogleJob = new QueryStorageDirs(RunReviewData.storageGooglePlusPath, 3, "QueryStorageGoogle");
        new Thread(queryGoogleJob).start();

        QueryStorageDirs queryTwitterJob = new QueryStorageDirs(RunReviewData.storageTwitterPath, 4, "QueryStorageTwitter");
        new Thread(queryTwitterJob).start();
        //END

        // start thread reporter
        reporter = ControllerReporter.getDefault(activeMQip, activeMQport, null, null, TOPIC_NAME);
        reporter.start();

        // Sleep for a while
        Funcs.sleep(2000);

        FixedThreadParse fixedThreadParse = new FixedThreadParse();
        new Thread(fixedThreadParse).start();
    }

    private static class FixedThreadParse implements Runnable {

        @Override
        public void run() {
            Thread.currentThread().setName("FixedThreadParse");
            CustomizedFixedThreadPool pool = new CustomizedFixedThreadPool(50, 50000, "Review");
            while (true) {
                try {
                    Pair<String, Integer> objectFile = bigQueue.poll();
                    if (objectFile == null) {
                        // do nothing
                        Funcs.sleep(15 * 1000);
                    } else {
                        String objectFilePath = objectFile.first;
                        int objectFileType = objectFile.second;
                        if (StringUtils.isEmpty(objectFilePath)) {
                            // do nothing
                            Funcs.sleep(15 * 1000);
                        } else {
                            pool.execute(new ProcessObjectFileImpl(objectFilePath, objectFileType, reporter));
                        }
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }

    }

    private static void init() {
        // init configuration
        try {
            ApplicationConfiguration.getInstance().initilize(CONF_FILE_PATH);

            storageFacebookMobilePath = ApplicationConfiguration.getInstance().getConfiguration("storage.facebook.mobile");
            LOG.info("Loaded config: {}", storageFacebookMobilePath);
            storageFacebookWebPath = ApplicationConfiguration.getInstance().getConfiguration("storage.facebook.webnologin");
            LOG.info("Loaded config: {}", storageFacebookWebPath);
            storageGooglePlusPath = ApplicationConfiguration.getInstance().getConfiguration("storage.googleplus.mobile");
            LOG.info("Loaded config: {}", storageGooglePlusPath);
            storageTwitterPath = ApplicationConfiguration.getInstance().getConfiguration("storage.twitter.mobile");
            LOG.info("Loaded config: {}", storageTwitterPath);

            activeMQip = ApplicationConfiguration.getInstance().getConfiguration("activemq.ip");
            activeMQport = Integer.parseInt(ApplicationConfiguration.getInstance().getConfiguration("activemq.port"));

        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

        // init parsed files
        try {
            File folder = new File(DIR_PARSED);
            File[] listOfFiles = folder.listFiles();
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    LOG.info("File {}", file.getAbsolutePath());

                    List<String> parsedFiles = new ArrayList<>();
                    try {
                        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                String temp = line.trim();
                                parsedFiles.add(temp);
                            }
                        }
                    } catch (IOException ex) {
                        LOG.error("Failed to read " + file.getAbsolutePath() + " file", ex);
                    }

                    for (String url : parsedFiles) {
                        allParsedFiles.put(url, url);
                    }
                } else if (file.isDirectory()) {
                    LOG.warn("Directory {}", file.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static class WriteParsedFilesImpl implements Runnable {

        public static AsyncFileWriter afwParsedFiles;

        @Override
        public void run() {
            Thread.currentThread().setName("WriteParsedFilesImpl");
            try {
                String filename = DIR_PARSED
                        + String.format(FORMAT_PARSED_FILENAME, String.valueOf(System.currentTimeMillis()));
                afwParsedFiles = new AsyncFileWriter(new File(filename));
                afwParsedFiles.open();
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            while (true) {
                try {
                    String filePath = parsedFilesQueue.poll();
                    if (StringUtils.isEmpty(filePath)) {
                        Thread.sleep(1000);
                    } else {
                        allParsedFiles.put(filePath, filePath);
                        afwParsedFiles.append(filePath + "\n");
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private static class WriteSensitiveProfileImpl implements Runnable {

        public static AsyncFileWriter afwSensitiveFiles;

        @Override
        public void run() {
            Thread.currentThread().setName("WriteSensitiveProfileImpl");
            try {
                String filename = DIR_SENSITIVE
                        + String.format(FORMAT_SENSITIVE_FILENAME, String.valueOf(System.currentTimeMillis()));
                afwSensitiveFiles = new AsyncFileWriter(new File(filename));
                afwSensitiveFiles.open();
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            while (true) {
                try {
                    String url = sensitiveQueue.poll();
                    if (StringUtils.isEmpty(url)) {
                        Thread.sleep(1000);
                    } else {
                        afwSensitiveFiles.append(url + "\n");
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }

    }

}
