package viettel.nfw.social.facebook.updatenews;

import com.viettel.naviebayes.ClassifierResult;
import com.viettel.naviebayes.NaiveBayes;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import jersey.repackaged.com.google.common.collect.Lists;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.gfw.vn.producer.producer.MessageInfo;
import viettel.gfw.vn.producer.producer.ProducerORMWeb;
import viettel.gfw.vn.social.FaceBookProto;
import viettel.gfw.vn.social.FaceBookProtoTrans;
import viettel.nfw.social.controller.SensitiveProfile;
import viettel.nfw.social.model.facebook.Comment;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.model.facebook.Like;
import viettel.nfw.social.model.facebook.Post;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class SendObjectToBigDataImpl implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(SendObjectToBigDataImpl.class);
	public static final int MAX_CAPACITY = 1000000;
	private static final ProducerORMWeb producer = new ProducerORMWeb("orm_web");
	public static BlockingQueue<FacebookObject> facebookObjectQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);

	@Override
	public void run() {
		Thread.currentThread().setName("SendFbObjectToBGImpl");

		Funcs.sleep(2000);

		NaiveBayes naiveBayes = null;
		try {
			naiveBayes = NaiveBayes.getInstance();
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}

		while (true) {
			try {
				FacebookObject fbObj = facebookObjectQueue.poll();
				if (fbObj != null) {

					if (fbObj.getInfo() == null) {
						LOG.info("PROFILE_INFO_NULL {}", JSON.encode(fbObj));
						continue;
					}

					String profileId = fbObj.getInfo().getId();
					String profileUrl = fbObj.getInfo().getUrl();
					if (StringUtils.isEmpty(profileId)) {
						LOG.info("PROFILE_ID_EMPTY {}", JSON.encode(fbObj));
						continue;
					}
					if (StringUtils.isNotEmpty(profileId) && StringUtils.isEmpty(profileUrl)) {
						LOG.info("PROFILE_URL_EMPTY {}", JSON.encode(fbObj));
						fbObj.getInfo().setUrl("https://www.facebook.com/profile.php?id=" + profileId);
					}

					// scoring and detect sensitive
					try {
						if (naiveBayes == null) {
							try {
								naiveBayes = NaiveBayes.getInstance();
							} catch (Exception e) {
								naiveBayes = null;
							}
						}
						if (naiveBayes != null) {
							detect(fbObj, naiveBayes);
						}
					} catch (Exception ex) {
						LOG.error(ex.getMessage(), ex);
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
									LOG.info("SMALL_FBOBJ {}", JSON.encode(smallfbObj));
									offer(smallfbObj);
								}
							}
						} catch (Exception e) {
							LOG.error(e.getMessage(), e);
							LOG.info("BIG_FBOBJ {}", JSON.encode(fbObj));
							offer(fbObj);
						}
					} else {
						LOG.info("BIG_FBOBJ {}", JSON.encode(fbObj));
						offer(fbObj);
					}
				} else {
					Funcs.sleep(Funcs.randInt(10, 20));
				}
			} catch (Exception ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
	}

	private void offer(FacebookObject fbobj) {
		try {
			MessageInfo message = new MessageInfo();
			FaceBookProto.FBSocialObject fbSocialObject = FaceBookProtoTrans.facebookObjectTo(fbobj);
			message.setDataSocial(fbSocialObject, MessageInfo.MESSAGE_TYPE_SOCIAL_FACEOOK);
			producer.sendMessageORMWeb(message);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	private void detect(FacebookObject fbobj, NaiveBayes naiveBayes) {
		double score = 0.0;
		String url = "";
		try {
			url = fbobj.getInfo().getUrl();
			if (StringUtils.isEmpty(url)
					&& !StringUtils.contains(url, ".facebook.com/")) {
				String id = fbobj.getInfo().getId();
				String profileType = fbobj.getInfo().getType();
				if (StringUtils.isNotEmpty(id) && StringUtils.isNotEmpty(profileType)) {
					if (profileType.equalsIgnoreCase("group")) {
						url = "https://m.facebook.com/groups/" + id;
					} else if (profileType.equalsIgnoreCase("page")) {
						url = "https://m.facebook.com/profile.php?id=" + id;
					} else {
						return;
					}
				} else {
					return;
				}
			}
			LOG.info("Start scoring url {}", url);
			score = calculateObjectScore(fbobj, naiveBayes);

			List<String> sharedUrls = new ArrayList<>();
			for (Post post : fbobj.getPosts()) {
				try {
					String outsideUrl = post.getOutsideUrl();
					if (StringUtils.isNotEmpty(outsideUrl)) {
						sharedUrls.add(outsideUrl);
					}
				} catch (Exception e) {
				}
			}
			double linkScore = 0.0;
			for (String sharedUrl : sharedUrls) {
				URI uri;
				try {
					uri = new URI(sharedUrl);
				} catch (Exception ex) {
					continue;
				}
				String host = uri.getHost();
				if (RunUpdateNews.allBlackSites.contains(host)) {
					linkScore++;
				}
			}
			LOG.info("Score - {} - post {} - links {}", new Object[]{url, score, linkScore});
			if (linkScore > 2) {
				score += linkScore;
			}
			if (score > 0) {
				if (StringUtils.isNotEmpty(url)) {
					String webUrl = StringUtils.replaceEach(url,
							new String[]{"//m.facebook.com/", "//www.facebook.com/", "//vi-vn.facebook.com"},
							new String[]{"//facebook.com/", "//facebook.com/", "//facebook.com/"});
					SensitiveProfile sp = new SensitiveProfile(webUrl, score);
					RunUpdateNews.sensitiveReporter.offer(sp);
					LOG.info("Sent url {} with score {}", webUrl, score);
				}
			}
		} catch (Exception ex) {
			LOG.error("Error in scoring facebook object {}", url);
			LOG.error(ex.getMessage(), ex);
		}
	}

	private double calculateObjectScore(FacebookObject fbObject, NaiveBayes naiveBayes) {
		StringBuilder fullContent = new StringBuilder();
		List<Post> posts = fbObject.getPosts();
		if (posts != null && !posts.isEmpty()) {
			for (Post post : posts) {
				String postContent = post.getContent();
				if (StringUtils.isNotEmpty(postContent)) {
					fullContent.append(postContent).append(" . ");
				}
			}
			try {
				for (Comment comment : fbObject.getComments()) {
					String commentContent = comment.getContent();
					if (StringUtils.isNotEmpty(commentContent)) {
						fullContent.append(commentContent).append(" . ");
					}
				}
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
			}

			if (StringUtils.isNotEmpty(fullContent.toString())) {
				ClassifierResult classifierByFocus = naiveBayes.getClassifierForcus(fullContent.toString());
				if (classifierByFocus.score >= 1.0) {
					ClassifierResult classifierByContentWithArticle = naiveBayes.getClassifierByContent(fullContent.toString());
					if (classifierByContentWithArticle.score > 0) {
						LOG.info("Score focus {} - content {} - Full content: {}",
								new Object[]{classifierByFocus.score, classifierByContentWithArticle.score, fullContent.toString()});
						return classifierByContentWithArticle.score;
					}
				}
			}
		}
		double score = 0.0;
		return score;
	}

}
