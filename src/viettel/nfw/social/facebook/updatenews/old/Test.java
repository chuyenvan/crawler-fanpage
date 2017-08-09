package viettel.nfw.social.facebook.updatenews.old;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class Test {

    private static final Logger LOG = LoggerFactory.getLogger(Test.class);

    public static void main(String[] args) {

//        getPageUrlsFromFile();
//        genAccounts();
        check();
    }

    private static void getPageUrlsFromFile() {
        String filename = "data2/update-news/pages.txt";
        Set<String> groupUrls = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (StringUtils.startsWithIgnoreCase(line, "p:https://m.facebook.com/")
                            || StringUtils.startsWithIgnoreCase(line, "p:https://www.facebook.com/")) {
                        String[] parts = StringUtils.split(line.trim(), "|");
                        String firstPos = parts[0];
                        String profileUrl = StringUtils.substring(firstPos, 2);
                        groupUrls.add(profileUrl);
                    }
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        for (String groupUrl : groupUrls) {
            System.out.println(groupUrl);
        }
    }

    private static void getGroupUrlsFromFile() {

    }

    private static void genAccounts() {
        String filename = "input/facebook-accounts-updatenews.txt";
        String filenameUA = "data2/useragent.txt";
        List<String> useragents = readUserAgents(filenameUA);
        List<String> accounts = new ArrayList<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    accounts.add(line);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        Random rand = new Random();
        for (String account : accounts) {
            int pos = rand.nextInt(useragents.size());
            String userAgent = useragents.get(pos);

            System.out.println(account + "\t" + userAgent);
        }
    }

    private static List<String> readUserAgents(String filename) {
        List<String> useragents = new ArrayList<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    useragents.add(temp);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return useragents;
    }

    private static void check() {
        String filename = "data2/update-news/mobile.txt";
        Set<String> useragents = new HashSet<>();
        try {
            try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
                String line;
                while ((line = br.readLine()) != null) {
                    String temp = line.trim();
                    useragents.add(temp);
                }
            }
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        LOG.info("{}", useragents.size());
    }
}
