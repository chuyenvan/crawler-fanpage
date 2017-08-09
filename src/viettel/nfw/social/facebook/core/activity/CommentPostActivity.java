package viettel.nfw.social.facebook.core.activity;

import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.FacebookAction;
import static viettel.nfw.social.facebook.core.FacebookAction.crawl;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.entity.IActivity;
import viettel.nfw.social.facebook.htmlobj.FacebookCommentForm;
import viettel.nfw.social.facebook.htmlobj.FacebookHeaderBar;
import viettel.nfw.social.facebook.core.HttpRequest;
import viettel.nfw.social.utils.HttpResponseInfo;

/**
 *
 * @author duongth5
 */
public class CommentPostActivity implements IActivity {

    private static final Logger LOG = LoggerFactory.getLogger(CommentPostActivity.class);
    private final FacebookAction fbAction;
    private MethodType chosenMethod;
    private static final Random randomize = new Random();
    private AccountStatus status;

    private static enum MethodType {

        COMMENT_MY_POST
    }

    public CommentPostActivity(FacebookAction fbAction) {
        this.fbAction = fbAction;
    }

    @Override
    public void run() {
        LOG.info("Run {}", CommentPostActivity.class.getName());
        AccountStatus retStatus = null;
        setMethod();

        switch (chosenMethod) {
            case COMMENT_MY_POST:
                retStatus = doCommentMyPost(fbAction);
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

    private AccountStatus doCommentMyPost(FacebookAction fbAction) {
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
                FacebookHeaderBar headerBar = Parser.getHeaderBarOfCurrentHtml(new URI(homeUrl), homeResponse);
                Map<String, URI> mapUris = headerBar.mapUris;
                if (MapUtils.isNotEmpty(mapUris)) {
                    URI profileUri = mapUris.get("Profile");
                    if (profileUri != null) {

                        // go to my profile timeline
                        String myTimelineResponse = crawl(profileUri.toString(), http, proxy);
                        AccountStatus myTimelineResponseKOT = Parser.verifyResponseHtml(profileUri.toString(), myTimelineResponse, true);
                        if (!myTimelineResponseKOT.equals(AccountStatus.ACTIVE)) {
                            accStatus = myTimelineResponseKOT;
                        } else {
                            List<String> fullStoryUrls = new ArrayList<>();
                            fullStoryUrls.addAll(Parser.getUrls(profileUri.toString(), myTimelineResponse, null, "Full Story", 0));
                            if (fullStoryUrls.isEmpty()) {
                                LOG.warn("Cannot find any post in my timeline");
                                accStatus = AccountStatus.DO_JOB_FAILED;
                            } else {
                                // get random a post
                                int randPos = randomize.nextInt(fullStoryUrls.size());
                                String visitStoryUrl = fullStoryUrls.get(randPos);
                                String pathStory = new URI(visitStoryUrl).getPath();

                                String storyResponse = crawl(visitStoryUrl, http, proxy);
                                AccountStatus storyResponeKOT = Parser.verifyResponseHtml(visitStoryUrl, storyResponse, true);
                                if (!storyResponeKOT.equals(AccountStatus.ACTIVE)) {
                                    accStatus = storyResponeKOT;
                                } else {
                                    // find comment form
                                    FacebookCommentForm fbCommentForm = Parser.getCommentForm(new URI(visitStoryUrl), storyResponse);
                                    if (fbCommentForm.commentForm != null) {
                                        String commentParams = Parser.buildParamsFromCommentForm(fbCommentForm.commentForm, "What's a nice day!");
                                        // do post status
                                        if (StringUtils.isNotEmpty(commentParams) && fbCommentForm.commentUri != null) {
                                            HttpResponseInfo responsePost = http.post(fbCommentForm.commentUri.toString(), commentParams, homeUrl, proxy);
                                            if (responsePost.getStatus() == 302) {
                                                String redirectUrl = responsePost.getHeaders().get("Location").get(0);
                                                LOG.info("Redirect Url {}", redirectUrl);
                                                URI redirectUri = new URI(redirectUrl);
                                                String redirectPath = redirectUri.getPath();
                                                String redirectResponse = crawl(redirectUrl, http, proxy);
                                                LOG.info("Redirect Body {}", redirectResponse);
                                                if (StringUtils.contains(redirectPath, pathStory)) {
                                                    LOG.info("COMMENTED");
                                                    accStatus = AccountStatus.DO_JOB_OK;
                                                }
                                            }
                                        } else {
                                            accStatus = AccountStatus.DO_JOB_FAILED;
                                        }
                                    } else {
                                        LOG.info("Cannot find comment form");
                                        accStatus = AccountStatus.DO_JOB_FAILED;
                                    }
                                }
                            }
                        }

                    } else {
                        LOG.warn("Cannot get Profile URL");
                        accStatus = AccountStatus.DO_JOB_FAILED;
                    }
                } else {
                    accStatus = AccountStatus.DO_JOB_FAILED;
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
            accStatus = AccountStatus.ERROR_UNKNOWN;
        }

        return accStatus;
    }

}
