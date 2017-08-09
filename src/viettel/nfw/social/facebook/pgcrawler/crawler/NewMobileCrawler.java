package viettel.nfw.social.facebook.pgcrawler.crawler;

import java.net.CookieManager;
import java.net.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.utils.Pair;

/**
 *
 * @author Duong
 */
public class NewMobileCrawler {

	private static final Logger LOG = LoggerFactory.getLogger(NewMobileCrawler.class);

	private final FacebookMobileActions facebookMobileActions;
	private final Account facebookAccount;

	public NewMobileCrawler(Account facebookAccount, CookieManager cookieManager, Proxy proxy) {
		this.facebookAccount = facebookAccount;
		this.facebookMobileActions = new FacebookMobileActions(facebookAccount, cookieManager, proxy);
	}

	public String getAccountInfo() {
		return this.facebookAccount.getUsername();
	}

	public boolean firstCheck() {
		AccountStatus accStatus = this.facebookMobileActions.login();
		return accStatus.equals(AccountStatus.LOGIN_OK);
	}

	public boolean canVisitProfileUrl(String profileUrl) {
		return this.facebookMobileActions.canVisitProfileUrl(profileUrl);
	}

	public FacebookObject doIt(String profileId, String profileUrl) {
		LOG.info("Account {} visit {} with url {}", new Object[]{facebookAccount.getUsername(), profileId, profileUrl});
		Pair<AccountStatus, FacebookObject> result = this.facebookMobileActions.deepProfile(profileUrl);
		LOG.info("{}", result.first.toString());
		return result.second;
	}

}
