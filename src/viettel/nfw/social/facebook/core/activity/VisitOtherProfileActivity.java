package viettel.nfw.social.facebook.core.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.entity.IActivity;

/**
 *
 * @author duongth5
 */
public class VisitOtherProfileActivity implements IActivity {

    private static final Logger LOG = LoggerFactory.getLogger(VisitOtherProfileActivity.class);

    public VisitOtherProfileActivity() {
    }

    private AccountStatus status;

    @Override
    public void run() {
        LOG.info("Run {}", CommentPostActivity.class.getName());
        AccountStatus retStatus = null;

        // set status
        status = retStatus;
    }

    @Override
    public AccountStatus getStatus() {
        return status;
    }

    @Override
    public void setMethod() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

//    public AccountStatus visitProfile(String profileUrl) {
//        try {
//            List<FbUrlToHtml> crawledData = new ArrayList<>();
//            LOG.info("Start crawl {}", profileUrl);
//
//            // crawl profile URL
//            String response = crawl(profileUrl, http, proxy);
//            writeActionsToFile(profileUrl, account.getUsername());
//            if (StringUtils.isEmpty(response)) {
//                LOG.warn("url: {} -- response: NULL", profileUrl);
//                return AccountStatus.KICKOUT_UNKNOWN;
//            }
//
//            AccountStatus kickoutType = Parser.verifyResponseHtml(profileUrl, response, true);
//            if (!kickoutType.equals(AccountStatus.ACTIVE)) {
//                return kickoutType;
//            }
//
//            Document profileHTMLDoc = Jsoup.parse(response);
//            crawledData.add(new FbUrlToHtml(profileUrl, response, System.currentTimeMillis()));
//
//            Map<String, String> foundProfileUrls = new HashMap<>();
//            foundProfileUrls.putAll(Parser.findProfileUrls(profileUrl, response));
//
//            Set<String> timelineUrls = Parser.getUrls(profileUrl, response, null, "Timeline", 0);
//            Set<String> aboutUrls = Parser.getUrls(profileUrl, response, null, "About", 0);
//            Set<String> friendsUrls = Parser.getUrls(profileUrl, response, null, "Friends", 0);
//            Set<String> likesUrls = Parser.getUrls(profileUrl, response, null, "Likes", 0);
//            Set<String> followingUrls = Parser.getUrls(profileUrl, response, null, "Following", 0);
//
//            Set<String> fullStoryUrls = new HashSet<>();
//            fullStoryUrls.addAll(Parser.getUrls(profileUrl, response, null, "Full Story", 0));
//            fullStoryUrls.addAll(Parser.getUrls(profileUrl, response, null, " Comments", 1));
//
//            if (!timelineUrls.isEmpty()) {
//                for (String timelineUrl : timelineUrls) {
//                    LOG.info("timelineUrl - {}", timelineUrl);
//                    String timelineRes = crawl(timelineUrl, http, proxy);
//                    writeActionsToFile(timelineUrl, account.getUsername());
//                    AccountStatus timelineKOT = Parser.verifyResponseHtml(timelineUrl, timelineRes, true);
//                    if (!timelineKOT.equals(AccountStatus.ACTIVE)) {
//                        writeCrawledDataAndOutlinks(crawledData, foundProfileUrls, profileUrl, account.getUsername());
//                        return timelineKOT;
//                    }
//                    crawledData.add(new FbUrlToHtml(timelineUrl, timelineRes, System.currentTimeMillis()));
//                    foundProfileUrls.putAll(Parser.findProfileUrls(timelineUrl, timelineRes));
//                    Set<String> timelineFullStoryUrls = Parser.getUrls(timelineUrl, timelineRes, null, "Full Story", 0);
//
//                    List<String> tempList = new ArrayList<>();
//                    tempList.addAll(timelineFullStoryUrls);
//                    if (tempList.size() > 0) {
//                        Collections.shuffle(tempList);
//                        String fullStoryWillVisit = tempList.get(0);
//                        String fullStoryRes = crawl(fullStoryWillVisit, http, proxy);
//                        writeActionsToFile(fullStoryWillVisit, account.getUsername());
//                        AccountStatus fullStoryKOT = Parser.verifyResponseHtml(fullStoryWillVisit, fullStoryRes, true);
//                        if (!fullStoryKOT.equals(AccountStatus.ACTIVE)) {
//                            writeCrawledDataAndOutlinks(crawledData, foundProfileUrls, profileUrl, account.getUsername());
//                            return fullStoryKOT;
//                        }
//                        crawledData.add(new FbUrlToHtml(fullStoryWillVisit, fullStoryRes, System.currentTimeMillis()));
//                        foundProfileUrls.putAll(Parser.findProfileUrls(fullStoryWillVisit, fullStoryRes));
//                    }
//                }
//            }
//            if (!aboutUrls.isEmpty()) {
//                for (String aboutUrl : aboutUrls) {
//                    LOG.info("aboutUrl - {}", aboutUrl);
//                    String aboutRes = crawl(aboutUrl, http, proxy);
//                    writeActionsToFile(aboutUrl, account.getUsername());
//                    AccountStatus aboutKOT = Parser.verifyResponseHtml(aboutUrl, aboutRes, true);
//                    if (!aboutKOT.equals(AccountStatus.ACTIVE)) {
//                        writeCrawledDataAndOutlinks(crawledData, foundProfileUrls, profileUrl, account.getUsername());
//                        return aboutKOT;
//                    }
//                    crawledData.add(new FbUrlToHtml(aboutUrl, aboutRes, System.currentTimeMillis()));
//                    foundProfileUrls.putAll(Parser.findProfileUrls(aboutUrl, aboutRes));
//                }
//            }
//            // this case for type PAGE or GROUP
//            if (!fullStoryUrls.isEmpty()) {
//                List<String> tempList = new ArrayList<>();
//                tempList.addAll(fullStoryUrls);
//                if (tempList.size() > 0) {
//                    Collections.shuffle(tempList);
//                    String fullStoryWillVisit = tempList.get(0);
//                    String fullStoryRes = crawl(fullStoryWillVisit, http, proxy);
//                    writeActionsToFile(fullStoryWillVisit, account.getUsername());
//                    AccountStatus fullStoryKOT = Parser.verifyResponseHtml(fullStoryWillVisit, fullStoryRes, true);
//                    if (!fullStoryKOT.equals(AccountStatus.ACTIVE)) {
//                        writeCrawledDataAndOutlinks(crawledData, foundProfileUrls, profileUrl, account.getUsername());
//                        return fullStoryKOT;
//                    }
//                    crawledData.add(new FbUrlToHtml(fullStoryWillVisit, fullStoryRes, System.currentTimeMillis()));
//                    foundProfileUrls.putAll(Parser.findProfileUrls(fullStoryWillVisit, fullStoryRes));
//                }
//            }
//
//            // find log out link
//            try {
//                Elements a_href = profileHTMLDoc.select("a[href]");
//                for (Element link : a_href) {
//                    String strLink = link.attr("href");
//                    if (StringUtils.startsWith(strLink, "/logout.php")) {
//                        URI profileUri = new URI(profileUrl);
//                        URI logoutUri = profileUri.resolve(strLink);
//                        this.logoutUrl = logoutUri.toString();
//                        LOG.info("Logout URL {}", this.logoutUrl);
//                    }
//                }
//            } catch (Exception ex) {
//                LOG.error(ex.getMessage(), ex);
//            }
//
//            // check crawled data
////            if (!crawledData.isEmpty()) {
////                LOG.info("crawledData size {} - profile {}", crawledData.size(), profileUrl);
////                String fIdUn = "df" + String.valueOf(System.currentTimeMillis());
////                String filename = "storage/facebook/" + fIdUn + "_" + String.valueOf(System.currentTimeMillis()) + ".nfbo";
////                FileUtils.writeObject2File(new File(filename), crawledData, false);
////            }
//            writeCrawledDataAndOutlinks(crawledData, foundProfileUrls, profileUrl, account.getUsername());
//        } catch (Exception ex) {
//            LOG.error(ex.getMessage(), ex);
//        }
//        return AccountStatus.ACTIVE;
//    }
//
//    public void writeActionsToFile(String url, String account) {
//        try {
//            String message = account + "|" + url + "|" + String.valueOf(System.currentTimeMillis());
//            Run.afwAccountActions.append(message + "\n");
//        } catch (Exception ex) {
//            LOG.error(ex.getMessage(), ex);
//        }
//    }
//
//    public void writeCrawledDataAndOutlinks(List<FbUrlToHtml> crawledData, Map<String, String> foundProfileUrls, String profileUrl, String account) {
//        try {
//            if (!crawledData.isEmpty()) {
//                LOG.info("crawledData size {} - profile {}", crawledData.size(), profileUrl);
//                String fIdUn = "df" + String.valueOf(System.currentTimeMillis());
//                String filename = "storage/facebook/" + fIdUn + "_" + String.valueOf(System.currentTimeMillis()) + ".nfbo";
//                FileUtils.writeObject2File(new File(filename), crawledData, false);
//            }
//            if (!MapUtils.isEmpty(foundProfileUrls)) {
//                for (Map.Entry<String, String> entrySet : foundProfileUrls.entrySet()) {
//                    String key = entrySet.getKey();
//                    String value = entrySet.getValue();
//                    String message = account + "|" + key + "|" + value;
//                    Run.afwOutlinks.append(message + "\n");
//                }
//            }
//        } catch (Exception ex) {
//            LOG.error(ex.getMessage(), ex);
//        }
//    }
}
