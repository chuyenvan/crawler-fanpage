package viettel.nfw.social.facebook.updatenews;

import com.restfb.exception.FacebookException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.facebook.updatenews.graph.FacebookGraphActions;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectType;
import viettel.nfw.social.facebook.updatenews.repo.FailedObjectRequest;
import viettel.nfw.social.facebook.updatenews.repo.ProfilePostsRepository;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.utils.DateUtils;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.utils.SerializeObjectUtils;
import vn.viettel.utils.SimpleTimer;

/**
 * Graph Application Download
 *
 * @author duongth5
 */
public class GraphCrawler implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(GraphCrawler.class);
	private static final int MAX_REQUEST_IN_DAY = 1000000;
	private static final int MAX_SLOW = 2;
	private static final int MAX_SPEED = 4000;
	private static final int MIN_SLEEP = 5 * 60 * 1000;
	private static final int MAX_SLEEP = 15 * 60 * 1000;
	private static final int NUMBER_RETRY = 2;
	private static final long TIME_TO_REFRESH_MILLIS = 30 * 60 * 1000;

	private static final BlockingQueue<ObjectRequest> queue = new ArrayBlockingQueue<>(RunUpdateNews.MAX_CAPACITY);
	private AtomicInteger numberRequestInDay;
	private AtomicInteger countSlow;
	private FacebookApp appInfo;
	private AtomicBoolean isSleepOrReachLimit;
	private AtomicBoolean isBusy;

	private FacebookGraphActions facebookGraphActions;

	public GraphCrawler(FacebookApp appInfo) {
		this.appInfo = appInfo;
		this.isSleepOrReachLimit = new AtomicBoolean(false);
		this.isBusy = new AtomicBoolean(false);
		this.numberRequestInDay = new AtomicInteger(0);
		this.countSlow = new AtomicInteger(0);
		this.facebookGraphActions = new FacebookGraphActions(appInfo);
	}

	public boolean isSleepOrReachLimit() {
		return isSleepOrReachLimit.get();
	}

	public boolean addToBigQueue(ObjectRequest objectRequest) {
		boolean isOK = true;
		try {
			queue.put(objectRequest);
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
		return isOK;
	}

	private static void updateFacebookAppInfo(FacebookApp appInfo) throws IOException {
		byte[] keyByteArr = appInfo.getAppID().getBytes();
		byte[] valueByteArr = SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(appInfo);
		RunUpdateNews.facebookAppRepository.write(keyByteArr, valueByteArr);
	}

	@Override
	public void run() {
		String threadName = "App-" + appInfo.getAppID();
		Thread.currentThread().setName(threadName);

		// init the app
		if (facebookGraphActions.initApp()) {
			LOG.info("Init app OK");
		} else {
			LOG.warn("Init app FAILED");
			return;
		}
		// debug current access token
		try {
			boolean isAccessTokenValid = facebookGraphActions.isUserAccessTokenValid(appInfo.getUserAccessToken());
			LOG.info("isAccessTokenValid {}", isAccessTokenValid);
			if (!isAccessTokenValid) {
				// renew access token
				String newUserAccessToken = facebookGraphActions.generateLongLiveToken(appInfo.getUserAccessToken());
				if (StringUtils.isNotEmpty(newUserAccessToken)) {
					appInfo.setUserAccessToken(newUserAccessToken);
					try {
						// update to database
						LOG.info("Update Facebook App");
						updateFacebookAppInfo(appInfo);
					} catch (IOException ex) {
						LOG.warn("Failed to update Facebook App info to database", ex);
						return;
					}
					// refresh the app with new access token
					facebookGraphActions.setAppInfo(appInfo);
					if (facebookGraphActions.refreshApp()) {
						LOG.info("Refresh app OK");
					} else {
						LOG.warn("Refresh app FAILED");
						return;
					}
				} else {
					LOG.warn("Cannot get new access token. Stop this app");
					return;
				}
			} else {
				LOG.info("User Access Token still valid {}", appInfo.getUserAccessToken());
			}
		} catch (FacebookException ex) {
			// debug token failed
			LOG.info(ex.getMessage(), ex);
			return;
		}

		long midnightTime = DateUtils.getMidnight();
		long startTime = System.currentTimeMillis();
		while (true) {
			// handle case when api calls reach limit
			// check number requests in day reach limit
			// if reach limit, make it sleep till tomorrow
			int currentRequestInDay = numberRequestInDay.get();
			if (currentRequestInDay > MAX_REQUEST_IN_DAY) {
				isSleepOrReachLimit.set(true);
				// reset number requests, [option: clear queue]
				numberRequestInDay.set(0);
				// sleep till tomorrow
				midnightTime = DateUtils.getMidnight();
				long timeToMidnight = midnightTime - System.currentTimeMillis();
				long rand = Funcs.randInt(30 * 60 * 1000, 45 * 60 * 1000);
				long timeToSleep;
				if (timeToMidnight > 0) {
					timeToSleep = timeToMidnight + rand;
				} else {
					timeToSleep = rand;
				}
				LOG.info("Reach limit {}. Sleep night for {} ms", currentRequestInDay, timeToSleep);
				Funcs.sleep(timeToSleep);
				isSleepOrReachLimit.set(false);
			}

			// do the job
			isBusy.set(true);
			if (doJob()) {
				countSlow.getAndAdd(1);
			}
			isBusy.set(false);

			// handle case when time reachs midnight
			// handle case refresh app 
			try {
				long currentTime = System.currentTimeMillis();
				if ((currentTime - startTime) > TIME_TO_REFRESH_MILLIS
					|| countSlow.get() > MAX_SLOW) {
					isBusy.set(true);
					LOG.info("Time to refresh app {}, count slow {}", appInfo.getAppID(), countSlow.get());
					Funcs.sleep(Funcs.randInt(MIN_SLEEP, MAX_SLEEP));
					if (facebookGraphActions.refreshApp()) {
						LOG.info("DONE refresh app {}", appInfo.getAppID());
						startTime = System.currentTimeMillis();
						countSlow.set(0);
					} else {
						LOG.warn("APP {} IS KILLED", appInfo.getAppID());
						break;
					}
					isBusy.set(false);
				}
				if (currentTime > midnightTime) {
					LOG.info("Pass midnight");
					LOG.info("Number API calls of app {} in day is {}", appInfo.getAppID(), numberRequestInDay);
					isSleepOrReachLimit.set(true);
					Funcs.sleep(10 * 60 * 1000);
					midnightTime = DateUtils.getMidnight();
					LOG.info("New midnight: {}", new Date(midnightTime).toString());
					// reset number requests, [option: clear queue]
					numberRequestInDay.set(0);
					// sleep
					long timeToSleep = Funcs.randInt(15 * 60 * 1000, 20 * 60 * 1000);
					LOG.info("Sleep night for {} ms", timeToSleep);
					Funcs.sleep(timeToSleep);
					isSleepOrReachLimit.set(false);
					LOG.info("WAKE UP!");
				}
			} catch (Exception ex) {
				LOG.error(ex.getMessage(), ex);
			}

			Funcs.sleep(Funcs.randInt(400, 800));
		}
	}

	/**
	 * Do download job
	 *
	 * @return Is job slow? true if yes, false if no
	 */
	private boolean doJob() {

		boolean isSlow = false;
		ObjectRequest objectRequest = queue.poll();
		if (objectRequest != null) {
			// do crawl Job
			LOG.info("Received Object {}", objectRequest.toString());
			SimpleTimer st = new SimpleTimer();
			Pair<viettel.nfw.social.model.facebook.FacebookObject, Integer> result = null;
			if (objectRequest.objectType.equals(ObjectType.GROUP)
				|| objectRequest.objectType.equals(ObjectType.PAGE)) {
				result = doCrawlProfile(objectRequest);
			} else if (objectRequest.objectType.equals(ObjectType.POST)) {
				result = doCrawlSinglePost(objectRequest);
			} else {
				LOG.warn("Not support this object!");
			}
			long crawledTime = st.getTimeAndReset();
			if (result != null) {
				FacebookObject facebookObject = result.first;
				int countRequests = result.second;
				numberRequestInDay.getAndAdd(countRequests);
				// calculate speed
				LOG.info("Crawled object {} in {} msec with {} requests",
					new Object[]{objectRequest.toString(), crawledTime, countRequests});
				if (countRequests != 0) {
					int speed = (int) crawledTime / countRequests;
					LOG.info("Speed {}", speed);
					if (speed > MAX_SPEED) {
						isSlow = true;
					}
				}
				// doing after
				after(facebookObject, objectRequest);
			}
		}
		return isSlow;
	}

	private void after(FacebookObject facebookObject, ObjectRequest objectRequest) {

		if (facebookObject != null) {
			// add to send to BigData
			SendObjectToBigDataImpl.facebookObjectQueue.add(facebookObject);

			if (facebookObject.getInfo() != null) {
				// send to master
				String url = "";
				String profileId = facebookObject.getInfo().getId();
				if (StringUtils.isNotEmpty(profileId)) {
					String account = "APP_" + appInfo.getAppID();
					if (objectRequest.objectType.equals(ObjectType.GROUP)) {
						url = "https://m.facebook.com/groups/" + profileId;
					} else if (objectRequest.objectType.equals(ObjectType.PAGE)) {
						url = "https://m.facebook.com/profile.php?id=" + profileId;
					}
					if (StringUtils.isNotEmpty(url)) {
						ServiceOutlinks.addCrawledUrl(account, url);
					}
				}

				// save to mapping repo
				if (StringUtils.isNotEmpty(profileId)) {
					String profileUsername = facebookObject.getInfo().getUsername();
					if (StringUtils.isEmpty(profileUsername)) {
						profileUsername = profileId;
					}
					try {
						RunUpdateNews.mappingUsername2IdRepositpory.write(profileId.trim().getBytes(), profileUsername.trim().getBytes());
					} catch (IOException ex) {
						LOG.error(ex.getMessage(), ex);
					}
				}

			}

			// save to db
			try {
				String key = appInfo.getAppID() + "_" + String.valueOf(System.currentTimeMillis());
				RunUpdateNews.crawledFbObjRepository.write(key.getBytes(), SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(facebookObject));
				LOG.info("Writed {} to db", key);

			} catch (Exception ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}

		// remove from justSent
		try {
			String keyInJustSent
				= String.format(RunUpdateNews.FORMAT_COMPOSITE_KEY,
					objectRequest.socialType, objectRequest.objectID, objectRequest.objectType);
			ManageObjectRequestImpl.justSentMap.remove(keyInJustSent);
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
		// write to db last time crawled
		String lastCrawledObjKey
			= String.format(RunUpdateNews.FORMAT_COMPOSITE_KEY,
				objectRequest.socialType, objectRequest.objectID, objectRequest.objectType);
		String currentTimeValue = String.valueOf(System.currentTimeMillis());
		try {
			RunUpdateNews.lastCrawledRepository.write(lastCrawledObjKey.getBytes(), currentTimeValue.getBytes());
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}

	}

	private Pair<viettel.nfw.social.model.facebook.FacebookObject, Integer> doCrawlProfile(ObjectRequest objectRequest) {
		int countRequests = 0;
		if (StringUtils.isEmpty(objectRequest.objectID)) {
			return new Pair<>(null, countRequests);
		}
		viettel.nfw.social.model.facebook.FacebookObject facebookObject = new viettel.nfw.social.model.facebook.FacebookObject();
		List<viettel.nfw.social.model.facebook.Post> vPosts = new ArrayList<>();
		List<viettel.nfw.social.model.facebook.Comment> vComments = new ArrayList<>();
		List<viettel.nfw.social.model.facebook.Like> vLikes = new ArrayList<>();

		viettel.nfw.social.model.facebook.Profile profile = null;
		for (int i = 0; i < NUMBER_RETRY; i++) {
			try {
				Pair<viettel.nfw.social.model.facebook.Profile, Integer> profilePair = facebookGraphActions.getProfileInfo(objectRequest.objectID, objectRequest.objectType);
				profile = profilePair.first;
				countRequests += profilePair.second;
				break;
			} catch (FacebookException ex) {
				LOG.error("Error with object {}", objectRequest.toString());
				String errorMessage = ex.getMessage();
				LOG.error(errorMessage, ex);
				RunUpdateNews.failedObjRepository.getFailedObjectRequestsQueue()
					.add(new FailedObjectRequest.Wrapper(objectRequest, errorMessage));
				return new Pair<>(null, countRequests);
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
				LOG.info("Retry getProfileInfo");
				facebookGraphActions.refreshApp();
				Funcs.sleep(Funcs.randInt(200, 400));
			}
		}

		if (profile != null) {
			byte[] value = RunUpdateNews.profiePostsRepository.get(objectRequest.objectID.getBytes());
			ProfilePostsRepository.LastestProfilePostList repoPosts;
			if (value == null) {
				LOG.info("Object id {} not existed in db.", objectRequest.objectID);
				repoPosts = new ProfilePostsRepository.LastestProfilePostList(100000);
			} else {
				repoPosts = (ProfilePostsRepository.LastestProfilePostList) SerializeObjectUtils.readingObjectFromByteArrayUsingGzip(value);
				LOG.info("Object id {} existed in db, have {} records", objectRequest.objectID, repoPosts.getPosts().size());
			}
			List<ProfilePostsRepository.ProfilePost> visitedPosts = repoPosts.getPosts();

			String profileId = profile.getId();
			if (StringUtils.isNotEmpty(profileId)) {
				List<String> postIds = new ArrayList<>();
				for (int i = 0; i < NUMBER_RETRY; i++) {
					try {
						Pair<List<String>, Integer> postIdsPair = facebookGraphActions.getListPostsOfProfile(profileId, visitedPosts);
						if (postIdsPair.first != null) {
							if (postIdsPair.first.size() > 0) {
								postIds.addAll(postIdsPair.first);
							}
						}
						countRequests += postIdsPair.second;
					} catch (IOException | FacebookException ex) {
						LOG.error(ex.getMessage(), ex);
						LOG.info("Retry getListPostsOfProfile");
						facebookGraphActions.refreshApp();
						Funcs.sleep(Funcs.randInt(200, 400));
					}
				}
				if (postIds.size() > 0) {
					for (String postId : postIds) {
						Funcs.sleep(Funcs.randInt(200, 400));
						viettel.nfw.social.model.facebook.Post post = null;
						for (int i = 0; i < NUMBER_RETRY; i++) {
							try {
								Pair<viettel.nfw.social.model.facebook.Post, Integer> postPair = facebookGraphActions.getSinglePostInfo(postId, ObjectType.POST);
								post = postPair.first;
								countRequests += postPair.second;
							} catch (IOException | FacebookException ex) {
								LOG.error(ex.getMessage(), ex);
								LOG.info("Retry getSinglePostInfo");
								facebookGraphActions.refreshApp();
								Funcs.sleep(Funcs.randInt(200, 400));
							}
						}
						if (post != null) {
							vPosts.add(post);
							try {
								Pair<List<viettel.nfw.social.model.facebook.Comment>, Integer> commentsPair = facebookGraphActions.getCommentsOfPost(post.getId());
								if (commentsPair.first != null) {
									vComments.addAll(commentsPair.first);
								}
								countRequests += commentsPair.second;

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
									}

									viettel.nfw.social.model.facebook.Like like = new viettel.nfw.social.model.facebook.Like();
									like.setId(post.getId());
									like.setLikedObjectType("post");
									like.setLikedProfileIds(likedProfileIds);
									like.setCreateTime(new Date());

									vLikes.add(like);
								}
								countRequests += likesPair.second;
							} catch (IOException | FacebookException ex) {
								LOG.error(ex.getMessage(), ex);
							}
						}
					}
				}
			}
		}

		facebookObject.setInfo(profile);
		facebookObject.setPosts(vPosts);
		facebookObject.setComments(vComments);
		facebookObject.setLikes(vLikes);

		return new Pair<>(facebookObject, countRequests);
	}

	private Pair<viettel.nfw.social.model.facebook.FacebookObject, Integer> doCrawlSinglePost(ObjectRequest objectRequest) {

		int countRequests = 0;
		viettel.nfw.social.model.facebook.FacebookObject facebookObject = new viettel.nfw.social.model.facebook.FacebookObject();

		if (StringUtils.isEmpty(objectRequest.objectID)) {
			return new Pair<>(null, countRequests);
		}

		facebookObject.setInfo(null);
		List<viettel.nfw.social.model.facebook.Post> vPosts = new ArrayList<>();
		List<viettel.nfw.social.model.facebook.Comment> vComments = new ArrayList<>();
		List<viettel.nfw.social.model.facebook.Like> vLikes = new ArrayList<>();

		viettel.nfw.social.model.facebook.Post post = null;
		for (int i = 0; i < NUMBER_RETRY; i++) {
			try {
				Pair<viettel.nfw.social.model.facebook.Post, Integer> postPair = facebookGraphActions.getSinglePostInfo(objectRequest.objectID, objectRequest.objectType);
				post = postPair.first;
				countRequests += postPair.second;
			} catch (FacebookException ex) {
				String errorMessage = ex.getMessage();
				LOG.error(errorMessage, ex);
				RunUpdateNews.failedObjRepository.getFailedObjectRequestsQueue()
					.add(new FailedObjectRequest.Wrapper(objectRequest, errorMessage));
				return new Pair<>(null, countRequests);
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
				LOG.info("Retry getSinglePostInfo");
				facebookGraphActions.refreshApp();
				Funcs.sleep(Funcs.randInt(200, 400));
			}
		}

		if (post != null) {
			vPosts.add(post);
			String postId = post.getId();
			if (StringUtils.isNotEmpty(postId)) {
				try {
					Pair<List<viettel.nfw.social.model.facebook.Comment>, Integer> commentsPair = facebookGraphActions.getCommentsOfPost(post.getId());
					if (commentsPair.first != null) {
						vComments.addAll(commentsPair.first);
					}
					countRequests += commentsPair.second;

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
						}

						viettel.nfw.social.model.facebook.Like like = new viettel.nfw.social.model.facebook.Like();
						like.setId(post.getId());
						like.setLikedObjectType("post");
						like.setLikedProfileIds(likedProfileIds);
						like.setCreateTime(new Date());

						vLikes.add(like);
					}
					countRequests += likesPair.second;
				} catch (IOException | FacebookException ex) {
					LOG.error(ex.getMessage(), ex);
				}
			}
			facebookObject.setPosts(vPosts);
			facebookObject.setComments(vComments);
			facebookObject.setLikes(vLikes);
		}

		return new Pair<>(facebookObject, countRequests);
	}

}
