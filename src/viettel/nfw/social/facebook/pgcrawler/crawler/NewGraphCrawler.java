package viettel.nfw.social.facebook.pgcrawler.crawler;

import com.restfb.exception.FacebookException;
import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.updatenews.graph.FacebookGraphActions;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectType;
import viettel.nfw.social.facebook.updatenews.graph.entities.ProfileType;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.utils.SimpleTimer;

/**
 *
 * @author Duong
 */
public class NewGraphCrawler {

	private static final Logger LOG = LoggerFactory.getLogger(NewGraphCrawler.class);

	private static final int MAX_REQUEST_IN_DAY = 1000000;
	private static final int MAX_SLOW = 2;
	private static final int MAX_SPEED = 4000;
	private static final long TIME_TO_REFRESH_MILLIS = 30 * 60 * 1000;
	private static final int MIN_SLEEP = 300; // ms
	private static final int MAX_SLEEP = 500; // ms
	private static final int NUMBER_RETRY = 3;

	private final FacebookGraphActions facebookGraphActions;
	private final ProfileDatabaseHandler db;
	private final Proxy proxy;

	public NewGraphCrawler(FacebookApp appInfo, ProfileDatabaseHandler db, Proxy proxy) {
		this.proxy = proxy;
		this.db = db;
		this.facebookGraphActions = new FacebookGraphActions(appInfo);
		this.facebookGraphActions.initApp();
	}

	public String getAppID() {
		return this.facebookGraphActions.getAppInfo().getAppID();
	}

	public void updateApp(FacebookApp newAppInfo) {
		facebookGraphActions.setAppInfo(newAppInfo);
		Funcs.sleep(300);
		facebookGraphActions.refreshApp();
		Funcs.sleep(300);
		checkToken(true);
	}

	public boolean checkToken(boolean isFirstInit) {
		boolean isOK = true;
		boolean isTokenExpireSoon;
		if (isFirstInit) {
			boolean isValid = false;
			for (int i = 0; i < NUMBER_RETRY; i++) {
				try {
					isValid = facebookGraphActions.isUserAccessTokenValid(facebookGraphActions.getAppInfo().getUserAccessToken());
					break;
				} catch (Exception e) {
					LOG.error(e.getMessage(), e);
				}
				Funcs.sleep(1000L);
			}

			// if token still valid, return false. otherwise, return true
			isTokenExpireSoon = !isValid;
		} else {
			// check token will expire soon?
			isTokenExpireSoon = facebookGraphActions.isTokenExpireSoon();
		}

		if (isTokenExpireSoon) {
			LOG.info("Token of app {} will be expired soon! Get the new one!", facebookGraphActions.getAppInfo().getAppID());
			// get new user access token
			String newUserAccessToken = facebookGraphActions.generateLongLiveToken(facebookGraphActions.getAppInfo().getUserAccessToken());
			if (StringUtils.isNotEmpty(newUserAccessToken)) {
				// update new user access token
				facebookGraphActions.getAppInfo().setUserAccessToken(newUserAccessToken);
				try {
					// update to database
					db.saveFacebookApp(facebookGraphActions.getAppInfo());
					LOG.info("Update_Facebook_App_To_DB");
				} catch (IOException ex) {
					LOG.warn("Failed to update Facebook App info to database", ex);
				}
				// refresh the app with new access token
				if (facebookGraphActions.refreshApp()) {
					LOG.info("Refresh app OK");
					// re debug token to update new debug token info
					boolean isNewTokenValid = false;

					for (int i = 0; i < NUMBER_RETRY; i++) {
						try {
							isNewTokenValid = facebookGraphActions.isUserAccessTokenValid(newUserAccessToken);
							break;
						} catch (Exception e) {
							LOG.error(e.getMessage(), e);
						}
						Funcs.sleep(1000L);
					}

					if (isNewTokenValid) {
						LOG.info("New token valid after refresh");
					} else {
						LOG.warn("New token not valid after refresh!");
						isOK = false;
					}
				} else {
					LOG.warn("Refresh app FAILED");
					isOK = false;
				}
			} else {
				LOG.warn("Cannot get new access token. Stop this app {}", facebookGraphActions.getAppInfo().getAppID());
				isOK = false;
			}
		}

		return isOK;
	}

