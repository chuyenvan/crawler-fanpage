package viettel.nfw.social.facebook.test;

import com.google.gson.Gson;
import com.viettel.naviebayes.ClassifierResult;
import com.viettel.naviebayes.NaiveBayes;
import viettel.nfw.social.utils.Storage;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.facebook.htmlobj.FacebookHeaderBar;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.automanacc.object.ObjectPost;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.common.ConfigurationChangeListner;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.facebook.htmlobj.FacebookCommentForm;
import viettel.nfw.social.facebook.deeptracking.ObjectDataSendCrawler;
import viettel.nfw.social.utils.Constant;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.TCrawler;
import viettel.nfw.social.utils.Funcs;
import vn.itim.engine.util.FileUtils;
import vn.viettel.engine.utils.TParser;

/**
 *
 * @author duongth5
 */
public class Test {

    private static final Logger LOG = LoggerFactory.getLogger(Test.class);

    private static final String[] UNEXPECT_WORDS = new String[]{
        "See More", "See translation", "Get Flash Player",
        "Flash Player upgrade required You must download and install the latest version of Adobe Flash Player to view this content."
    };

    public static void main(String[] args) {
//        List<Pair<URI, String>> pairs = readPairs();
//        testCaseGetFacebookCommentForm(pairs);
//        testCaseGetFacebookHeaderBar(pairs);
//        testReadPost();
//        for (int i = 0; i < 20; i++) {
//            testRandom();
//        }
//        testConfig();
//        testSplit();
//        testGetTopic();
//        test();
//        pushGoogleUrls();
//        pushTwitterUrls();
//        testRestService();
//        testGson();
//        pushEvalUrls();
//        testDate();
//        pushOutlinks();
//        checkInit();

//        String hostPro = "http://203.113.152.24:50025";
//        String ret = TCrawler.getContentFromUrl(hostPro + "/crawled/?action=show");
//        LOG.info(ret);
//        check();
//        String status = "Nông Đức Mạnh, ông nói đi, ông lấy tiền ở đâu ra để xây chốn hành lạc nguy nga, lộng lẫy thế?!, "
//                + "Flash Player upgrade required You must download and install the latest version of Adobe Flash Player to view this content. Get Flash Player Warning -"
//                + " Graphic Video Videos that contain graphic content can shock, offend and upset. Are you sure you want to see this?"
//                + "ĐMCS giàu thiệt!!!"
//                + " See More See translation";
//        String status = null;
//        for (String word : UNEXPECT_WORDS) {
//            status = StringUtils.replace(status, word, "");
//        }
//        System.out.println(status);
//        summaryDomain();
    }

