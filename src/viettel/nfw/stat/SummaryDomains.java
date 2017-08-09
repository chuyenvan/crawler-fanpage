package viettel.nfw.stat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.Funcs;
import vn.itim.engine.common.SiteUtils;

/**
 *
 * @author duongth5
 */
public class SummaryDomains {

    private static final Logger LOG = LoggerFactory.getLogger(SummaryDomains.class);

    public static void main(String[] args) {
        String path = args[0];
        if (StringUtils.isEmpty(path)) {
            LOG.error("Please input a file");
        } else {
            LOG.info("Starting ... {}", path);
            process(path);
            LOG.info("DONE!");
        }
    }

    private static void process(String path) {

        Set<String> domains = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(path)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (StringUtils.isNotEmpty(line.trim())) {
                        try {
                            String host = getHost(line.trim());
                            host = SiteUtils.getSiteForGrouping(host);
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
            FileUtils.write(new File("domains-" + String.valueOf(System.currentTimeMillis()) + ".txt"), domains);
        } catch (FileNotFoundException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        Funcs.sleep(3000);

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
}
