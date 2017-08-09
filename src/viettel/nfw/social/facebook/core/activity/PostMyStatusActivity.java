package viettel.nfw.social.facebook.core.activity;

import viettel.nfw.social.utils.Storage;
import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.automanacc.object.ObjectPost;
import viettel.nfw.social.facebook.core.FacebookAction;
import static viettel.nfw.social.facebook.core.FacebookAction.crawl;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.entity.IActivity;
import viettel.nfw.social.facebook.htmlobj.FacebookMyStatusForm;
import viettel.nfw.social.utils.Constant;
import viettel.nfw.social.facebook.core.HttpRequest;
import viettel.nfw.social.utils.HttpResponseInfo;

/**
 *
 * @author duongth5
 */
public class PostMyStatusActivity implements IActivity {

    private static final Logger LOG = LoggerFactory.getLogger(PostMyStatusActivity.class);
    private final FacebookAction fbAction;
    private MethodType chosenMethod;
    private static final Random randomize = new Random();
    private static Map<String, List<ObjectPost>> listDataUsePost = new HashMap<>();
    private AccountStatus status;

    private static enum MethodType {

        POST_STATUS
    }

    static {
        File file = new File(Constant.FILE_POSTDATA);

        if (file.isFile()) {
            try {
                Storage.Reader reader = new Storage.Reader(file);
                listDataUsePost = (Map<String, List<ObjectPost>>) reader.next();
                reader.close();
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    public PostMyStatusActivity(FacebookAction fbAction) {
        this.fbAction = fbAction;
    }

    @Override
    public void run() {
        LOG.info("Run {}", PostMyStatusActivity.class.getName());

        AccountStatus retStatus = null;
        setMethod();

        switch (chosenMethod) {
            case POST_STATUS:
                retStatus = doUpdateStatus(fbAction);
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

    private String getStatusContent() {
        String statusContent = "What's a beautiful day!";
        // get random topic and random post
        try {
            List<String> keys = new ArrayList<>(listDataUsePost.keySet());
            int randIndex = randomize.nextInt(keys.size());
            String chosenTopic = keys.get(randIndex);
            LOG.info(chosenTopic);
            List<ObjectPost> listPosts = listDataUsePost.get(chosenTopic);
            int randPost = randomize.nextInt(listPosts.size());
            ObjectPost chosenPost = listPosts.get(randPost);
            StringBuilder sb = new StringBuilder();
            sb.append(chosenPost.getTitlePost());
            sb.append("\n");
            sb.append(chosenPost.getUrlPost());
            statusContent = sb.toString().trim();
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
        return statusContent;
    }

    private AccountStatus doUpdateStatus(FacebookAction fbAction) {

        AccountStatus retStatus = AccountStatus.ACTIVE;
        try {
            String homeUrl = fbAction.getHomeUrl();
            if (StringUtils.isEmpty(homeUrl)) {
                homeUrl = "https://m.facebook.com";
            }

            String statusContent = getStatusContent();

            HttpRequest http = fbAction.getHttp();
            Proxy proxy = fbAction.getProxy();

            String response = crawl(homeUrl, http, proxy);
            AccountStatus responseKOT = Parser.verifyResponseHtml(homeUrl, response, false);
            if (!responseKOT.equals(AccountStatus.ACTIVE)) {
                retStatus = responseKOT;
            } else {
                FacebookMyStatusForm myStatusForm = Parser.getStatusForm(new URI(homeUrl), response);
                // find all input in form
                if (myStatusForm.statusForm != null) {
                    String statusParams = Parser.buildParamsFromStatusForm(myStatusForm.statusForm, statusContent);
                    // do post status
                    if (StringUtils.isNotEmpty(statusParams) && myStatusForm.postStatusUri != null) {
                        HttpResponseInfo responsePost = http.post(myStatusForm.postStatusUri.toString(), statusParams, homeUrl, proxy);
                        if (responsePost.getStatus() == 302) {
                            String redirectUrl = responsePost.getHeaders().get("Location").get(0);
                            LOG.info("Redirect Url {}", redirectUrl);
                            URI redirectUri = new URI(redirectUrl);
                            String redirectPath = redirectUri.getPath();
                            String redirectResponse = crawl(redirectUrl, http, proxy);
                            LOG.info("Redirect Body {}", redirectResponse);
                            if (StringUtils.contains(redirectPath, "home.php")) {
                                LOG.info("Status UPDATED");
                                retStatus = AccountStatus.DO_JOB_OK;
                            }
                        }
                    } else {
                        retStatus = AccountStatus.KICKOUT_UNKNOWN;
                    }
                }
            }
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
            retStatus = AccountStatus.ERROR_UNKNOWN;
        }

        return retStatus;
    }

}
