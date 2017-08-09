package viettel.nfw.social.reviewdata;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.Pair;
import vn.viettel.utils.SimpleTimer;

/**
 *
 * @author duongth5
 */
public class QueryStorageDirs implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(QueryStorageDirs.class);
    private final Set<String> allProfileFilePaths = new HashSet<>();

    private String storagePath;
    private int storageType;
    private String threadName;

    public QueryStorageDirs(String storagePath, int storageType, String threadName) {
        this.storagePath = storagePath;
        this.storageType = storageType;
        this.threadName = threadName;
    }

    @Override
    public void run() {
        Thread.currentThread().setName(threadName);
        while (true) {

            querySocialStorage(storagePath, storageType);

            // sleep for 5 minutes
            try {
                Thread.sleep(3 * 60 * 1000);
            } catch (InterruptedException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    private void querySocialStorage(String storagePath, int type) {
        SimpleTimer timer = new SimpleTimer();
        int countTotal = 0;
        int countNew = 0;
        try {
            File folder = new File(storagePath);
            File[] listOfFiles = folder.listFiles();
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    countTotal++;
                    LOG.debug("File {}", file.getAbsolutePath());
                    String filePath = file.getAbsolutePath();
                    if (allProfileFilePaths.contains(filePath)) {
                        // already contains
                    } else {
                        countNew++;
                        allProfileFilePaths.add(filePath);
                        Pair<String, Integer> pair = new Pair<>(filePath, type);
                        RunReviewData.bigQueue.add(pair);
                    }
                } else if (file.isDirectory()) {
                    LOG.warn("Directory {}", file.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
        LOG.info("Load folder {} - new {} files - total {} files in {} ms",
                new Object[]{storagePath, countNew, countTotal, timer.getTimeAndReset()});
    }

}
