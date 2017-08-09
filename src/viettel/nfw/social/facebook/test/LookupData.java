package viettel.nfw.social.facebook.test;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.utils.CustomPartition;
import viettel.nfw.social.model.googleplus.GooglePlusObject;
import viettel.nfw.social.reviewdata.ParsingUtils;
import viettel.nfw.social.utils.FileUtils;
import vn.viettel.social.fb.test.FbUrlToHtml;

/**
 *
 * @author duongth5
 */
public class LookupData {

    private static final Logger LOG = LoggerFactory.getLogger(LookupData.class);

    public static void main(String[] args) {
//        lookUpFacebookData();
        lookUpFacebookData2();
//        lookUpGoogleData();
    }

    private static void lookUpFacebookData2() {
        String pathFolder = "data2/facebook";
        List<File> fbDataFiles = new ArrayList<>();
        try {
            File folder = new File(pathFolder);
            File[] listOfFiles = folder.listFiles();
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    LOG.debug("File {}", file.getAbsolutePath());
                    List<FbUrlToHtml> htmls = (List<FbUrlToHtml>) FileUtils.readObjectFromFile(file, false);
                    if (!htmls.isEmpty()) {
                        FbUrlToHtml firstRecord = htmls.get(0);
                        AccountStatus status = Parser.verifyResponseHtml(firstRecord.getRawUrl(), firstRecord.getRawHtml(), true);
                        if (status.equals(AccountStatus.ACTIVE)) {
                            FacebookObject fbObj = ParsingUtils.fromHtmltoFacebookObject(htmls);
                            if (fbObj != null) {
                                LOG.info("dsadsa");
                            }
                        }
                    } else {
                        LOG.info("File {} is empty", file.getAbsolutePath());
                    }

                } else if (file.isDirectory()) {
                    LOG.warn("Directory {}", file.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static void lookUpFacebookData() {
        String pathFolder = "data2/facebook";
        List<File> fbDataFiles = new ArrayList<>();
        try {
            File folder = new File(pathFolder);
            File[] listOfFiles = folder.listFiles();
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    LOG.debug("File {}", file.getAbsolutePath());
                    fbDataFiles.add(file);
                } else if (file.isDirectory()) {
                    LOG.warn("Directory {}", file.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

        List<List<File>> partitions = CustomPartition.partition(fbDataFiles, 100);
        int j = 0;
        for (List<File> partition : partitions) {
            StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html>\n"
                    + "<html>\n"
                    + "<head>\n"
                    + "  <title>Lookup Data</title>\n"
                    + "</head>\n"
                    + "\n"
                    + "<body>");
            sb.append("<script type=\"text/javascript\">\n"
                    + " function ResizeIframe(id){\n"
                    + "  var frame = document.getElementById(id);\n"
                    + "  frame.height = frame.contentWindow.document.body.scrollHeight + \"px\";\n"
                    + "  frame.width = frame.contentWindow.document.body.scrollWidth + \"px\";\n"
                    + " }\n"
                    + "</script>");
            for (File fbDataFile : partition) {
                sb.append("<h2>");
                sb.append(fbDataFile.getAbsoluteFile());
                sb.append("</h2>");
                sb.append("<br>");
                try {
                    List<FbUrlToHtml> htmls = (List<FbUrlToHtml>) FileUtils.readObjectFromFile(fbDataFile, false);
                    int i = 0;
                    for (FbUrlToHtml html : htmls) {
                        sb.append("<h3>");
                        sb.append(html.getRawUrl()).append(" - ").append(new Date(html.getCrawledTime()).toString());
                        sb.append("</h3>");
                        sb.append("<br>");
                        String filename = "out/raw/" + fbDataFile.getName() + "_" + i + ".htm";
                        String src = "raw/" + fbDataFile.getName() + "_" + i + ".htm";
                        String id = fbDataFile.getName() + "_" + i;
                        Files.write(new File(filename).toPath(), html.getRawHtml().getBytes());
                        sb.append("<iframe id=\"").append(id).append("\" onload=\"ResizeIframe('").append(id).append("')\" scrolling=\"no\" \n"
                                + " height=\"600\" src=\"").append(src).append("\" width=\"100%\" height=\"100%\">");
                        sb.append("</iframe>");
                        sb.append("<br>");
                        i++;
                    }
                } catch (IOException | ClassNotFoundException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
            sb.append("</body>\n"
                    + "\n"
                    + "</html>");
            String outFilename = "out/out_" + j + ".html";
            File htmlFile = new File(outFilename);
            try {
                Files.write(htmlFile.toPath(), sb.toString().getBytes());
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }
            j++;
        }
    }

    private static void lookUpGoogleData() {
        String pathFolder = "data2/google";
        List<File> gpDataFiles = new ArrayList<>();
        try {
            File folder = new File(pathFolder);
            File[] listOfFiles = folder.listFiles();
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    LOG.debug("File {}", file.getAbsolutePath());
                    gpDataFiles.add(file);
                } else if (file.isDirectory()) {
                    LOG.warn("Directory {}", file.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (File gpDataFile : gpDataFiles) {
            try {
                GooglePlusObject gpObject = (GooglePlusObject) FileUtils.readObjectFromFile(gpDataFile, false);
                LOG.info(gpObject.toString());
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }
}
