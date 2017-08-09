package viettel.nfw.social.common;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.Constant;
import viettel.nfw.social.utils.CustomPartition;
import viettel.nfw.social.utils.TCrawler;
import vn.viettel.engine.utils.Crawler;

/**
 *
 * @author duongth5
 */
public class ServiceOutlinks {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceOutlinks.class);

    private static String serviceHost = "";

    private static final String SVC_PATH_ADD = "/add/";

    private static final String SVC_PATH_CRAWLED = "/crawled/";

    private static final String SVC_PATH_ERROR = "/error/";

    private static final String SVC_PATH_NEXT = "/next/";

    private static final String SVC_PATH_ACCOUNT = "/account/";

    private static final String SVC_PATH_LOCK = "/lock/";

    private static final String SVC_PATH_FRIEND = "/friend/";

    private static final String SVC_PATH_LOG = "/log/";

    private static final String SVC_PATH_TOPIC = "/topic/";

    private static final String SVC_PATH_PRIORITY = "/priority/";

    static {
        ApplicationConfiguration.getInstance().initilize(Constant.COMMON_CONF_FILE_PATH);
        String masterHostname = ApplicationConfiguration.getInstance().getConfiguration("server.master.hostname");
        String masterPort = ApplicationConfiguration.getInstance().getConfiguration("server.master.port");
        serviceHost = "http://" + masterHostname + ":" + masterPort;
    }

