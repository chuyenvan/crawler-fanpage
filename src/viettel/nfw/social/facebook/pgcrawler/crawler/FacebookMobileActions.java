package viettel.nfw.social.facebook.pgcrawler.crawler;

import java.io.IOException;
import java.net.CookieManager;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.facebook.core.FacebookMessage;
import viettel.nfw.social.facebook.core.HttpRequest;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.reviewdata.ParsingUtils;
import viettel.nfw.social.utils.HttpResponseInfo;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.social.fb.test.FbUrlToHtml;

/**
 *
 * @author Duong
 */
public class FacebookMobileActions {

	private static final Logger LOG = LoggerFactory.getLogger(FacebookMobileActions.class);

	private final Account account;
	private final CookieManager cookieManager;
	private final Proxy proxy;
	private final HttpRequest http;
	private String homeUrl;
	private String logoutUrl;

	public FacebookMobileActions(Account account, CookieManager cookieManager, Proxy proxy) {
		this.account = account;
		this.cookieManager = cookieManager;
		this.proxy = proxy;
		this.http = new HttpRequest(cookieManager, account.getUserAgent());
	}

	public AccountStatus login() {
		long startTime = System.currentTimeMillis();
		AccountStatus retCode = null;
		try {
			// reset cookie manager for Facebook mobile site
			cookieManager.put(new URI("https://m.facebook.com"), new HashMap<String, List<String>>());
			cookieManager.put(new URI("http://m.facebook.com"), new HashMap<String, List<String>>());

			// Login to Facebook and keep sessions
			HttpResponseInfo responseGet = http.get("https://m.facebook.com", proxy);
			Pair<String, String> postParams = viettel.nfw.social.facebook.core.Parser.getFacebookFormParams(responseGet.getBody(), account.getUsername(), account.getPassword());
			if (postParams == null) {
				retCode = AccountStatus.LOGIN_FAILED_POST_PARAM_NULL;
			} else {
				// Construct above post's content and then send a POST request for authentication
				String postUrl = postParams.first;
				if (StringUtils.isEmpty(postUrl)) {
					postUrl = "https://m.facebook.com/login.php";
				}
				String postParam = postParams.second;
				HttpResponseInfo responsePost = http.post(postUrl, postParam, null, proxy);

				if (responsePost.getStatus() == 302) {
					String redirectUrl = responsePost.getHeaders().get("Location").get(0);
					LOG.info("Redirect Url {}", redirectUrl);
					// check redirect url
					// ***if it starts with "https://m.facebook.com/home.php", login success
					// ***if it starts with "https://m.facebook.com/phoneacquire/", account have not verify mobile number but still login success
					// ***if it starts with "https://m.facebook.com/checkpoint/", 100% that account has been temporary lock
					URI redirectUri = new URI(redirectUrl);
					String path = redirectUri.getPath();

					if (path.contains("/home.php")) {
						retCode = AccountStatus.LOGIN_OK;
						homeUrl = redirectUri.toString();
					} else if (path.contains("/phoneacquire")) {
						retCode = AccountStatus.LOGIN_FAILED_NOT_VERIFY_MOBILE;
					} else if (path.contains("/checkpoint/")) {
						String checkpointResponse = null;
						try {
							HttpResponseInfo hriCheckpoint = http.get(redirectUrl, proxy);
							checkpointResponse = hriCheckpoint.getBody();
						} catch (Exception ex) {
							LOG.error(ex.getMessage(), ex);
						}
						// break to 2 cases:
						// 1. permitted ban: (Verify by PhotoId or Indentify Friends)
						// 2. just checking: input capcha
						Document doc = Jsoup.parse(checkpointResponse);
						String title = doc.title();
						String messageInForm = "";
						Elements checkpointForms = doc.select("form");
						for (Element checkpointForm : checkpointForms) {
							String action = checkpointForm.attr("action");
							if (StringUtils.contains(action, "/login/checkpoint/")) {
								messageInForm = checkpointForm.text();
								break;
							}
						}
						if (StringUtils.equalsIgnoreCase(title, "Please Verify Your Identity")
							|| (StringUtils.contains(messageInForm, "working hard to make sure")
							&& StringUtils.contains(messageInForm, "complete the following security check"))) {
							// account maybe alive
							retCode = AccountStatus.LOGIN_FAILED_VERIFY_IDENTITY;
						} else if (StringUtils.equalsIgnoreCase(title, "For security reasons your account is temporarily locked")
							|| StringUtils.contains(messageInForm, "reflects your real name")) {
							// account lock by photo id or friends check
							retCode = AccountStatus.LOGIN_FAILED_ACCOUNT_LOCK;
						} else {
							retCode = AccountStatus.LOGIN_FAILED_ACCOUNT_LOCK_UNKNOWN;
							LOG.warn("title {} - body {}", title, checkpointResponse);
						}
					} else {
						retCode = AccountStatus.LOGIN_FAILED_UNKNOWN;
					}
				} else {
					retCode = AccountStatus.LOGIN_FAILED_NOT_RETURN_302;
				}
			}

		} catch (IOException | URISyntaxException e) {
			LOG.error(e.getMessage(), e);
			retCode = AccountStatus.LOGIN_FAILED_UNKNOWN;
		} finally {
			long totalTime = System.currentTimeMillis() - startTime;
			String retMessage = "";
			if (retCode != null) {
				retMessage = retCode.toString();
			}
			LOG.info("Done action login in {} ms. Return code: {}", totalTime, retMessage);
		}
		return retCode;
	}

