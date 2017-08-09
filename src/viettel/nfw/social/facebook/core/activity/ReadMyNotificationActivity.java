package viettel.nfw.social.facebook.core.activity;

import java.net.Proxy;
import java.net.URI;
import java.util.Map;
import java.util.Random;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.FacebookAction;
import static viettel.nfw.social.facebook.core.FacebookAction.crawl;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.entity.IActivity;
import viettel.nfw.social.facebook.htmlobj.FacebookHeaderBar;
import viettel.nfw.social.facebook.core.HttpRequest;

/**
 *
 * @author duongth5
 */
public class ReadMyNotificationActivity implements IActivity {

    private static final Logger LOG = LoggerFactory.getLogger(ReadMyNotificationActivity.class);
    private final FacebookAction fbAction;
    private MethodType chosenMethod;
    private static final Random randomize = new Random();
    private AccountStatus status;

    private static enum MethodType {

        METHOD_1
    }

    public ReadMyNotificationActivity(FacebookAction fbAction) {
        this.fbAction = fbAction;
    }

    @Override
    public void run() {
        LOG.info("Run {}", CommentPostActivity.class.getName());

        AccountStatus retStatus = null;
        setMethod();

        switch (chosenMethod) {
            case METHOD_1:
                retStatus = doReadMyNotification(fbAction);
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

    private AccountStatus doReadMyNotification(FacebookAction fbAction) {
        AccountStatus retStatus = AccountStatus.ACTIVE;

        try {
            String homeUrl = fbAction.getHomeUrl();
            if (StringUtils.isEmpty(homeUrl)) {
                homeUrl = "https://m.facebook.com";
            }

            HttpRequest http = fbAction.getHttp();
            Proxy proxy = fbAction.getProxy();

            String homeResponse = crawl(homeUrl, http, proxy);
            AccountStatus homeResponseKOT = Parser.verifyResponseHtml(homeUrl, homeResponse, false);
            if (!homeResponseKOT.equals(AccountStatus.ACTIVE)) {
                retStatus = homeResponseKOT;
            } else {
                FacebookHeaderBar fbHeaderBar = Parser.getHeaderBarOfCurrentHtml(new URI(homeUrl), homeResponse);
                if (fbHeaderBar != null) {
                    Map<String, URI> mapUris = fbHeaderBar.mapUris;
                    if (MapUtils.isNotEmpty(mapUris)) {
                        URI notiUri = mapUris.get("Notifications");
                        if (notiUri != null) {
                            String notiResponse = crawl(notiUri.toString(), http, proxy);
                            AccountStatus notiResponseKOT = Parser.verifyResponseHtml(notiUri.toString(), notiResponse, true);
                            if (!notiResponseKOT.equals(AccountStatus.ACTIVE)) {
                                retStatus = notiResponseKOT;
                            } else {
                                LOG.info("Readed Notifications Tab");
                                // TODO update more code to do some fun
                            }
                        }
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
