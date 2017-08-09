package viettel.nfw.social.facebook.nologin;

import com.viettel.nfw.im.facebookparser.FacebookParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.gfw.vn.producer.producer.MessageInfo;
import viettel.gfw.vn.producer.producer.ProducerORMWeb;
import viettel.gfw.vn.social.FaceBookProto;
import viettel.gfw.vn.social.FaceBookProtoTrans;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.utils.AsyncFileWriter;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class ParseAndPushDataImpl implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ParseAndPushDataImpl.class);

    private static final BlockingQueue<String> filesQueue = new ArrayBlockingQueue<>(3000000);
    private static final BlockingQueue<String> parsedfilesQueue = new ArrayBlockingQueue<>(3000000);

    public static BlockingQueue<FacebookObject> facebookObjectQueue = new ArrayBlockingQueue<>(3000000);

    public static void main(String[] args) {
        ParseAndPushDataImpl impl = new ParseAndPushDataImpl();
        new Thread(impl).start();
    }

    @Override
    public void run() {
//        try {
//            List<String> listFolder = new ArrayList<>();
//            listFolder.add("storage/webnologin");
//
//            QueryFiles queryFiles = new QueryFiles(listFolder);
//            new Thread(queryFiles).start();
//
//            PushToBigDataImpl impl = new PushToBigDataImpl();
//            new Thread(impl).start();
//
//            WriteParsedFilesImpl writerImpl = new WriteParsedFilesImpl();
//            new Thread(writerImpl).start();
//
//            ThreadPoolExecutor excutor = new ThreadPoolExecutor(10, 10, 1, TimeUnit.DAYS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(10000));
//            while (true) {
//                final String objectFilePath = filesQueue.poll();
//                if (StringUtils.isEmpty(objectFilePath)) {
//                    // do nothing
//                    LOG.info("Queue is empty");
//                    try {
//                        Thread.sleep(15 * 1000);
//                    } catch (InterruptedException ex) {
//                        LOG.error(ex.getMessage(), ex);
//                    }
//                } else {
//                    try {
//                        excutor.execute(new Runnable() {
//
//                            @Override
//                            public void run() {
//                                try {
//                                    com.viettel.nfw.im.facebookparser.FacebookParser fbParser = new FacebookParser();
//                                    FacebookObject fbObj = fbParser.parseFileToObjectForWebNoLogin(new File(objectFilePath));
//                                    parsedfilesQueue.add(objectFilePath);
//                                    LOG.info(JSON.encode(fbObj));
//                                    if (fbObj != null) {
//                                        facebookObjectQueue.add(fbObj);
//                                    }
//                                } catch (URISyntaxException ex) {
//                                    LOG.error(ex.getMessage(), ex);
//                                } catch (Exception ex) {
//                                    LOG.error(ex.getMessage(), ex);
//                                }
//                            }
//                        });
//                    } catch (RejectedExecutionException ex) {
//                        while (excutor.getQueue().size() > 500) {
//                            try {
//                                Thread.sleep(50);
//                            } catch (InterruptedException ex1) {
//                                LOG.error(ex1.getMessage(), ex1);
//                            }
//                        }
//                    }
//                }
//            }
//
//        } catch (Exception ex) {
//            LOG.error(ex.getMessage(), ex);
//        }
    }

    private static class PushToBigDataImpl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(PushToBigDataImpl.class);
        private static ProducerORMWeb producer = new ProducerORMWeb("orm_web");

        @Override
        public void run() {
            Thread.currentThread().setName("PushToBigDataImpl");
            Funcs.sleep(2000);
            while (true) {
                try {
                    FacebookObject fbObj = facebookObjectQueue.poll();
                    if (fbObj == null) {
                        Thread.sleep(500);
                    } else {
                        MessageInfo message = new MessageInfo();
                        FaceBookProto.FBSocialObject fbSocialObject = FaceBookProtoTrans.facebookObjectTo(fbObj);
                        message.setDataSocial(fbSocialObject, MessageInfo.MESSAGE_TYPE_SOCIAL_FACEOOK);
                        producer.sendMessageORMWeb(message);
                        Thread.sleep(600);
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private static class WriteParsedFilesImpl implements Runnable {

        public static AsyncFileWriter afwParsedFiles;

        @Override
        public void run() {
            Thread.currentThread().setName("WriteCrawledUrlsImpl");
            try {
                String filename = "result/parsed/parsed-files_" + String.valueOf(System.currentTimeMillis());
                afwParsedFiles = new AsyncFileWriter(new File(filename));
                afwParsedFiles.open();
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            while (true) {
                try {
                    String filePath = parsedfilesQueue.poll();
                    if (StringUtils.isEmpty(filePath)) {
                        Thread.sleep(1000);
                    } else {
                        allProfileFilePaths.put(filePath, filePath);
                        afwParsedFiles.append(filePath + "\n");
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private static final ConcurrentHashMap<String, String> allProfileFilePaths = new ConcurrentHashMap<>();

    private static class QueryFiles implements Runnable {

        private final List<String> pathFolders;

        public QueryFiles(List<String> pathFolders) {
            this.pathFolders = pathFolders;
        }

        @Override
        public void run() {
            Thread.currentThread().setName("QueryListFolders");

            initParsedFiles();
            LOG.info("Loaded parsed files");

            while (true) {

                // query files in all folders
                for (String pathFolder : pathFolders) {
                    LOG.debug("folder {}", pathFolder);
                    long startTime = System.currentTimeMillis();
                    int count = 0;
                    try {
                        File folder = new File(pathFolder);
                        File[] listOfFiles = folder.listFiles();
                        for (File file : listOfFiles) {
                            if (file.isFile()) {
                                count++;
                                LOG.debug("File {}", file.getAbsolutePath());
                                String filePath = file.getAbsolutePath();
                                if (allProfileFilePaths.contains(filePath)) {
                                    // already contains
                                } else {
                                    allProfileFilePaths.put(filePath, filePath);
                                    filesQueue.add(filePath);
                                }
                            } else if (file.isDirectory()) {
                                LOG.warn("Directory {}", file.getAbsolutePath());
                            }
                        }
                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                    long endTime = System.currentTimeMillis();
                    LOG.info("Load folder {} - {} files in {} ms", new Object[]{pathFolder, count, endTime - startTime});
                }

                // sleep for 5 minutes
                try {
                    Thread.sleep(5 * 60 * 1000);
                } catch (InterruptedException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }

        private static void initParsedFiles() {
            String pathFolder = "result/parsed";
            try {
                File folder = new File(pathFolder);
                File[] listOfFiles = folder.listFiles();
                for (File file : listOfFiles) {
                    if (file.isFile()) {
                        LOG.info("File {}", file.getAbsolutePath());

                        List<String> filePaths = new ArrayList<>();
                        try {
                            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                                String line;
                                while ((line = br.readLine()) != null) {
                                    String temp = line.trim();
                                    filePaths.add(temp);
                                }
                            }
                        } catch (IOException ex) {
                            LOG.error("Failed to read " + file.getAbsolutePath() + " file", ex);
                        }

                        for (String filePath : filePaths) {
                            allProfileFilePaths.put(filePath, filePath);
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

}