	public void logout(String logoutUrl) {
		if (StringUtils.isEmpty(logoutUrl)) {
			// go to home page
		}

		// send request logout
	}

	/**
	 * Maximum time for retry connection
	 */
	private static final int NUM_RETRY_CONNECTION = 5;

	/**
	 * Crawl single URL
	 *
	 * @param url URL to crawl
	 * @param http HTTP Request
	 * @param proxy Proxy setting
	 * @return Response body in String
	 */
	public static String crawl(String url, HttpRequest http, Proxy proxy) {
		String result = "";
		try {
			int minTime = 2 * 1000;
			int maxTime = 4 * 1000;
			int sleepTime = Funcs.randInt(minTime, maxTime); // 2-4 seconds
			LOG.info("random sleep {}", sleepTime);
			Thread.sleep(sleepTime);
		} catch (InterruptedException ex) {
			LOG.error(ex.getMessage(), ex);
			return result;
		}
		for (int i = 0; i < NUM_RETRY_CONNECTION; i++) {
			try {
				HttpResponseInfo response = http.get(url, proxy);
				result = response.getBody();
				break;
			} catch (Exception ex) {
				LOG.error("Error connecting to URL", ex);
				if (i == NUM_RETRY_CONNECTION - 1) {
					return result;
				}
			}
		}
		return result;
	}

	public Account getAccount() {
		return account;
	}

	public CookieManager getCookieManager() {
		return cookieManager;
	}

	public Proxy getProxy() {
		return proxy;
	}

	public HttpRequest getHttp() {
		return http;
	}

	public void setHomeUrl(String homeUrl) {
		this.homeUrl = homeUrl;
	}

	public String getHomeUrl() {
		return homeUrl;
	}

	public String getLogoutUrl() {
		return logoutUrl;
	}

	public boolean canVisitProfileUrl(String profileUrl) {
		String response = crawl(profileUrl, http, proxy);
		LOG.info("url: {} -- response: {}", profileUrl, response);
		AccountStatus responseKOT = viettel.nfw.social.facebook.core.Parser.verifyResponseHtml(profileUrl, response, true);
		return responseKOT.equals(AccountStatus.ACTIVE);
	}

