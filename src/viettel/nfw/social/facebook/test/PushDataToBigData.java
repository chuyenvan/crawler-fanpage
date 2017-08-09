package viettel.nfw.social.facebook.test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.gfw.vn.producer.producer.MessageInfo;
import viettel.gfw.vn.producer.producer.ProducerORMWeb;
import viettel.gfw.vn.social.FaceBookProto;
import viettel.gfw.vn.social.FaceBookProtoTrans;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.reviewdata.ParsingUtils;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.social.fb.test.FbUrlToHtml;

/**
 *
 * @author duongth5
 */
public class PushDataToBigData {

    private static final Logger LOG = LoggerFactory.getLogger(PushDataToBigData.class);
    private static final int QUEUE_CAPACITY = 3000000;
    public static BlockingQueue<String> filesQueue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    public static BlockingQueue<FacebookObject> facebookObjectQueue = new ArrayBlockingQueue<>(2000000);

    public static void main(String[] args) {
        String listDirs = "list.txt";

        // Thread Query dirs 
        QueryStorageImpl queryStorageImpl = new QueryStorageImpl(listDirs);
        new Thread(queryStorageImpl).start();

        // Thread Parse HTML to FbObject
        ParseToFbObjImpl parseToFbObjImpl = new ParseToFbObjImpl();
        new Thread(parseToFbObjImpl).start();

        // Thread push fbObj to BigData
        PushToBigDataImpl pushToBGImpl = new PushToBigDataImpl();
        new Thread(pushToBGImpl).start();

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
                        Thread.sleep(1000);
                    } else {
                        MessageInfo message = new MessageInfo();
                        FaceBookProto.FBSocialObject fbSocialObject = FaceBookProtoTrans.facebookObjectTo(fbObj);
                        message.setDataSocial(fbSocialObject, MessageInfo.MESSAGE_TYPE_SOCIAL_FACEOOK);
                        producer.sendMessageORMWeb(message);
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    private static class ParseToFbObjImpl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(ParseToFbObjImpl.class);

        @Override
        public void run() {
            Thread.currentThread().setName("ParseToFbObjImpl");
            ThreadPoolExecutor excutor = new ThreadPoolExecutor(20, 20, 1, TimeUnit.DAYS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(1000));
            while (true) {
                final String objectFilePath = filesQueue.poll();
                if (StringUtils.isEmpty(objectFilePath)) {
                    // do nothing
                    LOG.info("Queue is empty");
                    try {
                        Thread.sleep(15 * 1000);
                    } catch (InterruptedException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                } else {
                    try {
                        excutor.execute(new Runnable() {

                            @Override
                            public void run() {
                                File file = new File(objectFilePath);
                                String filename = file.getName();
                                Thread.currentThread().setName(filename);
                                try {
                                    List<FbUrlToHtml> htmls = (List<FbUrlToHtml>) FileUtils.readObjectFromFile(file, false);
                                    if (!htmls.isEmpty()) {
                                        FbUrlToHtml firstRecord = htmls.get(0);
                                        AccountStatus status = Parser.verifyResponseHtml(firstRecord.getRawUrl(), firstRecord.getRawHtml(), true);
                                        if (status.equals(AccountStatus.ACTIVE)) {
                                            FacebookObject fbObj = ParsingUtils.fromHtmltoFacebookObject(htmls);
                                            if (fbObj != null) {
                                                facebookObjectQueue.add(fbObj);
                                            }
                                        }
                                    } else {
                                        LOG.info("File {} is empty", file.getAbsolutePath());
                                    }
                                } catch (Exception ex) {
                                    LOG.error(ex.getMessage(), ex);
                                    try {
                                        FacebookObject fbObject = (FacebookObject) FileUtils.readObjectFromFile(file, false);
                                        if (fbObject != null) {
                                            facebookObjectQueue.add(fbObject);
                                        }
                                    } catch (Exception e) {
                                        LOG.error(e.getMessage(), e);
                                    }
                                }
                            }
                        });
                    } catch (RejectedExecutionException ex) {
                        while (excutor.getQueue().size() > 500) {
                            try {
                                Thread.sleep(50);
                            } catch (InterruptedException ex1) {
                                LOG.error(ex1.getMessage(), ex1);
                            }
                        }
                    }
                }
            }
        }
    }

    private static class QueryStorageImpl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(QueryStorageImpl.class);
        private final String inputFilePath;
        private final Set<String> allProfileFilePaths = new HashSet<>();

        public QueryStorageImpl(String inputFilePath) {
            this.inputFilePath = inputFilePath;
        }

        @Override
        public void run() {
            Thread.currentThread().setName("QuerryStorageImpl");
            while (true) {
                // read input file path
                List<String> pathFolders = new ArrayList<>();
                try {
                    try (BufferedReader whiteSiteReader = new BufferedReader(new FileReader(new File(inputFilePath)))) {
                        String line;
                        while ((line = whiteSiteReader.readLine()) != null) {
                            String temp = line.trim();
                            pathFolders.add(temp);
                        }
                    }
                } catch (IOException ex) {
                    LOG.error("Failed to read " + inputFilePath + " file", ex);
                }

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
                                    allProfileFilePaths.add(filePath);
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

    }
}
