package viettel.nfw.gov;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class RunCrawlGov {

    private static final Logger LOG = LoggerFactory.getLogger(RunCrawlGov.class);
    private static final int MAX_CAPACITY = 3000000;
    public static final String PROXY_STRING = "192.168.4.13:3128";
    public static final String USER_AGENT_DF = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:33.0) Gecko/20100101 Firefox/33.0";
    private static final BlockingQueue<String> urlsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
    private static final ConcurrentHashMap<String, Data> mapsCrawledGovInfo = new ConcurrentHashMap<>();

    public static void main(String[] args) {

        List<String> govUrls = new ArrayList<>();
        String filename = "data2/gov/list-gov-whois.txt";
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    govUrls.add(temp);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (String govUrl : govUrls) {
            urlsQueue.add(govUrl);
        }

        LOG.info("DONE init urls to queue");
        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (int i = 0; i < 5; i++) {
            String threadName = "Crawler_" + i;
            WorkerImpl worker = new WorkerImpl(threadName);
            new Thread(worker).start();
        }

        while (urlsQueue.size() > 0) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }

        try {
            Thread.sleep(5 * 1000);
        } catch (InterruptedException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        LOG.info("START write result");

        try {
            File outFile = new File("gov-out.txt");
            if (!outFile.exists()) {
                outFile.createNewFile();
            }

            FileWriter fw = new FileWriter(outFile.getAbsoluteFile());
            try (BufferedWriter bw = new BufferedWriter(fw)) {
                for (String govUrl : govUrls) {
                    String format = "%s\t%s\t%s";
                    String retString;
                    Data govData = mapsCrawledGovInfo.get(govUrl);
                    if (govData == null) {
                        retString = String.format(format, govUrl, "null", "null");
                    } else {
                        String title;
                        if (StringUtils.isNotEmpty(govData.title.trim())) {
                            title = govData.title.trim().replaceAll("(\r\n|\n)", " ");
                        } else {
                            title = "null";
                        }
                        String footer;
                        if (StringUtils.isNotEmpty(govData.footer.trim())) {
                            footer = govData.footer.trim().replaceAll("(\r\n|\n)", " ");
                        } else {
                            footer = "null";
                        }
                        retString = String.format(format, govUrl, title, footer);
                    }
                    bw.write(retString);
                    bw.write("\n");
                }
            }

            LOG.info("DONE!!");

        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        try {
            Thread.sleep(2 * 60 * 1000);
        } catch (InterruptedException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private static class Data {

        public String title;
        public String footer;
    }

    private static class WorkerImpl implements Runnable {

        private final String threadName;

        public WorkerImpl(String threadName) {
            this.threadName = threadName;
        }

        @Override
        public void run() {
            Thread.currentThread().setName(threadName);
            PhantomJSDriver driver = startDriver(null, USER_AGENT_DF);
            while (true) {
                String nextUrl = urlsQueue.poll();
                if (StringUtils.isEmpty(nextUrl)) {
                    stopDriver(driver);
                } else {
                    try {
                        try {
                            driver.get("http://" + nextUrl);
                        } catch (TimeoutException ex) {
                            driver.navigate().refresh();
                        }
                        Thread.sleep(600);

                        Data data = new Data();
                        // get title
                        String title = driver.getTitle();
                        data.title = title;
                        // find footer
                        boolean isOK = false;
                        String footerText = "";
                        List<WebElement> elHasIds = driver.findElements(By.xpath("//*[@id]"));
                        if (!elHasIds.isEmpty()) {
                            for (WebElement elHasId : elHasIds) {
                                String idStr = elHasId.getAttribute("id");
                                if (StringUtils.contains(idStr, "footer")) {
                                    footerText = elHasId.getText();
                                    isOK = true;
                                    break;
                                }
                            }
                        }

                        if (!isOK) {
                            List<WebElement> elHasClasses = driver.findElements(By.xpath("//*[@class]"));
                            if (!elHasClasses.isEmpty()) {
                                for (WebElement elHasClass : elHasClasses) {
                                    String idStr = elHasClass.getAttribute("class");
                                    if (StringUtils.contains(idStr, "footer")) {
                                        footerText = elHasClass.getText();
                                        isOK = true;
                                        break;
                                    }
                                }
                            }
                        }

                        if (!isOK) {
                            List<WebElement> elBodys = driver.findElements(By.tagName("body"));
                            if (!elBodys.isEmpty()) {
                                WebElement elBody = elBodys.get(0);
                                String fullText = elBody.getText();
                                int fullTextLength = fullText.length();
                                if (fullTextLength > 1000) {
                                    footerText = StringUtils.substring(fullText, fullTextLength - 1000);
                                }
                            }
                        }

                        data.footer = footerText;
                        mapsCrawledGovInfo.put(nextUrl, data);

                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
            }
        }

        private static PhantomJSDriver startDriver(String proxyString, String userAgent) {
            DesiredCapabilities cap = DesiredCapabilities.phantomjs();
            if (proxyString != null) {
                org.openqa.selenium.Proxy p = new org.openqa.selenium.Proxy();
                p.setHttpProxy(proxyString).setFtpProxy(proxyString).setSslProxy(proxyString);
                cap.setCapability(CapabilityType.PROXY, p);
            }
            cap.setJavascriptEnabled(true);
            cap.setCapability("phantomjs.page.settings.userAgent", userAgent);

            PhantomJSDriver driver = new PhantomJSDriver(cap);
            try {
                driver.manage().deleteAllCookies();
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
            driver.manage().timeouts().pageLoadTimeout(1, TimeUnit.MINUTES);
            driver.manage().timeouts().setScriptTimeout(40, TimeUnit.SECONDS);
            driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

            return driver;
        }

        private static void stopDriver(PhantomJSDriver driver) {
            try {
                driver.quit();
            } catch (Exception ex) {
                LOG.error("Error in Stop Driver", ex);
            }
        }

    }
}
