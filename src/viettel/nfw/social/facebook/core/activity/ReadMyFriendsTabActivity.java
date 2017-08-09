package viettel.nfw.social.facebook.core.activity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.entity.IActivity;

/**
 *
 * @author duongth5
 */
public class ReadMyFriendsTabActivity implements IActivity {

    private static final Logger LOG = LoggerFactory.getLogger(ReadMyFriendsTabActivity.class);

    public ReadMyFriendsTabActivity() {
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

}
