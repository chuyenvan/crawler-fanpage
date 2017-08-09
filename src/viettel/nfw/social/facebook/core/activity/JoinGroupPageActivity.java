package viettel.nfw.social.facebook.core.activity;

import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.FacebookAction;
import static viettel.nfw.social.facebook.core.FacebookAction.crawl;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.entity.IActivity;
import viettel.nfw.social.facebook.htmlobj.FacebookHeaderBar;
import viettel.nfw.social.facebook.htmlobj.FacebookMyBookmarkMenu;
import viettel.nfw.social.facebook.core.HttpRequest;

/**
 *
 * @author duongth5
 */
public class JoinGroupPageActivity implements IActivity {

    private static final Logger LOG = LoggerFactory.getLogger(JoinGroupPageActivity.class);

    private final FacebookAction fbAction;
    private MethodType chosenMethod;
    private static final Random randomize = new Random();
    private AccountStatus status;

    private AccountStatus doBySearchGroup(FacebookAction fbAction) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    private static enum MethodType {

        LIKE_PAGE_SUGGESTED, JOIN_GROUP_SUGGESTED, LIKE_PAGE_SEARCH, JOIN_GROUP_SEARCH
    }

    public JoinGroupPageActivity(FacebookAction fbAction) {
        this.fbAction = fbAction;
    }

    @Override
    public void run() {
        LOG.info("Run {}", CommentPostActivity.class.getName());
        AccountStatus retStatus = null;
        setMethod();

        switch (chosenMethod) {
            case LIKE_PAGE_SUGGESTED:
                retStatus = doByPagesSuggested(fbAction);
                break;
            case JOIN_GROUP_SUGGESTED:
                retStatus = doByGroupSuggested(fbAction);
                break;
            case LIKE_PAGE_SEARCH:
                retStatus = doBySearchPage(fbAction);
                break;
            case JOIN_GROUP_SEARCH:
                retStatus = doBySearchGroup(fbAction);
                break;
        }

        // set status
        status = retStatus;
    }

    @Override
    public AccountStatus getStatus() {
        return status;
    }

    @Override
    public void setMethod() {
        int sizeOfMethod = MethodType.values().length;
        int pos = randomize.nextInt(sizeOfMethod);
        chosenMethod = MethodType.values()[pos];
    }

