package viettel.nfw.social.facebook.core.activity;

import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.FacebookAction;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.entity.IActivity;

/**
 *
 * @author duongth5
 */
public class VisitMyNewsFeedActivity implements IActivity {

    private static final Logger LOG = LoggerFactory.getLogger(VisitMyNewsFeedActivity.class);
    private final FacebookAction fbAction;
    private MethodType chosenMethod;
    private static final Random randomize = new Random();
    private AccountStatus status;

    private static enum MethodType {

        METHOD_1
    }

    public VisitMyNewsFeedActivity(FacebookAction fbAction) {
        this.fbAction = fbAction;
    }

    @Override
    public void run() {
        LOG.info("Run {}", VisitMyNewsFeedActivity.class.getName());

        AccountStatus retStatus = null;
        setMethod();

        switch (chosenMethod) {
            case METHOD_1:
                retStatus = surfMyHome(fbAction);
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

    public AccountStatus surfMyHome(FacebookAction fbAction) {

        AccountStatus retStatus;
        try {
            String homeUrl = fbAction.getHomeUrl();
            if (StringUtils.isEmpty(homeUrl)) {
                homeUrl = "https://m.facebook.com";
            }
            retStatus = fbAction.surfMyHome(homeUrl);

        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
            retStatus = AccountStatus.ERROR_UNKNOWN;
        }
        return retStatus;
    }
}
