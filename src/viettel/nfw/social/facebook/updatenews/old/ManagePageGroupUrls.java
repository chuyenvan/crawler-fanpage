package viettel.nfw.social.facebook.updatenews.old;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class ManagePageGroupUrls implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ManagePageGroupUrls.class);

    @Override
    public void run() {

        while (true) {
            try {
                doJob();
                long delay = 3 * 60 * 60 * 1000; // 3 hours
                Thread.sleep(delay);
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    private void doJob() {
        // query total urls
        Set<String> totalUrls = getTotalPageGroupUrls();
        // check conditioner
        Set<String> vietnamesePageUrls = getUrlsStorage("result/updatenews/page/vietnamese");
        Set<String> foreignPageUrls = getUrlsStorage("result/updatenews/page/foreign");
        Set<String> autogenPageUrls = getUrlsStorage("result/updatenews/page/autogen");
        Set<String> unknownPageUrls = getUrlsStorage("result/updatenews/page/unknown");

        Set<String> toCrawlUrls = new HashSet<>();
        for (String url : totalUrls) {
            if (foreignPageUrls.contains(url)) {
                continue;
            }
            if (autogenPageUrls.contains(url)) {
                continue;
            }
            if (unknownPageUrls.contains(url)) {
                continue;
            }
            toCrawlUrls.add(url);
        }

        // push to BIG QUEUE
        for (String toCrawlUrl : toCrawlUrls) {
            RunUpdateNewsOld.urlsQueue.add(toCrawlUrl);
        }
    }

    private static Set<String> getTotalPageGroupUrls() {
        String totalFilename = "input/updatenews/total_pages_20150612.txt";
        Set<String> totalUrls = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(totalFilename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    totalUrls.add(line.trim());
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return totalUrls;
    }

    private static Set<String> getUrlsStorage(String storagePath) {
        Set<String> urls = new HashSet<>();
        try {
            File folder = new File(storagePath);
            File[] listOfFiles = folder.listFiles();
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    LOG.info("File {}", file.getAbsolutePath());
                    try {
                        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                String temp = line.trim();
                                urls.add(temp);
                            }
                        }
                    } catch (IOException ex) {
                        LOG.error("Failed to read " + file.getAbsolutePath() + " file", ex);
                    }
                } else if (file.isDirectory()) {
                    LOG.warn("Directory {}", file.getAbsolutePath());
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return urls;
    }

}
