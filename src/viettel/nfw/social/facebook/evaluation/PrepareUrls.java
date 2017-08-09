package viettel.nfw.social.facebook.evaluation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.TCrawler;

/**
 *
 * @author duongth5
 */
public class PrepareUrls {

    private static final Logger LOG = LoggerFactory.getLogger(PrepareUrls.class);

    public static void main(String[] args) {
//        splitToGetUrls();
        pushEvalUrls();
    }

    private static void splitToGetUrls() {
        List<String> urls = new ArrayList<>();
        String filename = "data2/sensitive/bai3_lan2_from_trangdth.txt";

        File file = new File(filename);
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = StringUtils.split(line.trim(), "|");
                    urls.add(parts[0]);
                }
            }
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
        for (String url : urls) {
            System.out.println(url);
        }
    }

    private static void pushEvalUrls() {
        List<String> urls = new ArrayList<>();
        String filename = "data2/update-news/news/groupUNs.txt";

        File file = new File(filename);
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    urls.add(line);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (String bai2Url : urls) {
            LOG.info(bai2Url);
            try {
                String ret = TCrawler.getContentFromUrl("http://localhost:1155/eval/?url=" + URLEncoder.encode(bai2Url, "UTF-8"));
                LOG.info(ret);
            } catch (UnsupportedEncodingException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }
}
