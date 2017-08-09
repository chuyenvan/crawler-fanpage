package viettel.nfw.social.facebook.updatenews;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.controller.ControllerReporter;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import viettel.nfw.social.facebook.updatenews.graph.entities.SocialType;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.utils.CustomizedFixedThreadPool;

/**
 *
 * @author duongth5
 */
public class LoadObjectRequestFromLocal {

    private static final Logger LOG = LoggerFactory.getLogger(LoadObjectRequestFromLocal.class);
    private static final String DATA_FOLDER = "input/updatenews/data/";
    private static ConcurrentHashMap<String, ObjectRequest> allObjectsMap = new ConcurrentHashMap<>();
    private static final String TOPIC_NAME = "FB_UPDATE_NEWS";
    private static final String RECEIVED_LIST_PROFILES = "COLLECT_PAGE_GROUP";
    private static ControllerReporter reporter;

    private static ControllerReporter reporter2;

    public static void main(String[] args) {

        reporter = ControllerReporter.getDefault("10.30.154.103", 61616, null, null, RECEIVED_LIST_PROFILES);
        reporter.start();

        reporter2 = ControllerReporter.getDefault("10.30.154.103", 61616, null, null, TOPIC_NAME);
        reporter2.start();

        pushUrl();
        doJob();

        Funcs.sleep(2 * 60 * 60 * 1000);

        reporter2.shutdown();
        reporter.shutdown();
    }

    private static void pushUrl() {
        try {
            File folder = new File("input/updatenews/urls");
            File[] listOfFiles = folder.listFiles();
            Set<String> urls = new HashSet<>();
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    LOG.debug("File {}", file.getAbsolutePath());
                    String filePath = file.getAbsolutePath();
                    try {
                        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                line = line.trim();
                                if (StringUtils.isNotEmpty(line)
                                        && !StringUtils.startsWith(line, "#")) {
                                    urls.add(line);
                                }
                            }
                        }
                    } catch (IOException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }

                } else if (file.isDirectory()) {
                    LOG.warn("Directory {}", file.getAbsolutePath());
                }
            }

            int i = 0;
            List<String> smallList = new ArrayList<>();
            for (String url : urls) {
                if (i < 20) {
                    smallList.add(url);
                    i++;
                } else {
                    reporter.offer(StringUtils.join(smallList, ","));
                    smallList.clear();
                    i = 0;
                }
            }

        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static void doJob() {

        try {
            File folder = new File("input/updatenews/failed");
            File[] listOfFiles = folder.listFiles();
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    LOG.debug("File {}", file.getAbsolutePath());
                    String filePath = file.getAbsolutePath();
//                    List<ObjectRequest> objectRequests = readObjectRequestsFromFile(filePath);
                    // check condition
//                    for (ObjectRequest objectRequest : objectRequests) {
//                        String key = objectRequest.socialType + "_" + objectRequest.objectID;
//                        allObjectsMap.put(key, objectRequest);
//                    }

                    List<String> objectRequests = readObjectRequestsFromFile1(filePath);
                    for (String objectRequest : objectRequests) {
                        reporter2.offer(objectRequest);
                        Funcs.sleep(Funcs.randInt(700, 1500));
                    }

                } else if (file.isDirectory()) {
                    LOG.warn("Directory {}", file.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static List<String> readObjectRequestsFromFile1(String filePath) {

        List<String> objectRequests = new ArrayList<>();
        File file = new File(filePath);
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (StringUtils.isNotEmpty(line)
                            && !StringUtils.startsWith(line, "#")) {
                        String[] parts = StringUtils.split(line, "\t");
                        int length = parts.length;
                        if (length >= 3) {
                            String socialTypeStr = parts[0].toLowerCase();
                            String objectIdStr = parts[1].toLowerCase();
                            String objectTypeStr = parts[2].toLowerCase();
                            String timeLoopStr = "6h";

                            SocialType socialType = Helper.detectSocialType(socialTypeStr);
                            if (!socialType.equals(SocialType.UNDEFINED)) {
                                String send = socialTypeStr + "|" + objectIdStr + "|" + objectTypeStr + "|" + timeLoopStr;
                                objectRequests.add(send);
                            }
                        } else {
                            LOG.warn("This line has error format: {} in file {}", line, filePath);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        return objectRequests;
    }

    private static List<ObjectRequest> readObjectRequestsFromFile(String filePath) {

        List<ObjectRequest> objectRequests = new ArrayList<>();
        File file = new File(filePath);
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (StringUtils.isNotEmpty(line)
                            && !StringUtils.startsWith(line, "#")) {
                        String[] parts = StringUtils.split(line, "\t");
                        int length = parts.length;
                        if (length == 4) {
                            String socialTypeStr = parts[0];
                            String objectIdStr = parts[1];
                            String objectTypeStr = parts[2];
                            String timeLoopStr = parts[3];

                            SocialType socialType = Helper.detectSocialType(socialTypeStr);
                            if (!socialType.equals(SocialType.UNDEFINED)) {
                                String send = socialTypeStr + "|" + objectIdStr + "|" + objectTypeStr + "|" + timeLoopStr;
                                reporter.offer(send);
                                Funcs.sleep(Funcs.randInt(600, 900));
                                // TODO improve this later
                                // With socialType = facebook: objectType = page/group
                                // Others                    : objectType = unknown
                                ObjectRequest objectRequest = new ObjectRequest();
                                objectRequest.socialType = socialType;
                                objectRequest.objectID = objectIdStr;
                                objectRequest.objectType = Helper.detectObjectType(objectTypeStr);
                                objectRequest.loopTimeTimeMillis = Helper.convertTime(timeLoopStr);
                                objectRequests.add(objectRequest);
                            }
                        } else {
                            LOG.warn("This line has error format: {} in file {}", line, filePath);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        return objectRequests;
    }
}