	public FacebookObject doIt(String profileId, ProfileType profileType, List<String> crawledPosts, boolean isRecrawlProfileInfo) {
		LOG.info("App {} visit {}", facebookGraphActions.getAppInfo().getAppID(), profileId);
		SimpleTimer st = new SimpleTimer();
		Pair<viettel.nfw.social.model.facebook.FacebookObject, Integer> result = null;
		if (profileType.equals(ProfileType.PAGE_REAL)
			|| profileType.equals(ProfileType.GROUP_PUBLIC)) {
			try {
				result = downloadFullFacebookProfile(profileId, profileType, crawledPosts, isRecrawlProfileInfo);
			} catch (Exception e) {
				LOG.error("Error while executing method downloadFullFacebookProfile with profile id " + profileId, e);
			}
		} else {
			LOG.warn("Not support this object!");
		}
		long crawledTime = st.getTimeAndReset();
		FacebookObject facebookObject = null;
		if (result != null) {
			facebookObject = result.first;
			int countRequests = result.second;
			// calculate speed
			int speed = 0;
			if (countRequests != 0) {
				speed = (int) crawledTime / countRequests;
			}
			LOG.info("App {} crawled object {} in {} msec with {} requests - speed {} - curtime {}",
				new Object[]{facebookGraphActions.getAppInfo().getAppID(), profileId, crawledTime, countRequests, speed, System.currentTimeMillis()});
		}
		return facebookObject;
	}

