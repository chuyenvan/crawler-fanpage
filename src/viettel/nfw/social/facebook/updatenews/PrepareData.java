package viettel.nfw.social.facebook.updatenews;

import com.viettel.nfw.im.facebookparser.FacebookParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.utils.FileUtils;
import vn.itim.detector.InputType;
import vn.itim.detector.Language;
import vn.itim.detector.LanguageDetector;

/**
 *
 * @author duongth5
 */
public class PrepareData {

    private static final Logger LOG = LoggerFactory.getLogger(PrepareData.class);

    public static void main(String[] args) {
//        getGroupId();
//        findGroupId();
        collectPages();
    }

    // TODO
    private static void collectPages() {
        String storagePath = "data2/update-news/cate";
        Set<PageUser> results = new HashSet<>();
        try {
            File folder = new File(storagePath);
            File[] listOfFiles = folder.listFiles();
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    LOG.debug("File {}", file.getAbsolutePath());
                    try {
                        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                String part[] = StringUtils.split(line, "\t");
                                int length = part.length;
                                if (length == 4) {
                                    PageUser obj = new PageUser();
                                    obj.type = part[0];
                                    obj.originUrl = part[1];
                                    obj.redirectUrl = part[2];
                                    obj.title = part[3];
                                    results.add(obj);
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
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

        LanguageDetector languageDetector = new LanguageDetector();
        Set<String> pageUrls = new HashSet<>();
        for (PageUser result : results) {
            if (result.type.equalsIgnoreCase("PAGE")) {
//                Language language = languageDetector.detect(result.title, null, InputType.PLAIN);
//                if (language == Language.VIETNAMESE) {
//                    pageUrls.add(result.redirectUrl);
//                }

                pageUrls.add(result.redirectUrl);
            }
        }
        LOG.info("Size of pageUrls {}", pageUrls.size());

        Set<String> pageIds = new HashSet<>();
        for (String pageUrl : pageUrls) {
            int lastSource = StringUtils.lastIndexOf(pageUrl, "/");
            String pageId = StringUtils.substring(pageUrl, lastSource + 1);
            pageIds.add(pageId);
        }
        LOG.info("Size of pageIds {}", pageIds.size());

        Set<String> toTrack = new HashSet<>();
        for (String pageId : pageIds) {
            String temp = "facebook" + "\t" + pageId + "\t" + "page" + "\t" + "6h";
            toTrack.add(temp);
        }

        try {
            FileUtils.write(new File("data2/update-news/news/data.page.20150627.txt"), toTrack);
        } catch (FileNotFoundException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static class PageUser {

        public String type;
        public String originUrl;
        public String redirectUrl;
        public String title;
    }

    private static void findGroupId() {
        String storagePath = "storage/evalweb";
        List<String> results = new ArrayList<>();
        try {
            File folder = new File(storagePath);
            File[] listOfFiles = folder.listFiles();
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    LOG.debug("File {}", file.getAbsolutePath());
                    String filePath = file.getAbsolutePath();
                    com.viettel.nfw.im.facebookparser.FacebookParser fbParser = new FacebookParser();
                    FacebookObject fbObject = fbParser.parseFileToObject(file);
                    String result = fbObject.getInfo().getUrl() + "\t" + fbObject.getInfo().getId();
                    results.add(result);
                } else if (file.isDirectory()) {
                    LOG.warn("Directory {}", file.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

        try {
            FileUtils.write(new File("data2/update-news/news/data.group.un.20150623.txt"), results);
        } catch (FileNotFoundException ex) {
            LOG.error(ex.getMessage(), ex);
        }

    }

    private static void getGroupId() {
        String filename = "data2/update-news/news/groups.txt";
        Set<String> groupHasId = new HashSet<>();
        Set<String> groupHasUsername = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String url = line.trim();
                    String a = StringUtils.replace(url, "https://m.facebook.com/groups/", "");
                    String b = StringUtils.replace(a, "/", "");
                    if (b.matches("^[0-9]+$")) {
                        String temp = "facebook" + "\t" + b + "\t" + "group" + "\t" + "3h";
                        groupHasId.add(temp);
                    } else {
                        groupHasUsername.add(url);
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        try {
            FileUtils.write(new File("data2/update-news/news/data.group.20150623.txt"), groupHasId);
            FileUtils.write(new File("data2/update-news/news/groupUNs.txt"), groupHasUsername);
        } catch (FileNotFoundException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }
}
