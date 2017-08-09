package viettel.nfw.social.facebook.pgcrawler.planner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jersey.repackaged.com.google.common.collect.Lists;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.gfw.vn.producer.producer.MessageInfo;
import viettel.gfw.vn.producer.producer.ProducerORMWeb;
import viettel.gfw.vn.social.FaceBookProto;
import viettel.gfw.vn.social.FaceBookProtoTrans;
import static viettel.nfw.social.facebook.pgcrawler.planner.Planner.crawledQueue;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.pgcrawler.entities.StoredPostInfo;
import viettel.nfw.social.facebook.pgcrawler.entities.StoredProfileInfo;
import viettel.nfw.social.facebook.pgcrawler.planner.rabitmq.ThreadSendRabitMQ;
import viettel.nfw.social.facebook.pgcrawler.scoring.ProfileScoringCalulator;
import viettel.nfw.social.facebook.updatenews.graph.entities.ProfileType;
import viettel.nfw.social.model.facebook.Comment;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.model.facebook.Like;
import viettel.nfw.social.model.facebook.Post;
import viettel.nfw.social.model.facebook.Profile;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.Funcs;
import vn.itim.detector.InputType;
import vn.itim.detector.Language;
import vn.itim.detector.LanguageDetector;

/**
 *
 * @author Duong
 */