	private Pair<viettel.nfw.social.model.facebook.FacebookObject, Integer> downloadFullFacebookProfile(
		String profileId, ProfileType profileType, List<String> crawledPosts, boolean isRecrawlProfileInfo) {
		// count requests sent to facebook
		int countRequests = 0;
		// check if input profile id is empty or null
		if (StringUtils.isEmpty(profileId)) {
			return new Pair<>(null, countRequests);
		}
		// init facebook object
		viettel.nfw.social.model.facebook.FacebookObject facebookObject = new viettel.nfw.social.model.facebook.FacebookObject();
		List<viettel.nfw.social.model.facebook.Post> vPosts = new ArrayList<>();
		List<viettel.nfw.social.model.facebook.Comment> vComments = new ArrayList<>();
		List<viettel.nfw.social.model.facebook.Like> vLikes = new ArrayList<>();

		// download profile info
		viettel.nfw.social.model.facebook.Profile profile = null;
		if (isRecrawlProfileInfo) {
			for (int i = 0; i < NUMBER_RETRY; i++) {
				try {
					ObjectType objectType = ObjectType.UNKNOWN;
					if (profileType.equals(ProfileType.PAGE_REAL)) {
						objectType = ObjectType.PAGE;
					} else if (profileType.equals(ProfileType.GROUP_PUBLIC)) {
						objectType = ObjectType.GROUP;
					}
					// download profile info
					Pair<viettel.nfw.social.model.facebook.Profile, Integer> profilePair = facebookGraphActions.getProfileInfo(profileId, objectType);
					profile = profilePair.first;
					countRequests += profilePair.second;
					break;
				} catch (FacebookException ex) {
					LOG.error("Error while getting profile info of " + profileId, ex);
					db.sendFailProfileId(profileId);
					return new Pair<>(null, countRequests);
				} catch (IOException ex) {
					LOG.error(ex.getMessage(), ex);
					facebookGraphActions.refreshApp();
					Funcs.sleep(Funcs.randInt(MIN_SLEEP, MAX_SLEEP));
				}
			}
		}

		// download posts, comments, likes of this profile
		Map<String, Integer> postId2Status = new HashMap<>(); // 0 is new, 1 is old
		// query to get new posts
		for (int i = 0; i < NUMBER_RETRY; i++) {
			try {
//				Pair<List<String>, Integer> postIdsPair = facebookGraphActions.getNewPosts(profileId, crawledPosts);
				Pair<Pair<Set<String>, Set<String>>, Integer> postIdsData
					= FacebookGraphActions.getPosts(profileId, crawledPosts, facebookGraphActions.getAppInfo().getUserAccessToken(), proxy);
				if (postIdsData.first != null) {
					// new posts Id
					int newPostsSize = 0;
					int oldPostsSize = 0;
					if (postIdsData.first.first != null) {
						newPostsSize = postIdsData.first.first.size();
						for (String newPostId : postIdsData.first.first) {
							postId2Status.put(newPostId, 0);
						}
					}
					// old posts Id
					if (postIdsData.first.second != null) {
						oldPostsSize = postIdsData.first.second.size();
						for (String oldPostId : postIdsData.first.second) {
							postId2Status.put(oldPostId, 1);
						}
					}
					LOG.info("DISCOVER_POSTS_NEW_{}_OLD_{}_IN_{}", new Object[]{newPostsSize, oldPostsSize, profileId});
				}
				countRequests += postIdsData.second;
				break;
			} catch (FacebookException ex) {
				LOG.error("Error while getting list posts of " + profileId, ex);
				facebookGraphActions.refreshApp();
				Funcs.sleep(Funcs.randInt(MIN_SLEEP, MAX_SLEEP));
			}
		}
		// get detail info of each new post
		for (Map.Entry<String, Integer> entry : postId2Status.entrySet()) {
			String postId = entry.getKey();
			Integer status = entry.getValue();

			// check postId containt is list fail
//			if (db.isFailPostId(postId)) {
//				LOG.info("IN_FAIL_POST_IDS_LIST {}", postId);
//				continue;
//			}
			LOG.debug("START_DOWNLOAD_POST_ID_{}", postId);

			Funcs.sleep(Funcs.randInt(MIN_SLEEP, MAX_SLEEP));
			viettel.nfw.social.model.facebook.Post post = null;
			for (int i = 0; i < NUMBER_RETRY; i++) {
				// get by build own url
				try {
					Pair<viettel.nfw.social.model.facebook.Post, Integer> postPair
						= FacebookGraphActions.getSinglePostInfo(postId, ObjectType.POST, facebookGraphActions.getAppInfo().getUserAccessToken(), proxy);
					post = postPair.first;
					countRequests += postPair.second;
				} catch (Exception e) {
					LOG.error("Error while getting post info by manual " + postId, e);
				}
				if (post == null) {
					// try with restfb
					try {
						Pair<viettel.nfw.social.model.facebook.Post, Integer> postPair = facebookGraphActions.getSinglePostInfo(postId, ObjectType.POST);
						post = postPair.first;
						countRequests += postPair.second;
					} catch (IOException | FacebookException ex) {
						LOG.error("Error while getting post info by using lib RestFb " + postId, ex);
						facebookGraphActions.refreshApp();
						Funcs.sleep(Funcs.randInt(MIN_SLEEP, MAX_SLEEP));
					}
				}
				if (post != null) {
					break;
				} else {
					db.sendFailPostId(postId);
				}
			}

			if (post != null) {
				if (status == 1) {
					post.setUpdateTime(new Date());
				}
				vPosts.add(post);
				// get comments of posts
				try {
					if (post.getCommentsCount() > 0) {
						Pair<List<viettel.nfw.social.model.facebook.Comment>, Integer> commentsPair = facebookGraphActions.getCommentsOfPost(post.getId());
						if (commentsPair.first != null) {
							vComments.addAll(commentsPair.first);
						}
						countRequests += commentsPair.second;
					}
				} catch (IOException | FacebookException ex) {
					LOG.error("Error while getting comments of post " + postId, ex);
				}

				// get likes of posts
				try {
					if (post.getLikesCount() > 0) {
						Pair<Map<String, String>, Integer> likesPair = facebookGraphActions.getLikesOfPost(post.getId(), post.getLikesCount());
						if (likesPair.first != null) {
							List<String> likedProfileIds = new ArrayList<>();
							String item;
							for (Map.Entry<String, String> lEntry : likesPair.first.entrySet()) {
								String lProfileId = lEntry.getKey();
								String lProfileName = lEntry.getValue();
								if (StringUtils.isEmpty(lProfileName)) {
									item = lProfileId;
								} else {
									item = lProfileId + "\t" + lProfileName;
								}
								if (StringUtils.isNotEmpty(item)) {
									likedProfileIds.add(item);
								}

//								if (StringUtils.isNotEmpty(lProfileId) && StringUtils.isNotEmpty(lProfileName)) {
//									try {
//										CrawlerManager.id2fullnameQueue.put(new Pair<>(lProfileId, lProfileName));
//									} catch (InterruptedException ex) {
//										LOG.error("Error while putting id and fullname", ex);
//									}
//								}
							}

							viettel.nfw.social.model.facebook.Like like = new viettel.nfw.social.model.facebook.Like();
							like.setId(post.getId());
							like.setLikedObjectType("post");
							like.setLikedProfileIds(likedProfileIds);
							like.setCreateTime(new Date());

							vLikes.add(like);
						}
						countRequests += likesPair.second;
					}
				} catch (FacebookException ex) {
					LOG.error("Error while getting likes of post " + postId, ex);
				}
			}
		}

		facebookObject.setInfo(profile);
		facebookObject.setPosts(vPosts);
		facebookObject.setComments(vComments);
		facebookObject.setLikes(vLikes);

		return new Pair<>(facebookObject, countRequests);
	}

}