    private static void summaryDomain() {
        List<String> filenames = new ArrayList<>();
        filenames.add("/home/crawler/sumdomains/url-27.txt");
        filenames.add("/home/crawler/sumdomains/url-28.txt");
        filenames.add("/home/crawler/sumdomains/url-29.txt");

        int i = 0;
        for (String filename : filenames) {
            Set<String> domains = new HashSet<>();
            try {
                try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (StringUtils.isNotEmpty(line.trim())) {
//                            try {
//                                String url = URLEncoder.encode(line.trim(), "UTF-8");
//                                LOG.info("url {}", url);
//                                URI uri = new URI(url);
//                                String host = uri.getHost();
//                                if (StringUtils.isNotEmpty(host)) {
//                                    LOG.info("host {}", host);
//                                    domains.add(host);
//                                }
//                            } catch (UnsupportedEncodingException | URISyntaxException ex) {
//                                LOG.error(ex.getMessage(), ex);
//                            }
                            try {
                                String host = getHost(line.trim());
//                            LOG.info("{}", host);
                                domains.add(host);
                            } catch (Exception e) {
                                LOG.error(line);
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }
            LOG.info("size {}", domains.size());

            try {
                FileUtils.write(new File("domains-" + i + ".txt"), domains);
            } catch (FileNotFoundException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            Funcs.sleep(3000);
            i++;
        }

        LOG.info("Done!");
    }

    public static String getHost(String url) {
        if (url == null || url.length() == 0) {
            return "";
        }

        int doubleslash = url.indexOf("//");
        if (doubleslash == -1) {
            doubleslash = 0;
        } else {
            doubleslash += 2;
        }

        int end = url.indexOf('/', doubleslash);
        end = end >= 0 ? end : url.length();

        int port = url.indexOf(':', doubleslash);
        end = (port > 0 && port < end) ? port : end;

        return url.substring(doubleslash, end);
    }

    private static void check() {

        try {
            NaiveBayes.getInstance();
            LOG.info("Finished getting naive bayes instance");

            String text = "Nông Đức Mạnh, ông nói đi, ông lấy tiền ở đâu ra để xây chốn hành lạc nguy nga, lộng lẫy thế?!\n"
                    + "ĐMCS giàu thiệt!!!\n"
                    + "(Ảnh: Đoàn do BTTNTW Đoàn Nguyễn Đắc Vinh tới thăm, chúc Tết tại nhà Nguyên tổng bí thơ Nông Đức Mạnh)";

            String text_1 = "THÔNG BÁO: ÔNG LIÊN THÀNH SẼ THỰC HIỆN MỘT VIDEO TRẢ LỜI CÁC THẮC MẮC CỦA ĐỒNG BÀO VÀ QUÝ CHIẾN HỮU VỀ \"VỤ ÁN KHIẾM DIỆN LIÊN THÀNH\" Orange County, ngày 30 tháng 5 năm 2015 Thưa Đồng Bào, Thưa Quý Chiến Hữu,... Ngày 27-5-2015, ông Liên Thành được Ký giả Đoàn Trọng của Đài Truyền Hình Little Saigon 57.7 mời lên đài phỏng vấn về bản án khiếm diện của Tòa án California ngày 9-10-2014 do Võ Văn Ái, Phát ngôn nhân của GHPGVNTN, từ Paris tung ra trong thông báo ngày 23-5-2015. Chúng tôi xin gọi vụ án này là \"Vụ Án Khiếm Diện Liên Thành\". Một số lớn đồng bào và quý chiến hữu đã gởi email hoặc điện thoại cho ông Liên Thành than phiền rằng: 'Ngồi xem video Ký giả Đoàn Trọng phỏng vấn ông Liên Thành, có cảm tưởng như xem phiên tòa Việt Cộng xử Cha Lý tại Thừa Thiên Huế', \"Cách ngắt lời của Đoàn Trọng làm gợi nhớ đến hình ảnh Cha Lý bị CSVN bịt miệng\",... Đồng bào và quý chiến hữu muốn ông Liên Thành giải thích chi tiết hơn về \"Vụ Án Khiếm Diện Liên Thành\". Để đáp lời yêu cầu của đồng bào và quý chiến hữu, xin đồng bào và quý chiến hữu gởi về cho ông Liên Thành những câu hỏi liên quan. Chúng tôi sẽ chọn lọc và trả lời qua một video mà chúng tôi sẽ thực hiện và phổ biến lên Youtube để quý vị được rõ sự việc hơn. Các câu hỏi xin gởi về cho ông Liên Thành: Email: ubtttadcsvn.vg@gmail.com hoặc Điện Thoại: 626-257-1057 http://ubtttadcsvn.blogspot.com/…/thong-bao-ong-lien-thanh-… See More . THÔNG BÁO: ÔNG LIÊN THÀNH SẼ THỰC HIỆN MỘT VIDEO TRẢ LỜI CÁC THẮC MẮC CỦA ĐỒNG BÀO VÀ QUÝ CHIẾN... 1)- Thích Trí Quang, Thần Tượng hay Tội Đồ Dân Tộc?2)- Biến Động Miền Trung3)- Huế - Thảm Sát Mậu Thân***Ấn phí và cước phí:1/ Chi phiếu xin gởi vềLiên ThànhAddress: P.O. Box 6147 - Fullerton, CA 92834 - USAPhone: 626-257-1057Email: ubtttadcsvn.vg@gmail.com2/ Đặt sách online ubtttadcsvn.blogspot.com";

            String fullContent = text_1; // + text_1 + text_1;

            ClassifierResult classifierByContentWithArticle = NaiveBayes.getInstance().getClassifierByContentWithUserMode(fullContent, -50.0f);
            LOG.info("score {}", classifierByContentWithArticle.score);

        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

    }

    private static void checkInit() {
        List<String> urls = new ArrayList<>();
        urls.add("https://plus.google.com/app/basic/3213131312321");
        urls.add("https://m.facebook.com/test1");
        urls.add("https://www.facebook.com/test2");
        urls.add("https://vi-vn.facebook.com/test3");
        urls.add("https://mobile.twitter.com/test4");
        for (String url : urls) {
            String normurl = StringUtils.replaceEach(url,
                    new String[]{"//m.facebook.com/", "//www.facebook.com/", "//vi-vn.facebook.com", "//plus.google.com/app/basic/", "//mobile.twitter.com/"},
                    new String[]{"//facebook.com/", "//facebook.com/", "//facebook.com/", "//plus.google.com/", "//twitter.com/"});
            LOG.info(normurl);
        }
    }

    private static void testDate() {
//        Calendar cal = Calendar.getInstance();
//        System.out.println("current time is: " + cal.getTime() + " - " + System.currentTimeMillis());
//
//        Date date = new Date(System.currentTimeMillis() - 4 * 60 * 60 * 1000);
//        cal.setTime(date);
//
//        System.out.println("new time is: " + cal.getTime() + " - " + System.currentTimeMillis());
//
//        String str = "3 minutes ago";
//        Date humandate = Funcs.humanTimeParser(str);
//        if (humandate != null) {
//            System.out.println("parse date: " + humandate.toString());
//            System.out.println("new time is: " + cal.getTime() + " - " + System.currentTimeMillis());
//        }
//
//        long currentTime = System.currentTimeMillis();
//        Date resetDate = new Date(currentTime);
//        cal.setTime(resetDate);
//        System.out.println("new time is: " + cal.getTime() + " - " + System.currentTimeMillis());

        Date date = Funcs.humanTimeParser("Saturday at 8:30pm");
        System.out.println(date.toString());
        System.out.println(new Date(1432042200000L).toString());

        String url = "https://www.facebook.com/";
        try {
            String path = new URI(url).getPath();
            System.out.println(path);
        } catch (URISyntaxException ex) {
            java.util.logging.Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private static void testGson() {
        List<String> list = new ArrayList<>();
        list.add("abc");
        list.add("def");
        ObjectDataSendCrawler obj = new ObjectDataSendCrawler("1", list);

        String str = JSON.encode(obj);
        LOG.info(str);

        Gson gson = new Gson();
        ObjectDataSendCrawler trackingUrl = gson.fromJson(str, ObjectDataSendCrawler.class);
        LOG.info(trackingUrl.toString());
    }

    private static void testRestService() {
        try {
            String svcurl = "http://localhost:1130/eval/url/" + URLEncoder.encode("https://m.facebook.com/duongth5", "UTF-8");
            TCrawler.getContentFromUrl(svcurl);
        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static void pushOutlinks() {
        String filename = "logs/outlinks.txt";
        Set<String> oulinks = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    oulinks.add(temp);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        ThreadPoolExecutor excutor = new ThreadPoolExecutor(40, 40, 1, TimeUnit.DAYS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(1000));
        for (final String googleUrl : oulinks) {
            try {
                excutor.execute(new Runnable() {

                    @Override
                    public void run() {
                        boolean ret = ServiceOutlinks.addOutLink("NO-LOGIN", googleUrl, googleUrl);
                        LOG.info("{}", ret);
                    }
                });
            } catch (RejectedExecutionException ex) {
                while (excutor.getQueue().size() > 100) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex1) {
                        LOG.error(ex1.getMessage(), ex1);
                    }
                }
            }
        }
    }

    private static void pushGoogleUrls() {
//        String filename = "data2/google-urls.txt";
//        String filename = "data2/out.txt";
        String filename = "data2/crawled.profiles";
        Set<String> rows = new HashSet<>();
        Set<String> googleUrls = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (StringUtils.startsWithIgnoreCase(line, "p:https://plus.google.com/")) {
                        rows.add(line);
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (String row : rows) {
            String[] parts = StringUtils.split(row.trim(), "|");
            String firstPos = parts[0];
            String profileUrl = StringUtils.substring(firstPos, 2);
            googleUrls.add(profileUrl);
        }

        ThreadPoolExecutor excutor = new ThreadPoolExecutor(40, 40, 1, TimeUnit.DAYS, new java.util.concurrent.ArrayBlockingQueue<Runnable>(1000));
        for (final String googleUrl : googleUrls) {
//            try {
//                String ret = TCrawler.getContentFromUrl("http://192.168.6.81:1125/priority/?url=" + URLEncoder.encode(googleUrl, "UTF-8") + "&isForced=true");
//                LOG.info(ret);
//            } catch (UnsupportedEncodingException ex) {
//                java.util.logging.Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
//            }

            try {
                excutor.execute(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            String hostDev = "http://192.168.6.81:1125";
                            String hostPro = "http://203.113.152.24:50025";
                            String ret = TCrawler.getContentFromUrl(hostPro + "/priority/?url=" + URLEncoder.encode(googleUrl, "UTF-8") + "&isForced=true");
                            LOG.info("{} - {}", ret, googleUrl);
                        } catch (UnsupportedEncodingException ex) {
                            LOG.error(ex.getMessage(), ex);
                        } catch (Exception ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    }
                });
            } catch (RejectedExecutionException ex) {
                while (excutor.getQueue().size() > 100) {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex1) {
                        LOG.error(ex1.getMessage(), ex1);
                    }
                }
            }
        }

//        excutor.shutdown();
//        
//        try {
//            while (!excutor.awaitTermination(1, TimeUnit.DAYS)) {
//                
//            }
//        } catch (InterruptedException ex) {
//            LOG.error(ex.getMessage(), ex);
//        }
//        LOG.info("DONE!!!");
    }

    private static void pushEvalUrls() {

        Set<String> profileUrls = new HashSet<>();
        String mode = "2";
        if (mode.equalsIgnoreCase("1")) {
            String filename = "data2/eval/listProfileFacebooktocheckTool-53000-55000.txt";
            File file = new File(filename);
            List<String> rows = new ArrayList<>();
            try {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (StringUtils.startsWithIgnoreCase(line, "p:https://m.facebook.com/")) {
                            rows.add(line);
                        }
                    }
                }
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            for (String row : rows) {
                String[] parts = StringUtils.split(row.trim(), "|");
                String firstPos = parts[0];
                String profileUrl = StringUtils.substring(firstPos, 2);
                profileUrls.add(profileUrl);
            }
        } else if (mode.equalsIgnoreCase("2")) {
            List<String> bai2Urls = new ArrayList<>();
//            String filename = "data2/eval/list.txt";
            String filename = "data2/sensitive/bai3.txt";

            File file = new File(filename);
            try {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        bai2Urls.add(line);
                    }
                }
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            for (String bai2Url : bai2Urls) {
                LOG.info(bai2Url);
                try {
                    String ret = TCrawler.getContentFromUrl("http://localhost:1155/eval/?url=" + URLEncoder.encode(bai2Url, "UTF-8"));
                    LOG.info(ret);
                } catch (UnsupportedEncodingException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }

        } else if (mode.equalsIgnoreCase("3")) {
            String filename = "data2/eval/listProfileFacebooktocheckTool-53000-55000.txt";
            File file = new File(filename);
            List<String> rows = new ArrayList<>();
            try {
                try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (StringUtils.startsWithIgnoreCase(line, "p:https://m.facebook.com/")) {
                            rows.add(line);
                        }
                    }
                }
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            String filenameCannot = "data2/eval/cannotview.txt";
            File file2 = new File(filenameCannot);
            List<String> cannotviews = new ArrayList<>();
            try {
                try (BufferedReader br = new BufferedReader(new FileReader(file2))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        cannotviews.add(line);
                    }
                }
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            String filenameSen = "data2/eval/sen.txt";
            File file3 = new File(filenameSen);
            List<String> senUrls = new ArrayList<>();
            try {
                try (BufferedReader br = new BufferedReader(new FileReader(file3))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        senUrls.add(line);
                    }
                }
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            for (String row : rows) {
                String[] parts = StringUtils.split(row.trim(), "|");
                String firstPos = parts[0];
                String profileUrl = StringUtils.substring(firstPos, 2);

                if (cannotviews.contains(profileUrl)) {
                    System.out.println(profileUrl + "\t" + "CANNOT VIEW");
                } else if (senUrls.contains(profileUrl)) {
                    System.out.println(profileUrl + "\t" + true);
                } else {
                    System.out.println(profileUrl + "\t" + false);
                }
            }
        }

//        for (String profileUrl : profileUrls) {
//            LOG.info(profileUrl);
//            try {
//                String ret = TCrawler.getContentFromUrl("http://192.168.6.83:1155/eval/?url=" + URLEncoder.encode(profileUrl, "UTF-8"));
//                LOG.info(ret);
//            } catch (UnsupportedEncodingException ex) {
//                LOG.error(ex.getMessage(), ex);
//            }
//        }
    }

    private static void pushTwitterUrls() {
        String filename = "data2/crawled.profiles";
        File file = new File(filename);
        List<String> rows = new ArrayList<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (StringUtils.startsWithIgnoreCase(line, "p:https://mobile.twitter.com")) {
                        rows.add(line);
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        Set<String> profileUrls = new HashSet<>();
        for (String row : rows) {
            String[] parts = StringUtils.split(row.trim(), "|");
            String firstPos = parts[0];
            String profileUrl = StringUtils.substring(firstPos, 2);
            profileUrls.add(profileUrl);
        }

        for (String profileUrl : profileUrls) {
            try {
                String hostPro = "http://203.113.152.24:50025";
                String ret = TCrawler.getContentFromUrl(hostPro + "/priority/?url=" + URLEncoder.encode(profileUrl, "UTF-8") + "&isForced=true");
                LOG.info("{} - {}", ret, profileUrl);
            } catch (UnsupportedEncodingException ex) {
                java.util.logging.Logger.getLogger(Test.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

    }

    private static void test() {
        String profileUrl
               = "https://m.facebook.com/pages/H%E1%BB%99i-Ph%C3%A1t-cu%E1%BB%93ng-v%C3%AC-%C4%91%E1%BB%99-So-Ciu-c%E1%BB%A7a-teen-Vi%E1%BB%87t-Nam-X/530634390322184/";
        Map<String, String> retMap = Parser.extractUsernameOrIdFromUrl(profileUrl);
        LOG.info(retMap.toString());
    }

    private static void testGetTopic() {
        ServiceOutlinks.getTopic("fashion");
    }

    private static void testSplit() {
        String str = "duongth5, ";
        String[] parts = StringUtils.split(str.trim(), ",");
        List<String> items = new ArrayList<>();
        items.addAll(Arrays.asList(parts));
        System.out.println("size " + items.size());
    }

    private static void testConfig() {
        String FILE_PATH = "conf/app-maintain-account.properties";
        ConfigurationChangeListner listner = new ConfigurationChangeListner(FILE_PATH);
        try {
            new Thread(listner).start();
            while (true) {
                Thread.sleep(2000l);
                System.out.println(ApplicationConfiguration.getInstance().getConfiguration("com.dth.key1"));
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

    private static void testReadPost() {
        File file = new File(Constant.FILE_POSTDATA);
        Map<String, List<ObjectPost>> listDataUsePost = new HashMap<>();
        if (file.isFile()) {
            try {
                Storage.Reader reader = new Storage.Reader(file);
                listDataUsePost = (Map<String, List<ObjectPost>>) reader.next();
                reader.close();
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }

            for (Map.Entry<String, List<ObjectPost>> entrySet : listDataUsePost.entrySet()) {
                String key = entrySet.getKey();
                List<ObjectPost> value = entrySet.getValue();
                LOG.info("key {} - size {}", key, value.size());
            }
        }
    }

    private static void testCaseGetFacebookCommentForm(List<Pair<URI, String>> pairs) {
        for (Pair<URI, String> pair : pairs) {
            URI inputUri = pair.first;
            String rawHtml = readHtml(pair.second);
            FacebookCommentForm commentForm = Parser.getCommentForm(inputUri, rawHtml);
            LOG.info("comment uri {}", commentForm.commentUri.toString());
            String ret = Parser.buildParamsFromCommentForm(commentForm.commentForm, "Hello");
            LOG.info(ret);
        }
    }

    private static void testCaseGetFacebookHeaderBar(List<Pair<URI, String>> pairs) {
        for (Pair<URI, String> pair : pairs) {
            URI inputUri = pair.first;
            String rawHtml = readHtml(pair.second);
            FacebookHeaderBar headerBar = Parser.getHeaderBarOfCurrentHtml(inputUri, rawHtml);
            System.out.println(headerBar.toString());
        }
    }

    private static List<Pair<URI, String>> readPairs() {
        List<Pair<URI, String>> pairs = new ArrayList<>();
        String filename = "testdata/pairs.txt";
        File file = new File(filename);
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (StringUtils.startsWith(line, "#")) {
                        // ignore comment line
                    } else {
                        try {
                            String[] parts = StringUtils.split(line, "\t");
                            String url = parts[0];
                            String htmlFilePath = parts[1];
                            URI uri = new URI(url);
                            Pair<URI, String> uriToHtmlFile = new Pair<>(uri, htmlFilePath);
                            pairs.add(uriToHtmlFile);
                        } catch (URISyntaxException ex) {
                            LOG.error(ex.getMessage(), ex);
                        }
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return pairs;
    }

    public static String readHtml(String filename) {
        File file = new File(filename);
        StringBuilder sb = new StringBuilder();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return sb.toString();
    }

}