    private AccountStatus doBySearchPage(FacebookAction fbAction) {
        AccountStatus accStatus = AccountStatus.ACTIVE;
        String homeUrl = fbAction.getHomeUrl();
        HttpRequest http = fbAction.getHttp();
        Proxy proxy = fbAction.getProxy();

        try {
            if (StringUtils.isEmpty(homeUrl)) {
                homeUrl = "https://m.facebook.com";
            }
            String homeResponse = crawl(homeUrl, http, proxy);
            AccountStatus homeResponseKOT = Parser.verifyResponseHtml(homeUrl, homeResponse, false);
            if (!homeResponseKOT.equals(AccountStatus.ACTIVE)) {
                accStatus = homeResponseKOT;
            } else {

                String searchText = "Hội những người";
                // TODO get Text from master

                FacebookHeaderBar headerBar = Parser.getHeaderBarOfCurrentHtml(new URI(homeUrl), homeResponse);
                Element searchForm = headerBar.searchForm;
                if (searchForm != null) {
                    String attrMethod = searchForm.attr("method");
                    if (StringUtils.equalsIgnoreCase(attrMethod, "get")) {

                    } else {
                        LOG.warn("Search form has method {}", attrMethod);
                    }
                } else {
                    LOG.warn("Cannot find search form!");
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return accStatus;
    }

    private AccountStatus doByPagesSuggested(FacebookAction fbAction) {
        AccountStatus accStatus = AccountStatus.ACTIVE;
        String homeUrl = fbAction.getHomeUrl();
        HttpRequest http = fbAction.getHttp();
        Proxy proxy = fbAction.getProxy();

        try {
            if (StringUtils.isEmpty(homeUrl)) {
                homeUrl = "https://m.facebook.com";
            }
            String homeResponse = crawl(homeUrl, http, proxy);
            AccountStatus homeResponseKOT = Parser.verifyResponseHtml(homeUrl, homeResponse, false);
            if (!homeResponseKOT.equals(AccountStatus.ACTIVE)) {
                accStatus = homeResponseKOT;
            } else {
                FacebookMyBookmarkMenu myBookmarkMenu = Parser.getBookmarkMenuInHome(new URI(homeUrl), homeResponse);
                Map<String, URI> mapUris = myBookmarkMenu.mapUris;
                URI pagesUri = mapUris.get("Pages");
                if (pagesUri != null) {
                    // go to Pages tab
                    String pagesResponse = crawl(pagesUri.toString(), http, proxy);
                    AccountStatus pagesResponseKOT = Parser.verifyResponseHtml(pagesUri.toString(), pagesResponse, true);
                    if (!pagesResponseKOT.equals(AccountStatus.ACTIVE)) {
                        accStatus = pagesResponseKOT;
                    } else {
                        List<String> suggestedPages = new ArrayList<>();
                        suggestedPages.addAll(Parser.getUrls(pagesUri.toString(), pagesResponse, null, "Like", 0));
                        if (suggestedPages.isEmpty()) {
                            LOG.warn("Cannot find any suggested pages!");
                        } else {
                            int randPos = randomize.nextInt(suggestedPages.size());
                            String willLikePageUrl = suggestedPages.get(randPos);
                            String likePageResponse = crawl(willLikePageUrl, http, proxy);
                            LOG.info("LIKE PAGE: {}", willLikePageUrl);
                            AccountStatus likePageResponeKOT = Parser.verifyResponseHtml(willLikePageUrl, likePageResponse, true);
                            if (!likePageResponeKOT.equals(AccountStatus.ACTIVE)) {
                                LOG.info("LIKE PAGE: NOT OK");
                                accStatus = likePageResponeKOT;
                            } else {
                                LOG.info("LIKE PAGE: OK");
                                accStatus = AccountStatus.DO_JOB_OK;

                                String nextHomeUrl = getHomeUrl(new URI(willLikePageUrl), likePageResponse);
                                if (StringUtils.isNotEmpty(nextHomeUrl)) {
                                    fbAction.setHomeUrl(nextHomeUrl);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

        return accStatus;
    }

    private AccountStatus doByGroupSuggested(FacebookAction fbAction) {
        AccountStatus accStatus = AccountStatus.ACTIVE;
        String homeUrl = fbAction.getHomeUrl();
        HttpRequest http = fbAction.getHttp();
        Proxy proxy = fbAction.getProxy();

        try {
            if (StringUtils.isEmpty(homeUrl)) {
                homeUrl = "https://m.facebook.com";
            }
            String homeResponse = crawl(homeUrl, http, proxy);
            AccountStatus homeResponseKOT = Parser.verifyResponseHtml(homeUrl, homeResponse, false);
            if (!homeResponseKOT.equals(AccountStatus.ACTIVE)) {
                accStatus = homeResponseKOT;
            } else {
                FacebookMyBookmarkMenu myBookmarkMenu = Parser.getBookmarkMenuInHome(new URI(homeUrl), homeResponse);
                Map<String, URI> mapUris = myBookmarkMenu.mapUris;
                URI nextUri = mapUris.get("Groups");
                if (nextUri != null) {
                    // go to Groups tab
                    String groupsResponse = crawl(nextUri.toString(), http, proxy);
                    AccountStatus groupsResponseKOT = Parser.verifyResponseHtml(nextUri.toString(), groupsResponse, true);
                    if (!groupsResponseKOT.equals(AccountStatus.ACTIVE)) {
                        accStatus = groupsResponseKOT;
                    } else {
                        List<String> suggestedGroups = new ArrayList<>();
                        suggestedGroups.addAll(Parser.getUrls(nextUri.toString(), groupsResponse, null, "Join", 0));
                        if (suggestedGroups.isEmpty()) {
                            LOG.warn("Cannot find any suggested groups!");
                        } else {
                            int randPos = randomize.nextInt(suggestedGroups.size());
                            String willJoinGroupUrl = suggestedGroups.get(randPos);
                            String joinGroupResponse = crawl(willJoinGroupUrl, http, proxy);
                            LOG.info("JOIN GROUP: {}", willJoinGroupUrl);
                            AccountStatus joinGroupResponeKOT = Parser.verifyResponseHtml(willJoinGroupUrl, joinGroupResponse, true);
                            if (!joinGroupResponeKOT.equals(AccountStatus.ACTIVE)) {
                                LOG.info("JOIN GROUP: NOT OK");
                                accStatus = joinGroupResponeKOT;
                            } else {
                                LOG.info("JOIN GROUP: OK");
                                accStatus = AccountStatus.DO_JOB_OK;

                                String nextHomeUrl = getHomeUrl(new URI(willJoinGroupUrl), joinGroupResponse);
                                if (StringUtils.isNotEmpty(nextHomeUrl)) {
                                    fbAction.setHomeUrl(nextHomeUrl);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }

        return accStatus;
    }

    private static String getHomeUrl(URI uri, String rawHtml) {
        String homeUrl = "";
        try {
            FacebookHeaderBar headerBar = Parser.getHeaderBarOfCurrentHtml(uri, rawHtml);
            Map<String, URI> mapUris = headerBar.mapUris;
            if (MapUtils.isNotEmpty(mapUris)) {
                URI homeUri = mapUris.get("Home");
                if (homeUri == null) {
                    homeUri = mapUris.get("Facebook Logo");
                }
                if (homeUri != null) {
                    homeUrl = homeUri.toString();
                } else {
                    homeUrl = "https://m.facebook.com";
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return homeUrl;
    }

}