public class PlannerReceiveFromCrawler implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(PlannerReceiveFromCrawler.class);
	private static final ProducerORMWeb FB_ORM_PRODUCER = new ProducerORMWeb("orm_web");
	private static final ThreadSendRabitMQ SEND_TO_RABBIT_MQ = new ThreadSendRabitMQ();
	private final ProfileDatabaseHandler db;
	private final LanguageDetector languageDetector;

	public PlannerReceiveFromCrawler(ProfileDatabaseHandler db, LanguageDetector languageDetector) {
		this.db = db;
		this.languageDetector = languageDetector;
	}

	@Override
	public void run() {
		try {
			SEND_TO_RABBIT_MQ.startWorking();
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
		// re calculate frequence
		while (!Planner.isTerminating.get() || crawledQueue.size() > 0) {

			Pair<String, FacebookObject> crawledData = crawledQueue.poll();
			if (crawledData == null) {
				Funcs.sleep(20);
			} else {
				String profileID = crawledData.first;
				FacebookObject fbObj = crawledData.second;
				try {
					if (StringUtils.isNotEmpty(profileID)) {
						// remove from inCrawlingSet
//						Planner.profileInCrawlingMap.remove(profileID);
					} else {
						LOG.warn("AKWARD_PROFILE_ID_NULL");
						continue;
					}

					if (fbObj == null) {
						LOG.info("{} receive_object_null", profileID);
						continue;
					}

					// send to big data
					splitAndSendToBigData(fbObj);

					// get stored profile info in database
					StoredProfileInfo storedProfileInfo = null;
					try {
						storedProfileInfo = db.getStoredProfileInfo(profileID);
					} catch (IOException ex) {
						LOG.error("Error while getStoredProfileInfo", ex);
					}
					if (storedProfileInfo == null) {
						storedProfileInfo = new StoredProfileInfo(profileID);
					}
					Profile profile = fbObj.getInfo();
					if (profile != null) {
						storedProfileInfo.setUsername(profile.getUsername() == null ? "" : profile.getUsername());
						String fullname = profile.getFullname() == null ? "" : profile.getFullname();
						storedProfileInfo.setFullname(fullname);
						storedProfileInfo.setUrl(profile.getUrl() == null ? "https://www.facebook.com/profile.php?id=" + profileID : profile.getUrl());
						String description = profile.getBio() == null ? "" : profile.getBio();
						storedProfileInfo.setDescription(description);
						// detect language profile base on profile description and fullname
						Language descriptionLanguage = Language.UNKNOWN;
						if (StringUtils.isNotEmpty(description)) {
							String removedLinksInDescription = description.replaceAll("(http|https)://[^\\s]+", "");
							descriptionLanguage = languageDetector.detect(removedLinksInDescription, null, InputType.PLAIN);
						}
						if (!descriptionLanguage.equals(Language.VIETNAMESE)) {
							if (StringUtils.isNotEmpty(fullname)) {
								String removedLinksInFullname = fullname.replaceAll("(http|https)://[^\\s]+", "");
								descriptionLanguage = languageDetector.detect(removedLinksInFullname, null, InputType.PLAIN);
							}
						}
						storedProfileInfo.setLanguage(descriptionLanguage);
						if (storedProfileInfo.getProfileType() == null) {
							LOG.warn("profile {} has not profile_type", profileID);
							storedProfileInfo.setProfileType(ProfileType.UNKNOWN);
						}
						if (storedProfileInfo.getProfileType().equals(ProfileType.PAGE_REAL)
							|| storedProfileInfo.getProfileType().equals(ProfileType.USER)) {
							storedProfileInfo.setLikesOrMembers(profile.getTotalFriends());
						} else if (storedProfileInfo.getProfileType().equals(ProfileType.GROUP_PUBLIC)) {
							storedProfileInfo.setLikesOrMembers(profile.getTotalFriends() < 0 ? 0 : profile.getTotalFriends());
						}
						if (storedProfileInfo.getFirstCrawlingTime() == -1) {
							storedProfileInfo.setFirstCrawlingTime(System.currentTimeMillis());
						}
						storedProfileInfo.setLastCrawlingTimelineTime(System.currentTimeMillis());
						storedProfileInfo.setLastSuccessCrawlingTimelineTime(System.currentTimeMillis());
					}
					// get crawled post ids in database
					String[] crawledPostIds = storedProfileInfo.getCrawledPostIds();
					Set<String> postIds = new HashSet<>();
					Map<String, StoredPostInfo> postId2StoredPostInfo = new HashMap<>();
					if (crawledPostIds != null) {
						for (String crawledPostId : crawledPostIds) {
							postIds.add(crawledPostId);
							try {
								StoredPostInfo storedPostInfo = db.getStoredPostInfo(crawledPostId);
								if (storedPostInfo != null) {
									postId2StoredPostInfo.put(crawledPostId, storedPostInfo);
								}
							} catch (IOException ex) {
								LOG.error("Error while getStoredPostInfo of post " + crawledPostId, ex);
							}
						}
					}
					List<Post> posts = fbObj.getPosts();
					if (posts != null) {
						for (Post post : posts) {
							String postId = post.getId();
							if (StringUtils.isNotEmpty(postId)) {
								postIds.add(postId);

								StoredPostInfo storedPostInfo = new StoredPostInfo();
								storedPostInfo.setPostId(postId);
								storedPostInfo.setActorProfileId(post.getActorProfileId() == null ? "" : post.getActorProfileId());
								storedPostInfo.setProfileTimelineId(post.getWallProfileId() == null ? "" : post.getWallProfileId());
								storedPostInfo.setUrl(post.getUrl() == null ? "https://www.facebook.com/" + postId : post.getUrl());
								Language contentLanguage = Language.UNKNOWN;
								String postContent = post.getContent();
								if (StringUtils.isNotEmpty(postContent)) {
									String removedLinksInPostContent = postContent.replaceAll("(http|https)://[^\\s]+", "");
									contentLanguage = languageDetector.detect(removedLinksInPostContent, null, InputType.PLAIN);
								}
								storedPostInfo.setContentLanguage(contentLanguage);
								storedPostInfo.setPublishedTime(post.getPostTime() == null ? StoredPostInfo.UNSET_TIME : post.getPostTime().getTime());
								storedPostInfo.setCrawledTime(post.getCreateTime() == null ? StoredPostInfo.UNSET_TIME : post.getCreateTime().getTime());
								storedPostInfo.setReCrawledTime(post.getUpdateTime() == null ? StoredPostInfo.UNSET_TIME : post.getUpdateTime().getTime());
								storedPostInfo.setComments(post.getCommentsCount());
								storedPostInfo.setLikes(post.getLikesCount());
								storedPostInfo.setShares(-1);
								storedPostInfo.setInsideLinksSize(post.getInsideUrl() == null ? 0 : 1);
								storedPostInfo.setOutsideLinksSize(post.getOutsideUrl() == null ? 0 : 1);
								try {
									db.saveStoredPostInfo(storedPostInfo);
								} catch (IOException ex) {
									LOG.error("Error while saveStoredPostInfo of post " + postId, ex);
								}
								postId2StoredPostInfo.put(postId, storedPostInfo);
							}
						}
					}
					if (postIds.size() > 0) {
						storedProfileInfo.setCrawledPostIds(new ArrayList<>(postIds).toArray(new String[]{}));
					} else {
						storedProfileInfo.setCrawledPostIds(null);
					}
					boolean isOrm = db.containsInOrmList(profileID);
					boolean isSpecial = db.containsInSpecialList(profileID);
					double freq = ProfileScoringCalulator.guessScheduleTime(storedProfileInfo, postId2StoredPostInfo, isOrm, isSpecial);
					storedProfileInfo.setPostFrequency(Math.round(freq));
					try {
						db.saveProfileFreq(profileID, Math.round(freq));
					} catch (IOException ex) {
						LOG.error("Error while saveProfileFreq of profile " + profileID, ex);
					}
					try {
						db.saveStoredProfileInfo(storedProfileInfo);
					} catch (IOException ex) {
						LOG.error("Error while saveStoredProfileInfo of profile " + profileID, ex);
					}
				} catch (Exception e) {
					LOG.error("Error while receiving profile " + profileID, e);
				}
			}

		}
	}

	private void splitAndSendToBigData(FacebookObject fbObj) {
		if (fbObj.getInfo() == null) {
			LOG.warn("PROFILE_INFO_NULL {}", JSON.encode(fbObj));
			return;
		}

		String profileId = fbObj.getInfo().getId();
		String profileUrl = fbObj.getInfo().getUrl();
		if (StringUtils.isEmpty(profileId)) {
			LOG.warn("PROFILE_ID_EMPTY {}", JSON.encode(fbObj));
			return;
		}
		if (StringUtils.isNotEmpty(profileId) && StringUtils.isEmpty(profileUrl)) {
			LOG.warn("PROFILE_URL_EMPTY {}", JSON.encode(fbObj));
			fbObj.getInfo().setUrl("https://www.facebook.com/" + profileId);
			try {
				if (fbObj.getInfo().getType().equalsIgnoreCase("page")) {
					fbObj.getInfo().setUrl("https://www.facebook.com/profile.php?id=" + profileId);
				} else if (fbObj.getInfo().getType().equalsIgnoreCase("group")) {
					fbObj.getInfo().setUrl("https://www.facebook.com/groups/" + profileId);
				}
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
			}
		}

		// split and send to kafka
		List<Post> posts = fbObj.getPosts();
		List<Comment> comments = fbObj.getComments();
		List<Like> likes = fbObj.getLikes();

		if ((posts.size() + comments.size() + likes.size()) > (25 + 25 + 10)) {
			try {
				int size = 5;
				List<List<Post>> partitionPosts = null;
				if (!posts.isEmpty()) {
					partitionPosts = Lists.partition(posts, size);
				}
				List<List<Comment>> partitionComments = null;
				if (!comments.isEmpty()) {
					partitionComments = Lists.partition(comments, size);
				}
				List<List<Like>> partitionLikes = null;
				if (!likes.isEmpty()) {
					partitionLikes = Lists.partition(likes, 1);
				}

				List<Integer> partitionSizes = new ArrayList<>();
				if (partitionPosts != null) {
					partitionSizes.add(partitionPosts.size());
				}
				if (partitionComments != null) {
					partitionSizes.add(partitionComments.size());
				}
				if (partitionLikes != null) {
					partitionSizes.add(partitionLikes.size());
				}

				int max = 0;
				for (Integer partitionSize : partitionSizes) {
					if (partitionSize > max) {
						max = partitionSize;
					}
				}

				if (max != 0) {
					for (int i = 0; i < max; i++) {
						FacebookObject smallfbObj = new FacebookObject();
						if (fbObj.getInfo() != null) {
							smallfbObj.setInfo(fbObj.getInfo());
						}

						if (partitionPosts != null) {
							if (i < partitionPosts.size()) {
								smallfbObj.setPosts(partitionPosts.get(i));
							}
						}

						if (partitionComments != null) {
							if (i < partitionComments.size()) {
								smallfbObj.setComments(partitionComments.get(i));
							}
						}

						if (partitionLikes != null) {
							if (i < partitionLikes.size()) {
								smallfbObj.setLikes(partitionLikes.get(i));
							}
						}

						// send small facebook object
						LOG.debug("SMALL_FBOBJ {}", JSON.encode(smallfbObj));
						offer(smallfbObj);
					}
				}
			} catch (Exception ex) {
				LOG.error("Error while splitting facebook object of profile " + profileId, ex);
				LOG.debug("BIG_FBOBJ {}", JSON.encode(fbObj));
				offer(fbObj);
			}
		} else {
			LOG.debug("BIG_FBOBJ {}", JSON.encode(fbObj));
			offer(fbObj);
		}
	}

	private void offer(FacebookObject fbobj) {
		// send to kafka
		try {
			MessageInfo message = new MessageInfo();
			FaceBookProto.FBSocialObject fbSocialObject = FaceBookProtoTrans.facebookObjectTo(fbobj);
			message.setDataSocial(fbSocialObject, MessageInfo.MESSAGE_TYPE_SOCIAL_FACEOOK);
			FB_ORM_PRODUCER.sendMessageORMWeb(message);
		} catch (Exception e) {
			LOG.error("Error while sending to kafka", e);
		}
		// send to rabit mq
		try {
			SEND_TO_RABBIT_MQ.addQueueRabitMq(fbobj);
		} catch (Exception e) {
			LOG.error("Error while sending to rabitmq", e);
		}
	}
}