//    public static void main(String[] args){
//        String url = genSvcAddUrl("duongth5", "http://tanghaiduong.com/index.html", "http://tanghaiduong.com");
//        System.out.println(url);
//    }
    public static String getAccountPage() {
        String url = serviceHost + SVC_PATH_ACCOUNT;
        String response = TCrawler.getContentFromUrl(url);
        return response;
    }

    public static String getLockPage() {
        String url = serviceHost + SVC_PATH_LOCK;
        String response = TCrawler.getContentFromUrl(url);
        return response;
    }

    public static List<String> getLockedAccounts() {
        List<String> lockedAccounts = new ArrayList<>();
        String svcUrl = genSvcGetLockedAccounts();
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - getLockedAccounts - {}", ret);
        if (!StringUtils.isEmpty(ret)) {
            String[] parts = StringUtils.split(ret.trim(), ",");
            lockedAccounts.addAll(Arrays.asList(parts));
        }
        return lockedAccounts;
    }

    public static void addLockedAccount(String accountName, String message) {
        String svcUrl = genSvcAddLockedAccount(accountName, message);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - add LockedAccount - {}", ret);
    }

    public static void sendLog(String accountName, String logMessage) {
        String svcUrl = genSvcSendLog(accountName, logMessage);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - sendLog - {}", ret);
    }

    public static void getTopic(String topicName) {
        String svcUrl = genSvcGetTopic(topicName);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - getTopic - {}", ret);
    }

    public static void sendAddFriend(String accountName, String friendAccount) {
        String svcUrl = genSvcAddFriend(accountName, friendAccount);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - sendAddFriend - {}", ret);
    }

    public static void checkIsFriend(String accountName, String friendAccount) {
        String svcUrl = genSvcCheckIsFriend(accountName, friendAccount);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - checkIsFriend - {}", ret);
    }

    public static String getAccount(String ip) {
        String svcUrl = genSvcGetAccount(ip);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - getAccount - {}", ret);
        return ret;
    }

    public static void deleteAccount(String username, String accountType) {
        String svcUrl = genSvcDeleteAccount(username, accountType);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - deleteAccount {} - ret {}", username, ret);
    }

    public static void pushAccountToMaster(String accType, String accName, String accPwd, String userAgent, String ip) {
        Map<String, String> param = new HashMap<>();
        param.put("accountType", accType);
        param.put("accountName", accName);
        param.put("password", accPwd);
        param.put("userAgent", userAgent);
        param.put("usingIp", ip);

        String url = serviceHost + SVC_PATH_ACCOUNT;
        TCrawler.postContentFromUrl(url, param);
    }

    public static void addOutlinkPriority(String url, boolean isForce) {
        String svcUrl = genSvcAddOutlinkPriority(url, isForce);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - addOutlinkPriority - ret {} - url {}", ret, url);
    }

    public static String genSvcAddOutlinkPriority(String addUrl, boolean isForce) {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("url", "UTF-8") + "=" + URLEncoder.encode(addUrl, "UTF-8"));
            String isForceStr;
            if (isForce) {
                isForceStr = "true";
            } else {
                isForceStr = "false";
            }
            paramList.add(URLEncoder.encode("isForced", "UTF-8") + "=" + URLEncoder.encode(isForceStr, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_PRIORITY + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    public static String getNextUrl(String host, String account) {
        String svcUrl = genSvcNextUrl(host, account);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - getNextUrl - {}", ret);
        return ret;
    }

    public static void addCrawledUrl(String account, String url) {
        String svcUrl = genSvcCrawledUrl(account, url, true, null);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - addCrawledUrl - {}", ret);
    }

    public static void addCrawledUrl(String account, String url, String time) {
        String svcUrl = genSvcCrawledUrl(account, url, false, time);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - addCrawledUrl - {}", ret);
    }

    public static void addOutlinks(Set<String> urls) {
        LOG.info("Size of outlinks: " + urls.size());

        List<String> listUrls = new ArrayList<>();
        listUrls.addAll(urls);
        List<List<String>> partition = CustomPartition.partition(listUrls, 3);

        for (List<String> item : partition) {
            String svcUrl = genSvcAddUrl(item);
            String ret = Crawler.getContentFromUrl(svcUrl);
            LOG.info("SVC - addOutlinks - {}", ret);
        }
    }

    public static boolean addOutLink(String account, String originalUrl, String normalizedUrl) {
        String svcUrl = genSvcAddUrl(account, originalUrl, normalizedUrl);
        String ret = Crawler.getContentFromUrl(svcUrl);
        // LOG.info("SVC - addOutlinks - {}", ret);
        return "1".equals(ret);
    }

    public static void sendError(String account, String message) {
        String svcUrl = genSvcErrorUrl(account, message);
        String ret = Crawler.getContentFromUrl(svcUrl);
        LOG.info("SVC - sendError - {}", ret);
    }

    public static String genSvcGetAccount(String ip) {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("action", "UTF-8") + "=" + URLEncoder.encode("get", "UTF-8"));
            paramList.add(URLEncoder.encode("usingIp", "UTF-8") + "=" + URLEncoder.encode(ip, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_ACCOUNT + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    private static String genSvcAddFriend(String accountName, String friendAccount) {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("action", "UTF-8") + "=" + URLEncoder.encode("add", "UTF-8"));
            paramList.add(URLEncoder.encode("account", "UTF-8") + "=" + URLEncoder.encode(accountName, "UTF-8"));
            paramList.add(URLEncoder.encode("friendAccount", "UTF-8") + "=" + URLEncoder.encode(friendAccount, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_FRIEND + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    private static String genSvcCheckIsFriend(String accountName, String friendAccount) {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("action", "UTF-8") + "=" + URLEncoder.encode("is_friend", "UTF-8"));
            paramList.add(URLEncoder.encode("account", "UTF-8") + "=" + URLEncoder.encode(accountName, "UTF-8"));
            paramList.add(URLEncoder.encode("friendAccount", "UTF-8") + "=" + URLEncoder.encode(friendAccount, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_FRIEND + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    private static String genSvcDeleteAccount(String username, String accountType) {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("action", "UTF-8") + "=" + URLEncoder.encode("delete", "UTF-8"));
            paramList.add(URLEncoder.encode("accountName", "UTF-8") + "=" + URLEncoder.encode(username, "UTF-8"));
            paramList.add(URLEncoder.encode("accountType", "UTF-8") + "=" + URLEncoder.encode(accountType, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_ACCOUNT + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    public static String genSvcNextUrl(String host, String account) {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("host", "UTF-8") + "=" + URLEncoder.encode(host, "UTF-8"));
            paramList.add(URLEncoder.encode("account", "UTF-8") + "=" + URLEncoder.encode(account, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_NEXT + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    public static String genSvcAddUrl(List<String> profileUrls) {
        String url = "";
        try {
            String profileUrlsStr = StringUtils.join(profileUrls, ";");
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("urls", "UTF-8") + "=" + URLEncoder.encode(profileUrlsStr, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_ADD + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    public static String genSvcCrawledUrl(String account, String crawledUrl, boolean isCurrent, String inputTime) {
        String url = "";
        try {
            // http://192.168.6.81:1125/crawled/?account=duongth5&time=1231231233&url=http://facebook.com/thientoi4
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("account", "UTF-8") + "=" + URLEncoder.encode(account, "UTF-8"));
            String time;
            if (isCurrent) {
                time = String.valueOf(System.currentTimeMillis());
            } else {
                time = inputTime;
            }
            paramList.add(URLEncoder.encode("time", "UTF-8") + "=" + URLEncoder.encode(time, "UTF-8"));
            paramList.add(URLEncoder.encode("url", "UTF-8") + "=" + URLEncoder.encode(crawledUrl, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_CRAWLED + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    public static String genSvcErrorUrl(String account, String message) {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("account", "UTF-8") + "=" + URLEncoder.encode(account, "UTF-8"));
            paramList.add(URLEncoder.encode("time", "UTF-8") + "=" + URLEncoder.encode(String.valueOf(System.currentTimeMillis()), "UTF-8"));
            paramList.add(URLEncoder.encode("message", "UTF-8") + "=" + URLEncoder.encode(message, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_ERROR + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    private static String genSvcAddUrl(String account, String originalUrl, String normalizedUrl) {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("account", "UTF-8") + "=" + URLEncoder.encode(account, "UTF-8"));
            paramList.add(URLEncoder.encode("originalUrl", "UTF-8") + "=" + URLEncoder.encode(originalUrl, "UTF-8"));
            paramList.add(URLEncoder.encode("normalizedUrl", "UTF-8") + "=" + URLEncoder.encode(normalizedUrl, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_ADD + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    private static String genSvcGetLockedAccounts() {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("action", "UTF-8") + "=" + URLEncoder.encode("GET", "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_LOCK + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    private static String genSvcAddLockedAccount(String accountName, String message) {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("action", "UTF-8") + "=" + URLEncoder.encode("ADD", "UTF-8"));
            paramList.add(URLEncoder.encode("accountName", "UTF-8") + "=" + URLEncoder.encode(accountName, "UTF-8"));
            paramList.add(URLEncoder.encode("lockedMessage", "UTF-8") + "=" + URLEncoder.encode(message, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_LOCK + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    private static String genSvcGetTopic(String topicName) {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("action", "UTF-8") + "=" + URLEncoder.encode("get", "UTF-8"));
            paramList.add(URLEncoder.encode("topic", "UTF-8") + "=" + URLEncoder.encode(topicName, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_TOPIC + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }

    private static String genSvcSendLog(String accountName, String logMessage) {
        String url = "";
        try {
            List<String> paramList = new ArrayList<>();
            paramList.add(URLEncoder.encode("action", "UTF-8") + "=" + URLEncoder.encode("add", "UTF-8"));
            paramList.add(URLEncoder.encode("account", "UTF-8") + "=" + URLEncoder.encode(accountName, "UTF-8"));
            paramList.add(URLEncoder.encode("log", "UTF-8") + "=" + URLEncoder.encode(logMessage, "UTF-8"));
            // Build parameters list
            StringBuilder result = new StringBuilder();
            for (String param : paramList) {
                if (result.length() == 0) {
                    result.append(param);
                } else {
                    result.append("&").append(param);
                }
            }

            String path = result.toString();
            url = serviceHost + SVC_PATH_LOG + "?" + path;

        } catch (UnsupportedEncodingException ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return url;
    }
}