	public Pair<AccountStatus, FacebookObject> deepProfile(String profileUrl) {

		AccountStatus retStatus = AccountStatus.ACTIVE;
		FacebookObject fbObj = null;
		try {
			List<FbUrlToHtml> crawledData = new ArrayList<>();
			LOG.info(FacebookMessage.CRAWL_PROFILE_START, profileUrl);

			// crawl profile URL
			String response = crawl(profileUrl, http, proxy);
			LOG.info("url: {} -- response: {}", profileUrl, response);
			AccountStatus responseKOT = viettel.nfw.social.facebook.core.Parser.verifyResponseHtml(profileUrl, response, true);
			if (!responseKOT.equals(AccountStatus.ACTIVE)) {
				LOG.warn("URL FAILED - {}", profileUrl);
				return new Pair<>(responseKOT, fbObj);
			}
			crawledData.add(new FbUrlToHtml(profileUrl, response, System.currentTimeMillis()));

			Map<String, String> foundProfileUrls = new HashMap<>();
			foundProfileUrls.putAll(viettel.nfw.social.facebook.core.Parser.findProfileUrls(profileUrl, response));

			List<String> timelineUrls = new ArrayList<>();
			timelineUrls.addAll(viettel.nfw.social.facebook.core.Parser.getUrls(profileUrl, response, null, "Timeline", 0));

			Set<String> fullStoryUrls = new HashSet<>();
			fullStoryUrls.addAll(viettel.nfw.social.facebook.core.Parser.getUrls(profileUrl, response, null, "Full Story", 0));
			if (fullStoryUrls.size() < 1) {
				fullStoryUrls.addAll(viettel.nfw.social.facebook.core.Parser.getUrls(profileUrl, response, null, "Comment", 1));
			}

			if (!timelineUrls.isEmpty()) {
				int count = 0;
				while (timelineUrls.size() > 0) {
					String timelineUrl = timelineUrls.remove(0);
					LOG.info("timelineUrl - {}", timelineUrl);

					if (!StringUtils.isEmpty(timelineUrl)) {
						String tlResponse = crawl(timelineUrl, http, proxy);
						AccountStatus tlResponseKOT = viettel.nfw.social.facebook.core.Parser.verifyResponseHtml(timelineUrl, tlResponse, true);
						if (!tlResponseKOT.equals(AccountStatus.ACTIVE)) {
							fbObj = parseData(profileUrl, crawledData);
							return new Pair<>(tlResponseKOT, fbObj);
						}
						crawledData.add(new FbUrlToHtml(timelineUrl, tlResponse, System.currentTimeMillis()));
						foundProfileUrls.putAll(viettel.nfw.social.facebook.core.Parser.findProfileUrls(timelineUrl, tlResponse));

						Set<String> tlFullStoryUrls = new HashSet<>();
						tlFullStoryUrls.addAll(viettel.nfw.social.facebook.core.Parser.getUrls(timelineUrl, tlResponse, null, "Full Story", 0));
						if (tlFullStoryUrls.size() < 1) {
							tlFullStoryUrls.addAll(viettel.nfw.social.facebook.core.Parser.getUrls(timelineUrl, tlResponse, null, "Comment", 1));
						}

						if (!tlFullStoryUrls.isEmpty()) {
							for (String tlFullStoryUrl : tlFullStoryUrls) {
								String fsResponse = crawl(tlFullStoryUrl, http, proxy);
								AccountStatus fsResponseKOT = viettel.nfw.social.facebook.core.Parser.verifyResponseHtml(tlFullStoryUrl, fsResponse, true);
								if (!fsResponseKOT.equals(AccountStatus.ACTIVE)) {
									fbObj = parseData(profileUrl, crawledData);
									return new Pair<>(fsResponseKOT, fbObj);
								}
								crawledData.add(new FbUrlToHtml(tlFullStoryUrl, fsResponse, System.currentTimeMillis()));
								foundProfileUrls.putAll(viettel.nfw.social.facebook.core.Parser.findProfileUrls(tlFullStoryUrl, fsResponse));
							}
						}

						if (count < 5) {
							List<String> seeMoreTimelineUrls = new ArrayList<>();
							seeMoreTimelineUrls.addAll(viettel.nfw.social.facebook.core.Parser.getUrls(timelineUrl, tlResponse, null, "Show more", 0));
							if (!seeMoreTimelineUrls.isEmpty()) {
								LOG.info("Show more size {}", seeMoreTimelineUrls.size());
								timelineUrls.add(seeMoreTimelineUrls.get(0));
								count++;
							}
						}
					}
				}
			} else {
				List<String> showMoreUrls = new ArrayList<>();
				showMoreUrls.addAll(viettel.nfw.social.facebook.core.Parser.getUrls(profileUrl, response, null, "Show More", 0));
				int count = 0;
				while (showMoreUrls.size() > 0) {
					String timelineUrl = showMoreUrls.remove(0);
					LOG.info("timelineUrl - {}", timelineUrl);

					if (!StringUtils.isEmpty(timelineUrl)) {
						String tlResponse = crawl(timelineUrl, http, proxy);
						AccountStatus tlResponseKOT = viettel.nfw.social.facebook.core.Parser.verifyResponseHtml(timelineUrl, tlResponse, true);
						if (!tlResponseKOT.equals(AccountStatus.ACTIVE)) {
							fbObj = parseData(profileUrl, crawledData);
							return new Pair<>(tlResponseKOT, fbObj);
						}
						crawledData.add(new FbUrlToHtml(timelineUrl, tlResponse, System.currentTimeMillis()));
						foundProfileUrls.putAll(viettel.nfw.social.facebook.core.Parser.findProfileUrls(timelineUrl, tlResponse));

						Set<String> tlFullStoryUrls = new HashSet<>();
						tlFullStoryUrls.addAll(viettel.nfw.social.facebook.core.Parser.getUrls(timelineUrl, tlResponse, null, "Full Story", 0));
						if (tlFullStoryUrls.size() < 1) {
							tlFullStoryUrls.addAll(viettel.nfw.social.facebook.core.Parser.getUrls(timelineUrl, tlResponse, null, "Comment", 1));
						}
						fullStoryUrls.addAll(tlFullStoryUrls);

						if (count < 5) {
							List<String> seeMoreTimelineUrls = new ArrayList<>();
							seeMoreTimelineUrls.addAll(viettel.nfw.social.facebook.core.Parser.getUrls(timelineUrl, tlResponse, null, "Show More", 0));
							if (!seeMoreTimelineUrls.isEmpty()) {
								LOG.info("Show more size {}", seeMoreTimelineUrls.size());
								showMoreUrls.add(seeMoreTimelineUrls.get(0));
								count++;
							}
						}
					}
				}
			}

			if (!fullStoryUrls.isEmpty()) {
				for (String fullStoryUrl : fullStoryUrls) {
					String fsResponse = crawl(fullStoryUrl, http, proxy);
					AccountStatus fsResponseKOT = viettel.nfw.social.facebook.core.Parser.verifyResponseHtml(fullStoryUrl, fsResponse, true);
					if (!fsResponseKOT.equals(AccountStatus.ACTIVE)) {
						fbObj = parseData(profileUrl, crawledData);
						return new Pair<>(fsResponseKOT, fbObj);
					}
					crawledData.add(new FbUrlToHtml(fullStoryUrl, fsResponse, System.currentTimeMillis()));
					foundProfileUrls.putAll(viettel.nfw.social.facebook.core.Parser.findProfileUrls(fullStoryUrl, fsResponse));
				}
			}

			// collect profile URLs and crawled data
			fbObj = parseData(profileUrl, crawledData);
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
			retStatus = AccountStatus.ERROR_UNKNOWN;
		}

		return new Pair<>(retStatus, fbObj);
	}

	private static FacebookObject parseData(String profileUrl, List<FbUrlToHtml> crawledData) {
		try {
			if (!crawledData.isEmpty()) {
				LOG.info("crawledData size {} - profile {}", crawledData.size(), profileUrl);

				FacebookObject fbObj = ParsingUtils.fromHtmltoFacebookObject(crawledData);
				if (fbObj != null) {
					return fbObj;
				}
			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
		return null;
	}
}
