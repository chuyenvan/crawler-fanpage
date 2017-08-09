package viettel.nfw.social.facebook.core;

import viettel.nfw.social.common.ServiceOutlinks;
import java.io.File;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.utils.HttpResponseInfo;
import java.io.IOException;
import java.net.CookieManager;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.crawler.RunFacebookCrawler;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.facebook.deeptracking.PostInfo;
import viettel.nfw.social.facebook.deeptracking.ProfileInfo;
import viettel.nfw.social.facebook.deeptracking.RunDeepTracking;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.model.facebook.Post;
import viettel.nfw.social.reviewdata.ParsingUtils;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.Funcs;
import viettel.nfw.social.utils.FileUtils;
import vn.viettel.social.fb.test.FbUrlToHtml;

/**
 *
 * @author duongth5
 */
public class FacebookAction {

	private static final Logger LOG = LoggerFactory.getLogger(FacebookAction.class);

	private final Account account;
	private final CookieManager cookieManager;
	private final Proxy proxy;
	private final HttpRequest http;
	private String homeUrl;
	private String logoutUrl;

	public FacebookAction(Account account, CookieManager cookieManager, Proxy proxy) {
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
			Pair<String, String> postParams = Parser.getFacebookFormParams(responseGet.getBody(), account.getUsername(), account.getPassword());
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

	// for crawler
	public AccountStatus surfMyHome(String homeUrl) {
		AccountStatus retStatus = AccountStatus.ACTIVE;
		try {
			// go to Home
			HttpResponseInfo homeResponse = http.get(homeUrl, proxy);
			AccountStatus responseKOT = Parser.verifyResponseHtml(homeUrl, homeResponse.getBody(), false);
			if (responseKOT.equals(AccountStatus.KICKOUT_LEVEL_2)) {
				retStatus = responseKOT;
				return retStatus;
			}

			Map<String, String> foundProfileUrls = new HashMap<>();
			foundProfileUrls.putAll(Parser.findProfileUrls(homeUrl, homeResponse.getBody()));
			Set<String> profileUrls = Parser.getUrls(homeUrl, homeResponse.getBody(), "div#viewport", "Profile", 1);
			Set<String> findFriendsUrls = Parser.getUrls(homeUrl, homeResponse.getBody(), "div#viewport", "Find Friends", 0);
			Set<String> groupsUrls = Parser.getUrls(homeUrl, homeResponse.getBody(), "div#viewport", "Groups", 0);
			Set<String> pagesUrls = Parser.getUrls(homeUrl, homeResponse.getBody(), "div#viewport", "Pages", 0);
			Set<String> fullStoryInNewsFeedUrls = new HashSet<>();
			fullStoryInNewsFeedUrls.addAll(Parser.getUrls(homeUrl, homeResponse.getBody(), null, "Full Story", 0));
			fullStoryInNewsFeedUrls.addAll(Parser.getUrls(homeUrl, homeResponse.getBody(), null, " Comments", 1));

			if (!profileUrls.isEmpty()) {
				for (String profileUrl : profileUrls) {
					HttpResponseInfo myProfileResponse = http.get(profileUrl, proxy);
					AccountStatus myProfileResponseKOT = Parser.verifyResponseHtml(profileUrl, myProfileResponse.getBody(), true);
					if (myProfileResponseKOT.equals(AccountStatus.KICKOUT_LEVEL_2)) {
						retStatus = myProfileResponseKOT;
						sendOutLinks(account.getUsername(), foundProfileUrls);
						return retStatus;
					}

					Set<String> friendsUrls = Parser.getUrls(profileUrl, myProfileResponse.getBody(), null, "Friends", 0);
					if (!friendsUrls.isEmpty()) {
						for (String friendsUrl : friendsUrls) {
							LOG.info("myfriendsUrl - {}", friendsUrl);
							String frResponse = crawl(friendsUrl, http, proxy);
							AccountStatus frResponseKOT = Parser.verifyResponseHtml(friendsUrl, frResponse, true);
							if (frResponseKOT.equals(AccountStatus.KICKOUT_LEVEL_2)) {
								retStatus = frResponseKOT;
								sendOutLinks(account.getUsername(), foundProfileUrls);
								return retStatus;
							}

							foundProfileUrls.putAll(Parser.findProfileUrls(friendsUrl, frResponse));
							List<String> seeMoreFriends = new ArrayList<>();
							seeMoreFriends.addAll(Parser.getUrls(friendsUrl, frResponse, null, "See More Friends", 0));
							if (!seeMoreFriends.isEmpty()) {
								while (seeMoreFriends.size() > 0) {
									String seeMoreFriendsUrl = seeMoreFriends.remove(0);
									LOG.info("myfriendsUrl - {}", seeMoreFriendsUrl);
									String smfResponse = crawl(seeMoreFriendsUrl, http, proxy);
									AccountStatus smfResponseKOT = Parser.verifyResponseHtml(seeMoreFriendsUrl, smfResponse, true);
									if (smfResponseKOT.equals(AccountStatus.KICKOUT_LEVEL_2)) {
										retStatus = smfResponseKOT;
										sendOutLinks(account.getUsername(), foundProfileUrls);
										return retStatus;
									}
									foundProfileUrls.putAll(Parser.findProfileUrls(seeMoreFriendsUrl, smfResponse));
									seeMoreFriends.addAll(Parser.getUrls(seeMoreFriendsUrl, smfResponse, null, "See More Friends", 0));
								}
							}

						}
					}
				}
			}
			if (!findFriendsUrls.isEmpty()) {
				for (String findFriendsUrl : findFriendsUrls) {
					HttpResponseInfo myFindFriendsResponse = http.get(findFriendsUrl, proxy);
					AccountStatus myFindFriendsResponseKOT = Parser.verifyResponseHtml(findFriendsUrl, myFindFriendsResponse.getBody(), true);
					if (myFindFriendsResponseKOT.equals(AccountStatus.KICKOUT_LEVEL_2)) {
						retStatus = myFindFriendsResponseKOT;
						sendOutLinks(account.getUsername(), foundProfileUrls);
						return retStatus;
					}
					foundProfileUrls.putAll(findFriends(findFriendsUrl, myFindFriendsResponse.getBody()));
				}
			}
			if (!groupsUrls.isEmpty()) {
				for (String groupsUrl : groupsUrls) {
					HttpResponseInfo myGroupsReponse = http.get(groupsUrl, proxy);
					AccountStatus myGroupsReponseKOT = Parser.verifyResponseHtml(groupsUrl, myGroupsReponse.getBody(), true);
					if (myGroupsReponseKOT.equals(AccountStatus.KICKOUT_LEVEL_2)) {
						retStatus = myGroupsReponseKOT;
						sendOutLinks(account.getUsername(), foundProfileUrls);
						return retStatus;
					}
					foundProfileUrls.putAll(Parser.findProfileUrls(groupsUrl, myGroupsReponse.getBody()));
				}
			}
			if (!pagesUrls.isEmpty()) {
				for (String pagesUrl : pagesUrls) {
					HttpResponseInfo myPagesReponse = http.get(pagesUrl, proxy);
					AccountStatus myPagesReponseKOT = Parser.verifyResponseHtml(pagesUrl, myPagesReponse.getBody(), true);
					if (myPagesReponseKOT.equals(AccountStatus.KICKOUT_LEVEL_2)) {
						retStatus = myPagesReponseKOT;
						sendOutLinks(account.getUsername(), foundProfileUrls);
						return retStatus;
					}
					foundProfileUrls.putAll(Parser.findProfileUrls(pagesUrl, myPagesReponse.getBody()));
				}
			}
			if (!fullStoryInNewsFeedUrls.isEmpty()) {
				for (String fullStoryUrl : fullStoryInNewsFeedUrls) {
					String fsResponse = crawl(fullStoryUrl, http, proxy);
					AccountStatus fsResponseKOT = Parser.verifyResponseHtml(fullStoryUrl, fsResponse, true);
					if (fsResponseKOT.equals(AccountStatus.KICKOUT_LEVEL_2)) {
						retStatus = fsResponseKOT;
						sendOutLinks(account.getUsername(), foundProfileUrls);
						return retStatus;
					}
					foundProfileUrls.putAll(Parser.findProfileUrls(fullStoryUrl, fsResponse));
				}
			}

			// send out links
			sendOutLinks(account.getUsername(), foundProfileUrls);

		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
			retStatus = AccountStatus.ERROR_UNKNOWN;
		}
		return retStatus;
	}

	private void sendOutLinks(String username, Map<String, String> foundProfileUrls) {
		try {
			for (Map.Entry<String, String> entrySet : foundProfileUrls.entrySet()) {
				String normUrl = entrySet.getKey(); // key norm
				String origUrl = entrySet.getValue(); // value orig
				ServiceOutlinks.addOutLink(username, origUrl, normUrl);
			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
	}

	private Map<String, String> findFriends(String findFriendsUrl, String findFriendsRawHtml) {
		Map<String, String> profileUrls = new HashMap<>();
		List<URI> friendHoverCardUris = new ArrayList<>();
		if (StringUtils.isNotEmpty(findFriendsUrl)) {
			Set<URI> uris = Parser.findAllHrefsInMainContent(findFriendsUrl, findFriendsRawHtml);
			for (URI uri : uris) {
				if (uri.getPath().contains("/friends/hovercard/mbasic")) {
					friendHoverCardUris.add(uri);
				}
			}
		}
		int sizeOfFriendHoverCard = friendHoverCardUris.size();
		if (sizeOfFriendHoverCard > 0) {
			Collections.shuffle(friendHoverCardUris);
			for (int i = 0; i < sizeOfFriendHoverCard; i++) {
				String url = friendHoverCardUris.get(i).toString();
				try {
					HttpResponseInfo responseGet = http.get(url, proxy);
					// in response HTML, find:
					Document responseDoc = Jsoup.parse(responseGet.getBody());
					// find button view profile
					Elements viewProfileBtns = responseDoc.select("a:contains(View Profile)");
					for (Element viewProfileBtn : viewProfileBtns) {
						String href = viewProfileBtn.attr("href");
						URI profileUri = new URI(url).resolve(href);
						String result = Parser.normalizeProfileUrl(profileUri);
						if (StringUtils.isNotEmpty(result)) {
							LOG.info("findFriends - {}", result);
							String[] parts = StringUtils.split(result, "|");
							profileUrls.put(parts[1], parts[0]);
						}
					}
				} catch (IOException | URISyntaxException ex) {
					LOG.error(ex.getMessage(), ex);
				}

			}
		}
		return profileUrls;
	}

	public AccountStatus crawl(String profileUrl) {

		String savedDir = "storage/facebook/";
		AccountStatus retStatus = AccountStatus.ACTIVE;
		try {
			List<FbUrlToHtml> crawledData = new ArrayList<>();
			LOG.info(FacebookMessage.CRAWL_PROFILE_START, profileUrl);

			// crawl profile URL
			String response = crawl(profileUrl, http, proxy);
			LOG.info("url: {} -- response: {}", profileUrl, response);
			AccountStatus responseKOT = Parser.verifyResponseHtml(profileUrl, response, true);
			Document profileHTMLDoc = Jsoup.parse(response);
			if (!responseKOT.equals(AccountStatus.ACTIVE)) {
				LOG.warn("URL FAILED - {}", profileUrl);
				retStatus = responseKOT;
				return retStatus;
			}
			crawledData.add(new FbUrlToHtml(profileUrl, response, System.currentTimeMillis()));

			Map<String, String> foundProfileUrls = new HashMap<>();
			foundProfileUrls.putAll(Parser.findProfileUrls(profileUrl, response));
			List<String> timelineUrls = new ArrayList<>();
			timelineUrls.addAll(Parser.getUrls(profileUrl, response, null, "Timeline", 0));
			Set<String> aboutUrls = Parser.getUrls(profileUrl, response, null, "About", 0);
			Set<String> friendsUrls = Parser.getUrls(profileUrl, response, null, "Friends", 0);
			Set<String> likesUrls = Parser.getUrls(profileUrl, response, null, "Likes", 0);
			Set<String> followingUrls = Parser.getUrls(profileUrl, response, null, "Following", 0);
			Set<String> fullStoryUrls = new HashSet<>();
			fullStoryUrls.addAll(Parser.getUrls(profileUrl, response, null, "Full Story", 0));
			if (fullStoryUrls.size() < 1) {
				fullStoryUrls.addAll(Parser.getUrls(profileUrl, response, null, "Comment", 1));
			}

			if (!timelineUrls.isEmpty()) {

				int count = 0;
				while (timelineUrls.size() > 0) {
					String timelineUrl = timelineUrls.remove(0);
					LOG.info("timelineUrl - {}", timelineUrl);

					if (!StringUtils.isEmpty(timelineUrl)) {
						String tlResponse = crawl(timelineUrl, http, proxy);
						AccountStatus tlResponseKOT = Parser.verifyResponseHtml(timelineUrl, tlResponse, true);
						if (!tlResponseKOT.equals(AccountStatus.ACTIVE)) {
							writeCrawledData(account.getUsername(), profileUrl, crawledData, savedDir);
							sendOutLinks(account.getUsername(), foundProfileUrls);
							retStatus = responseKOT;
							return retStatus;
						}
						crawledData.add(new FbUrlToHtml(timelineUrl, tlResponse, System.currentTimeMillis()));
						foundProfileUrls.putAll(Parser.findProfileUrls(timelineUrl, tlResponse));

						Set<String> tlFullStoryUrls = new HashSet<>();
						tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Full Story", 0));
						if (tlFullStoryUrls.size() < 1) {
							tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Comment", 1));
						}

						if (!tlFullStoryUrls.isEmpty()) {
							for (String tlFullStoryUrl : tlFullStoryUrls) {
								String fsResponse = crawl(tlFullStoryUrl, http, proxy);
								AccountStatus fsResponseKOT = Parser.verifyResponseHtml(tlFullStoryUrl, fsResponse, true);
								if (!fsResponseKOT.equals(AccountStatus.ACTIVE)) {
									writeCrawledData(account.getUsername(), profileUrl, crawledData, savedDir);
									sendOutLinks(account.getUsername(), foundProfileUrls);
									retStatus = fsResponseKOT;
									return retStatus;
								}
								crawledData.add(new FbUrlToHtml(tlFullStoryUrl, fsResponse, System.currentTimeMillis()));
								foundProfileUrls.putAll(Parser.findProfileUrls(tlFullStoryUrl, fsResponse));
							}
						}

						if (count < 5) {
							List<String> seeMoreTimelineUrls = new ArrayList<>();
							seeMoreTimelineUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Show more", 0));
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
				showMoreUrls.addAll(Parser.getUrls(profileUrl, response, null, "Show More", 0));
				int count = 0;
				while (showMoreUrls.size() > 0) {
					String timelineUrl = showMoreUrls.remove(0);
					LOG.info("timelineUrl - {}", timelineUrl);

					if (!StringUtils.isEmpty(timelineUrl)) {
						String tlResponse = crawl(timelineUrl, http, proxy);
						AccountStatus tlResponseKOT = Parser.verifyResponseHtml(timelineUrl, tlResponse, true);
						if (!tlResponseKOT.equals(AccountStatus.ACTIVE)) {
							writeCrawledData(account.getUsername(), profileUrl, crawledData, savedDir);
							sendOutLinks(account.getUsername(), foundProfileUrls);
							retStatus = responseKOT;
							return retStatus;
						}
						crawledData.add(new FbUrlToHtml(timelineUrl, tlResponse, System.currentTimeMillis()));
						foundProfileUrls.putAll(Parser.findProfileUrls(timelineUrl, tlResponse));

						Set<String> tlFullStoryUrls = new HashSet<>();
						tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Full Story", 0));
						if (tlFullStoryUrls.size() < 1) {
							tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Comment", 1));
						}
						fullStoryUrls.addAll(tlFullStoryUrls);

						if (count < 5) {
							List<String> seeMoreTimelineUrls = new ArrayList<>();
							seeMoreTimelineUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Show More", 0));
							if (!seeMoreTimelineUrls.isEmpty()) {
								LOG.info("Show more size {}", seeMoreTimelineUrls.size());
								showMoreUrls.add(seeMoreTimelineUrls.get(0));
								count++;
							}
						}
					}
				}
			}
			if (!aboutUrls.isEmpty()) {
				for (String aboutUrl : aboutUrls) {
					LOG.info("aboutUrl - {}", aboutUrl);
					String abResponse = crawl(aboutUrl, http, proxy);
					AccountStatus abResponseKOT = Parser.verifyResponseHtml(aboutUrl, abResponse, true);
					if (!abResponseKOT.equals(AccountStatus.ACTIVE)) {
						writeCrawledData(account.getUsername(), profileUrl, crawledData, savedDir);
						sendOutLinks(account.getUsername(), foundProfileUrls);
						retStatus = abResponseKOT;
						return retStatus;
					}
					crawledData.add(new FbUrlToHtml(aboutUrl, abResponse, System.currentTimeMillis()));
					foundProfileUrls.putAll(Parser.findProfileUrls(aboutUrl, abResponse));
				}
			}
			if (!friendsUrls.isEmpty()) {
				for (String friendsUrl : friendsUrls) {
					LOG.info("friendsUrl - {}", friendsUrl);
					String frResponse = crawl(friendsUrl, http, proxy);
					AccountStatus frResponseKOT = Parser.verifyResponseHtml(friendsUrl, frResponse, true);
					if (!frResponseKOT.equals(AccountStatus.ACTIVE)) {
						writeCrawledData(account.getUsername(), profileUrl, crawledData, savedDir);
						sendOutLinks(account.getUsername(), foundProfileUrls);
						retStatus = frResponseKOT;
						return retStatus;
					}
					crawledData.add(new FbUrlToHtml(friendsUrl, frResponse, System.currentTimeMillis()));
					foundProfileUrls.putAll(Parser.findProfileUrls(friendsUrl, frResponse));

					List<String> seeMoreFriends = new ArrayList<>();
					seeMoreFriends.addAll(Parser.getUrls(friendsUrl, frResponse, null, "See More Friends", 0));
					if (!seeMoreFriends.isEmpty()) {
						int count = 0;
						// int maxCount = Funcs.randInt(4, 8);
						while (seeMoreFriends.size() > 0) {
							String seeMoreFriendsUrl = seeMoreFriends.remove(0);
							String smfResponse = crawl(seeMoreFriendsUrl, http, proxy);
							AccountStatus smfResponseKOT = Parser.verifyResponseHtml(seeMoreFriendsUrl, smfResponse, true);
							if (!smfResponseKOT.equals(AccountStatus.ACTIVE)) {
								writeCrawledData(account.getUsername(), profileUrl, crawledData, savedDir);
								sendOutLinks(account.getUsername(), foundProfileUrls);
								retStatus = smfResponseKOT;
								return retStatus;
							}
							crawledData.add(new FbUrlToHtml(seeMoreFriendsUrl, smfResponse, System.currentTimeMillis()));
							foundProfileUrls.putAll(Parser.findProfileUrls(seeMoreFriendsUrl, smfResponse));
							seeMoreFriends.addAll(Parser.getUrls(seeMoreFriendsUrl, smfResponse, null, "See More Friends", 0));
							try {
								if (count % 5 == 0) {
									Thread.sleep(5000l);
								}
							} catch (Exception ex) {
								LOG.error(ex.getMessage(), ex);
							}
                            // if (count < maxCount) {
							//     seeMoreFriends.addAll(Parser.getUrls(seeMoreFriendsUrl, smfResponse, null, "See More Friends", 0));
							// }
							count++;
						}
					}
				}
			}
			if (!likesUrls.isEmpty()) {
				for (String likesUrl : likesUrls) {
					LOG.info("likesUrl - {}", likesUrl);
					String lkResponse = crawl(likesUrl, http, proxy);
					AccountStatus lkResponseKOT = Parser.verifyResponseHtml(likesUrl, lkResponse, true);
					if (!lkResponseKOT.equals(AccountStatus.ACTIVE)) {
						writeCrawledData(account.getUsername(), profileUrl, crawledData, savedDir);
						sendOutLinks(account.getUsername(), foundProfileUrls);
						retStatus = lkResponseKOT;
						return retStatus;
					}
					crawledData.add(new FbUrlToHtml(likesUrl, lkResponse, System.currentTimeMillis()));
					foundProfileUrls.putAll(Parser.findProfileUrls(likesUrl, lkResponse));
				}
			}
			if (!followingUrls.isEmpty()) {
				for (String followingUrl : followingUrls) {
					LOG.info("followingUrl - {}", followingUrl);
					String flResponse = crawl(followingUrl, http, proxy);
					AccountStatus flResponseKOT = Parser.verifyResponseHtml(followingUrl, flResponse, true);
					if (!flResponseKOT.equals(AccountStatus.ACTIVE)) {
						writeCrawledData(account.getUsername(), profileUrl, crawledData, savedDir);
						sendOutLinks(account.getUsername(), foundProfileUrls);
						retStatus = flResponseKOT;
						return retStatus;
					}
					crawledData.add(new FbUrlToHtml(followingUrl, flResponse, System.currentTimeMillis()));
					foundProfileUrls.putAll(Parser.findProfileUrls(followingUrl, flResponse));
				}
			}
			if (!fullStoryUrls.isEmpty()) {
				Random rand = new Random();
				for (String fullStoryUrl : fullStoryUrls) {
					int percent = rand.nextInt(100);
					if (percent <= 70) {
						String fsResponse = crawl(fullStoryUrl, http, proxy);
						AccountStatus fsResponseKOT = Parser.verifyResponseHtml(fullStoryUrl, fsResponse, true);
						if (!fsResponseKOT.equals(AccountStatus.ACTIVE)) {
							writeCrawledData(account.getUsername(), profileUrl, crawledData, savedDir);
							sendOutLinks(account.getUsername(), foundProfileUrls);
							retStatus = fsResponseKOT;
							return retStatus;
						}
						crawledData.add(new FbUrlToHtml(fullStoryUrl, fsResponse, System.currentTimeMillis()));
						foundProfileUrls.putAll(Parser.findProfileUrls(fullStoryUrl, fsResponse));
					}
				}
			}

			// find log out link
			try {
				Elements a_href = profileHTMLDoc.select("a[href]");
				for (Element link : a_href) {
					String strLink = link.attr("href");
					if (StringUtils.startsWith(strLink, "/logout.php")) {
						this.logoutUrl = FacebookURL.BASE_URL + strLink;
						LOG.info("Logout URL {}", this.logoutUrl);
					}
				}
			} catch (Exception ex) {
				LOG.error(ex.getMessage(), ex);
			}

			// collect profile URLs and crawled data
			sendOutLinks(account.getUsername(), foundProfileUrls);
			writeCrawledData(account.getUsername(), profileUrl, crawledData, savedDir);
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
			retStatus = AccountStatus.ERROR_UNKNOWN;
		}

		return retStatus;
	}

	private static void writeCrawledData(String username, String profileUrl, List<FbUrlToHtml> crawledData, String savedDir) {
		try {
			if (!crawledData.isEmpty()) {
				LOG.info("crawledData size {} - profile {}", crawledData.size(), profileUrl);

				try {
					FacebookObject fbObj = ParsingUtils.fromHtmltoFacebookObject(crawledData);
					if (fbObj != null) {
						RunFacebookCrawler.facebookObjectQueue.add(fbObj);
					}
				} catch (Exception ex) {
					LOG.error(ex.getMessage(), ex);
				}

				String fIdUn = "";
				Map<String, String> retMap = Parser.extractUsernameOrIdFromUrl(profileUrl);
				if (retMap.containsKey("username")) {
					fIdUn = retMap.get("username");
				} else if (retMap.containsKey("id")) {
					fIdUn = retMap.get("id");
				} else if (retMap.containsKey("groupId")) {
					fIdUn = retMap.get("groupId");
				} else if (retMap.containsKey("pageId")) {
					fIdUn = retMap.get("pageId");
				}
				if (StringUtils.isEmpty(fIdUn)) {
					fIdUn = "df" + String.valueOf(System.currentTimeMillis());
				}

				String filename = savedDir + fIdUn + "_" + String.valueOf(System.currentTimeMillis()) + ".nfbo";
				FileUtils.writeObject2File(new File(filename), crawledData, false);
				URI profileUri = new URI(profileUrl);
				String temp = Parser.normalizeProfileUrl(profileUri);
				if (StringUtils.isEmpty(temp)) {
					ServiceOutlinks.addCrawledUrl(username, profileUrl);
				} else {
					String[] parts = StringUtils.split(temp, "|");
					String sentUrl = parts[1];
					ServiceOutlinks.addCrawledUrl(username, sentUrl);
				}
			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
	}

	public AccountStatus deepCrawl(String profileUrl) {

		String savedDir = "storage/eval/";
		AccountStatus retStatus = AccountStatus.ACTIVE;
		try {
			List<FbUrlToHtml> crawledData = new ArrayList<>();
			LOG.info(FacebookMessage.CRAWL_PROFILE_START, profileUrl);

			// crawl profile URL
			String response = crawl(profileUrl, http, proxy);
			LOG.info("url: {} -- response: {}", profileUrl, response);
			AccountStatus responseKOT = Parser.verifyResponseHtml(profileUrl, response, true);
			Document profileHTMLDoc = Jsoup.parse(response);
			if (!responseKOT.equals(AccountStatus.ACTIVE)) {
				LOG.warn("URL FAILED - {}", profileUrl);
				retStatus = responseKOT;
				return retStatus;
			}
			crawledData.add(new FbUrlToHtml(profileUrl, response, System.currentTimeMillis()));

			Map<String, String> foundProfileUrls = new HashMap<>();
			foundProfileUrls.putAll(Parser.findProfileUrls(profileUrl, response));

			List<String> timelineUrls = new ArrayList<>();
			timelineUrls.addAll(Parser.getUrls(profileUrl, response, null, "Timeline", 0));

			Set<String> fullStoryUrls = new HashSet<>();
			fullStoryUrls.addAll(Parser.getUrls(profileUrl, response, null, "Full Story", 0));
			if (fullStoryUrls.size() < 1) {
				fullStoryUrls.addAll(Parser.getUrls(profileUrl, response, null, "Comment", 1));
			}

			if (!timelineUrls.isEmpty()) {
				int count = 0;
				while (timelineUrls.size() > 0) {
					String timelineUrl = timelineUrls.remove(0);
					LOG.info("timelineUrl - {}", timelineUrl);

					if (!StringUtils.isEmpty(timelineUrl)) {
						String tlResponse = crawl(timelineUrl, http, proxy);
						AccountStatus tlResponseKOT = Parser.verifyResponseHtml(timelineUrl, tlResponse, true);
						if (!tlResponseKOT.equals(AccountStatus.ACTIVE)) {
							writeCrawledData3(account.getUsername(), profileUrl, crawledData, savedDir);
							sendOutLinks(account.getUsername(), foundProfileUrls);
							retStatus = responseKOT;
							return retStatus;
						}
						crawledData.add(new FbUrlToHtml(timelineUrl, tlResponse, System.currentTimeMillis()));
						foundProfileUrls.putAll(Parser.findProfileUrls(timelineUrl, tlResponse));

						Set<String> tlFullStoryUrls = new HashSet<>();
						tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Full Story", 0));
						if (tlFullStoryUrls.size() < 1) {
							tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Comment", 1));
						}

						if (!tlFullStoryUrls.isEmpty()) {
							for (String tlFullStoryUrl : tlFullStoryUrls) {
								String fsResponse = crawl(tlFullStoryUrl, http, proxy);
								AccountStatus fsResponseKOT = Parser.verifyResponseHtml(tlFullStoryUrl, fsResponse, true);
								if (!fsResponseKOT.equals(AccountStatus.ACTIVE)) {
									writeCrawledData3(account.getUsername(), profileUrl, crawledData, savedDir);
									sendOutLinks(account.getUsername(), foundProfileUrls);
									retStatus = fsResponseKOT;
									return retStatus;
								}
								crawledData.add(new FbUrlToHtml(tlFullStoryUrl, fsResponse, System.currentTimeMillis()));
								foundProfileUrls.putAll(Parser.findProfileUrls(tlFullStoryUrl, fsResponse));
							}
						}

						if (count < 5) {
							List<String> seeMoreTimelineUrls = new ArrayList<>();
							seeMoreTimelineUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Show more", 0));
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
				showMoreUrls.addAll(Parser.getUrls(profileUrl, response, null, "Show More", 0));
				int count = 0;
				while (showMoreUrls.size() > 0) {
					String timelineUrl = showMoreUrls.remove(0);
					LOG.info("timelineUrl - {}", timelineUrl);

					if (!StringUtils.isEmpty(timelineUrl)) {
						String tlResponse = crawl(timelineUrl, http, proxy);
						AccountStatus tlResponseKOT = Parser.verifyResponseHtml(timelineUrl, tlResponse, true);
						if (!tlResponseKOT.equals(AccountStatus.ACTIVE)) {
							writeCrawledData3(account.getUsername(), profileUrl, crawledData, savedDir);
							sendOutLinks(account.getUsername(), foundProfileUrls);
							retStatus = responseKOT;
							return retStatus;
						}
						crawledData.add(new FbUrlToHtml(timelineUrl, tlResponse, System.currentTimeMillis()));
						foundProfileUrls.putAll(Parser.findProfileUrls(timelineUrl, tlResponse));

						Set<String> tlFullStoryUrls = new HashSet<>();
						tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Full Story", 0));
						if (tlFullStoryUrls.size() < 1) {
							tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Comment", 1));
						}
						fullStoryUrls.addAll(tlFullStoryUrls);

						if (count < 5) {
							List<String> seeMoreTimelineUrls = new ArrayList<>();
							seeMoreTimelineUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Show More", 0));
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
					AccountStatus fsResponseKOT = Parser.verifyResponseHtml(fullStoryUrl, fsResponse, true);
					if (!fsResponseKOT.equals(AccountStatus.ACTIVE)) {
						writeCrawledData3(account.getUsername(), profileUrl, crawledData, savedDir);
						sendOutLinks(account.getUsername(), foundProfileUrls);
						retStatus = fsResponseKOT;
						return retStatus;
					}
					crawledData.add(new FbUrlToHtml(fullStoryUrl, fsResponse, System.currentTimeMillis()));
					foundProfileUrls.putAll(Parser.findProfileUrls(fullStoryUrl, fsResponse));
				}
			}

			// collect profile URLs and crawled data
			sendOutLinks(account.getUsername(), foundProfileUrls);
			writeCrawledData3(account.getUsername(), profileUrl, crawledData, savedDir);
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
			retStatus = AccountStatus.ERROR_UNKNOWN;
		}

		return retStatus;
	}

	private static void writeCrawledData3(String username, String profileUrl, List<FbUrlToHtml> crawledData, String savedDir) {
		try {
			if (!crawledData.isEmpty()) {
				LOG.info("crawledData size {} - profile {}", crawledData.size(), profileUrl);

				String fIdUn = "";
				Map<String, String> retMap = Parser.extractUsernameOrIdFromUrl(profileUrl);
				if (retMap.containsKey("username")) {
					fIdUn = retMap.get("username");
				} else if (retMap.containsKey("id")) {
					fIdUn = retMap.get("id");
				} else if (retMap.containsKey("groupId")) {
					fIdUn = retMap.get("groupId");
				} else if (retMap.containsKey("pageId")) {
					fIdUn = retMap.get("pageId");
				}
				if (StringUtils.isEmpty(fIdUn)) {
					fIdUn = "df" + String.valueOf(System.currentTimeMillis());
				}

				String filename = savedDir + fIdUn + "_" + String.valueOf(System.currentTimeMillis()) + ".nfbo";
				FileUtils.writeObject2File(new File(filename), crawledData, false);
				URI profileUri = new URI(profileUrl);
				String temp = Parser.normalizeProfileUrl(profileUri);
				if (StringUtils.isEmpty(temp)) {
					ServiceOutlinks.addCrawledUrl(username, profileUrl);
				} else {
					String[] parts = StringUtils.split(temp, "|");
					String sentUrl = parts[1];
					ServiceOutlinks.addCrawledUrl(username, sentUrl);
				}
			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
	}

	public AccountStatus trackingProfile(String profileUrl, String urlFrom) {

		String savedDir = "storage/tracking/";
		AccountStatus retStatus = AccountStatus.ACTIVE;
		try {
			List<FbUrlToHtml> crawledData = new ArrayList<>();
			LOG.info(FacebookMessage.CRAWL_PROFILE_START, profileUrl);

			// crawl profile URL
			String response = crawl(profileUrl, http, proxy);
			LOG.info("url: {} -- response: {}", profileUrl, response);
			AccountStatus responseKOT = Parser.verifyResponseHtml(profileUrl, response, true);
			Document profileHTMLDoc = Jsoup.parse(response);
			if (!responseKOT.equals(AccountStatus.ACTIVE)) {
				LOG.warn("URL FAILED - {}", profileUrl);
				retStatus = responseKOT;
				return retStatus;
			}
			crawledData.add(new FbUrlToHtml(profileUrl, response, System.currentTimeMillis()));

			Map<String, String> foundProfileUrls = new HashMap<>();
			foundProfileUrls.putAll(Parser.findProfileUrls(profileUrl, response));

			List<String> timelineUrls = new ArrayList<>();
			timelineUrls.addAll(Parser.getUrls(profileUrl, response, null, "Timeline", 0));

			Set<String> fullStoryUrls = new HashSet<>();
			if (StringUtils.equalsIgnoreCase(urlFrom, "trungnt3")) {
				fullStoryUrls.addAll(Parser.getUrls(profileUrl, response, null, "Full Story", 0));
				if (fullStoryUrls.size() < 1) {
					fullStoryUrls.addAll(Parser.getUrls(profileUrl, response, null, "Comment", 1));
				}
			}

			if (!timelineUrls.isEmpty()) {
				int count = 0;
				while (timelineUrls.size() > 0) {
					String timelineUrl = timelineUrls.remove(0);
					LOG.info("timelineUrl - {}", timelineUrl);

					if (!StringUtils.isEmpty(timelineUrl)) {
						String tlResponse = crawl(timelineUrl, http, proxy);
						AccountStatus tlResponseKOT = Parser.verifyResponseHtml(timelineUrl, tlResponse, true);
						if (!tlResponseKOT.equals(AccountStatus.ACTIVE)) {
							writeCrawledData2(account.getUsername(), profileUrl, crawledData, savedDir, urlFrom);
							sendOutLinks(account.getUsername(), foundProfileUrls);
							retStatus = responseKOT;
							return retStatus;
						}
						crawledData.add(new FbUrlToHtml(timelineUrl, tlResponse, System.currentTimeMillis()));
						foundProfileUrls.putAll(Parser.findProfileUrls(timelineUrl, tlResponse));

						if (StringUtils.equalsIgnoreCase(urlFrom, "trungnt3")) {
							Set<String> tlFullStoryUrls = new HashSet<>();
							tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Full Story", 0));
							if (tlFullStoryUrls.size() < 1) {
								tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Comment", 1));
							}

							if (!tlFullStoryUrls.isEmpty()) {
								for (String tlFullStoryUrl : tlFullStoryUrls) {
									String fsResponse = crawl(tlFullStoryUrl, http, proxy);
									AccountStatus fsResponseKOT = Parser.verifyResponseHtml(tlFullStoryUrl, fsResponse, true);
									if (!fsResponseKOT.equals(AccountStatus.ACTIVE)) {
										writeCrawledData2(account.getUsername(), profileUrl, crawledData, savedDir, urlFrom);
										sendOutLinks(account.getUsername(), foundProfileUrls);
										retStatus = fsResponseKOT;
										return retStatus;
									}
									crawledData.add(new FbUrlToHtml(tlFullStoryUrl, fsResponse, System.currentTimeMillis()));
									foundProfileUrls.putAll(Parser.findProfileUrls(tlFullStoryUrl, fsResponse));
								}
							}
						}

						if (count < 5) {
							List<String> seeMoreTimelineUrls = new ArrayList<>();
							seeMoreTimelineUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Show more", 0));
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
				showMoreUrls.addAll(Parser.getUrls(profileUrl, response, null, "Show More", 0));
				int count = 0;
				while (showMoreUrls.size() > 0) {
					String timelineUrl = showMoreUrls.remove(0);
					LOG.info("timelineUrl - {}", timelineUrl);

					if (!StringUtils.isEmpty(timelineUrl)) {
						String tlResponse = crawl(timelineUrl, http, proxy);
						AccountStatus tlResponseKOT = Parser.verifyResponseHtml(timelineUrl, tlResponse, true);
						if (!tlResponseKOT.equals(AccountStatus.ACTIVE)) {
							writeCrawledData2(account.getUsername(), profileUrl, crawledData, savedDir, urlFrom);
							sendOutLinks(account.getUsername(), foundProfileUrls);
							retStatus = responseKOT;
							return retStatus;
						}
						crawledData.add(new FbUrlToHtml(timelineUrl, tlResponse, System.currentTimeMillis()));
						foundProfileUrls.putAll(Parser.findProfileUrls(timelineUrl, tlResponse));

						if (StringUtils.equalsIgnoreCase(urlFrom, "trungnt3")) {
							Set<String> tlFullStoryUrls = new HashSet<>();
							tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Full Story", 0));
							if (tlFullStoryUrls.size() < 1) {
								tlFullStoryUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Comment", 1));
							}
							fullStoryUrls.addAll(tlFullStoryUrls);
						}

						if (count < 5) {
							List<String> seeMoreTimelineUrls = new ArrayList<>();
							seeMoreTimelineUrls.addAll(Parser.getUrls(timelineUrl, tlResponse, null, "Show More", 0));
							if (!seeMoreTimelineUrls.isEmpty()) {
								LOG.info("Show more size {}", seeMoreTimelineUrls.size());
								showMoreUrls.add(seeMoreTimelineUrls.get(0));
								count++;
							}
						}
					}
				}
			}

			if (StringUtils.equalsIgnoreCase(urlFrom, "trungnt3")) {
				if (!fullStoryUrls.isEmpty()) {
					for (String fullStoryUrl : fullStoryUrls) {
						String fsResponse = crawl(fullStoryUrl, http, proxy);
						AccountStatus fsResponseKOT = Parser.verifyResponseHtml(fullStoryUrl, fsResponse, true);
						if (!fsResponseKOT.equals(AccountStatus.ACTIVE)) {
							writeCrawledData2(account.getUsername(), profileUrl, crawledData, savedDir, urlFrom);
							sendOutLinks(account.getUsername(), foundProfileUrls);
							retStatus = fsResponseKOT;
							return retStatus;
						}
						crawledData.add(new FbUrlToHtml(fullStoryUrl, fsResponse, System.currentTimeMillis()));
						foundProfileUrls.putAll(Parser.findProfileUrls(fullStoryUrl, fsResponse));
					}
				}
			}

			// collect profile URLs and crawled data
			sendOutLinks(account.getUsername(), foundProfileUrls);
			writeCrawledData2(account.getUsername(), profileUrl, crawledData, savedDir, urlFrom);
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
			retStatus = AccountStatus.ERROR_UNKNOWN;
		}

		return retStatus;
	}

	private static void writeCrawledData2(String username, String profileUrl, List<FbUrlToHtml> crawledData, String savedDir, String urlFrom) {
		try {
			if (!crawledData.isEmpty()) {
				LOG.info("crawledData size {} - profile {}", crawledData.size(), profileUrl);

				FacebookObject fbObj = ParsingUtils.fromHtmltoFacebookObject(crawledData);
				try {
					LOG.info("Parsed_Object {}", JSON.encode(fbObj));
				} catch (Exception e) {
				}
//                if (StringUtils.equalsIgnoreCase(urlFrom, "trungnt3")) {
				if (fbObj != null) {
					RunDeepTracking.facebookObjectQueue.add(fbObj);
				}
//                }

				ProfileInfo profileInfo = new ProfileInfo();
				profileInfo.profile = StringUtils.replace(profileUrl, "//m.facebook.com/", "//facebook.com/");
				profileInfo.timeCrawled = System.currentTimeMillis();
				List<PostInfo> postInfos = new ArrayList<>();
				List<Post> fbPosts = fbObj.getPosts();
				for (Post fbPost : fbPosts) {
					if (fbPost != null) {
						String url = fbPost.getUrl();
						Date postTime = fbPost.getPostTime();
						long time = System.currentTimeMillis();
						if (postTime != null) {
							time = postTime.getTime();
						}
						String content = fbPost.getContent();
						postInfos.add(new PostInfo(url, time, content, fbPost.getCommentsCount(), fbPost.getLikesCount()));
					}
				}
				profileInfo.listPost = postInfos;

				if (StringUtils.equalsIgnoreCase(urlFrom, "chuyennd2")) {
					RunDeepTracking.profileInfoQueue.add(profileInfo);
				}

				String fIdUn = "";
				Map<String, String> retMap = Parser.extractUsernameOrIdFromUrl(profileUrl);
				if (retMap.containsKey("username")) {
					fIdUn = retMap.get("username");
				} else if (retMap.containsKey("id")) {
					fIdUn = retMap.get("id");
				} else if (retMap.containsKey("groupId")) {
					fIdUn = retMap.get("groupId");
				} else if (retMap.containsKey("pageId")) {
					fIdUn = retMap.get("pageId");
				}
				if (StringUtils.isEmpty(fIdUn)) {
					fIdUn = "df" + String.valueOf(System.currentTimeMillis());
				}

				String filename = savedDir + fIdUn + "_" + String.valueOf(System.currentTimeMillis()) + ".nfbo";
				FileUtils.writeObject2File(new File(filename), crawledData, false);
				URI profileUri = new URI(profileUrl);
				String temp = Parser.normalizeProfileUrl(profileUri);

			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
	}
}
