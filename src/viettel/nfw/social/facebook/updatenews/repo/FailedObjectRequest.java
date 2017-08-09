package viettel.nfw.social.facebook.updatenews.repo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.RunUpdateNews;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import viettel.nfw.social.utils.AsyncFileWriter;

/**
 *
 * @author duongth5
 */
public class FailedObjectRequest {

    private static final Logger LOG = LoggerFactory.getLogger(FailedObjectRequest.class);

    private static final int MAX_CAPACITY = 500000;
    private static final int NUMBER_THREAD = 2;
    private static final String DIR_FAILED_OBJ = "database/failedobject/";
    private static final String FORMAT_FAILED_OBJ_FILENAME = "failed_obj_%s.txt";

    private static final BlockingQueue<Wrapper> failedObjectRequestsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
    private static final ConcurrentHashMap<String, String> failedObjectRequestsMap = new ConcurrentHashMap<>();
    private static AsyncFileWriter afwWriteFailedObjRequest;

    private ScheduledExecutorService executor = null;

    public static class Wrapper {

        public ObjectRequest objectRequest;
        public String message;

        public Wrapper(ObjectRequest objectRequest, String message) {
            this.objectRequest = objectRequest;
            this.message = message;
        }

    }

    public FailedObjectRequest() throws Exception {
        boolean isWriterOK = true;
        try {
            String filename = DIR_FAILED_OBJ
                    + String.format(FORMAT_FAILED_OBJ_FILENAME, String.valueOf(System.currentTimeMillis()));
            afwWriteFailedObjRequest = new AsyncFileWriter(new File(filename));
            afwWriteFailedObjRequest.open();
        } catch (IOException ex) {
            LOG.error("Error with FailedObjectRequest Writer", ex);
            isWriterOK = false;
        }

        executor = Executors.newScheduledThreadPool(NUMBER_THREAD);
        Reader reader = new Reader("FailedObjectRequest.Reader");
        executor.scheduleAtFixedRate(reader, 1, 5, TimeUnit.SECONDS);
        LOG.info("Started FailedObjectRequest Reader");

        if (isWriterOK) {
            Writer writer = new Writer("FailedObjectRequest.Writer");
            executor.scheduleAtFixedRate(writer, 1, 500, TimeUnit.MILLISECONDS);
            LOG.info("Started FailedObjectRequest Writer");
        }

    }

    public BlockingQueue<Wrapper> getFailedObjectRequestsQueue() {
        return failedObjectRequestsQueue;
    }

    public ConcurrentHashMap<String, String> getFailedObjectRequestsMap() {
        return failedObjectRequestsMap;
    }

    private static FailedObjectRequest instance = null;

    public static FailedObjectRequest getInstance() {
        try {
            return instance == null ? instance = new FailedObjectRequest() : instance;
        } catch (Exception ex) {
            return null;
        }
    }

    private static class Reader implements Runnable {

        private final String threadName;

        public Reader(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(threadName);
            try {
                File folder = new File(DIR_FAILED_OBJ);
                File[] listOfFiles = folder.listFiles();
                for (File file : listOfFiles) {
                    if (file.isFile()) {
                        // LOG.info("File {}", file.getAbsolutePath());
                        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                line = line.trim();
                                if (StringUtils.isEmpty(line)
                                        || StringUtils.startsWith(line, "#")) {
                                    continue;
                                }
                                String[] parts = StringUtils.split(line, "\t");
                                int length = parts.length;
                                if (length >= 3) {
                                    String socialTypeStr = parts[0];
                                    String objectIdStr = parts[1];
                                    String objectTypeStr = parts[2];
                                    String compositeKey = String.format(RunUpdateNews.FORMAT_COMPOSITE_KEY,
                                            socialTypeStr, objectIdStr, objectTypeStr);
                                    failedObjectRequestsMap.put(compositeKey, compositeKey);
                                } else {
                                    LOG.warn("This line has error format: {} in file {}", line, file.getAbsolutePath());
                                }
                            }
                        }
                    } else if (file.isDirectory()) {
                        LOG.warn("Directory {}", file.getAbsolutePath());
                    }
                }
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }

    }

    private static class Writer implements Runnable {

        private final String threadName;

        public Writer(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(threadName);
            Wrapper wrapper = failedObjectRequestsQueue.poll();
            if (wrapper != null) {
                try {
                    ObjectRequest objectRequest = wrapper.objectRequest;
                    String message = wrapper.message;
                    if (objectRequest != null && StringUtils.isNotEmpty(message)) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(objectRequest.socialType);
                        sb.append("\t");
                        sb.append(objectRequest.objectID);
                        sb.append("\t");
                        sb.append(objectRequest.objectType);
                        sb.append("\t");
                        sb.append(message);
                        afwWriteFailedObjRequest.append(sb.toString() + "\n");
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }

    }

}
