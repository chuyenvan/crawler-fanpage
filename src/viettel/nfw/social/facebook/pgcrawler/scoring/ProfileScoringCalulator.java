package viettel.nfw.social.facebook.pgcrawler.scoring;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.entities.StoredPostInfo;
import viettel.nfw.social.facebook.pgcrawler.entities.StoredProfileInfo;
import viettel.nfw.social.facebook.updatenews.graph.entities.ProfileType;
import vn.itim.detector.Language;

/**
 *
 * @author Duong
 */
public class ProfileScoringCalulator {

	private static final Logger LOG = LoggerFactory.getLogger(ProfileScoringCalulator.class);

	public static final double MIN_TIME = 50 * 60 * 1000.0; // 55 minutes
	public static final double MAX_TIME = 12 * 60 * 60 * 1000.0; // 12 hours

	private static final double MIN_SCORE = 40.0;
	private static final double MAX_SCORE = 105.0;

	public static final double AVERAGE_TIME = 75 * 60 * 1000.0; // 75 minutes

	private static final double BASE_SCORES = 40.0;
	private static final double MEMBERS_COEFF = 1.0;
	private static final double CRAWLED_POST_COEFF = 1.0;
	private static final double LANGUAGE_COEFF = 1.0;
	private static final double ORM_RANK_COEFF = 1.0;

	private static final long SIX_MONTH = 6 * 30 * 24 * 60 * 60 * 1000L;
	private static final long ONE_MONTH = 1 * 30 * 24 * 60 * 60 * 1000L;
	private static final long ONE_HOUR = 1 * 60 * 60 * 1000L;

	public static double guessScheduleTime(StoredProfileInfo storedProfileInfo,
		Map<String, StoredPostInfo> postId2StoredPostInfo, boolean isOrm, boolean isSpecial) {

		String profileId = storedProfileInfo.getId();

		if (isSpecial) {
			LOG.info("SCORE of {} is special - freq time is {}", new Object[]{profileId, ONE_HOUR});
			return ONE_HOUR * 1.0;
		}

		// scoring base members
		long likesOrMembers = storedProfileInfo.getLikesOrMembers();
		double membersScore = 0.0;
		if (likesOrMembers <= 0) {
			LOG.info("Profile {} has 0 likes or members", profileId);
			if (storedProfileInfo.getProfileType().equals(ProfileType.GROUP_PUBLIC)) {
				membersScore = MEMBERS_COEFF * 2.0;
			}
		} else {
			membersScore = MEMBERS_COEFF * Math.log(likesOrMembers);
		}
		// scoring base posts size
		long crawledPosts = storedProfileInfo.getCrawledPostsSize();
		double crawledPostsScore = 0.0;
		if (crawledPosts <= 0) {
			LOG.info("Profile {} has 0 posts", profileId);
			crawledPostsScore -= 10.0;
		} else {
			crawledPostsScore = CRAWLED_POST_COEFF * Math.log(crawledPosts);
		}
		// scoring base lastest post time and base percent post Vietnamese/Total
		double lastestPostTimeScore = 0.0;
		double vietnamesePostsScore = 0.0;
		if (postId2StoredPostInfo != null) {
			long lastestPostTime = 0L;
			int vietnamesePostCounter = 0;
			int totalPosts = postId2StoredPostInfo.size();
			for (Map.Entry<String, StoredPostInfo> entrySet : postId2StoredPostInfo.entrySet()) {
				StoredPostInfo storedPostInfo = entrySet.getValue();
				long postTime = storedPostInfo.getPublishedTime();
				if (postTime > lastestPostTime) {
					lastestPostTime = postTime;
				}
				Language contentLanguage = storedPostInfo.getContentLanguage();
				if (contentLanguage.equals(Language.VIETNAMESE)) {
					vietnamesePostCounter++;
				}
			}
			// scoring lastest post time
			long diff = System.currentTimeMillis() - lastestPostTime;
			if (diff >= SIX_MONTH) {
				LOG.info("Profile {} has lastest post over 6 months {}", profileId, lastestPostTime);
				lastestPostTimeScore -= 20.0;
			} else if (diff >= ONE_MONTH && diff < SIX_MONTH) {
				LOG.info("Profile {} has lastest post over 1 months {}", profileId, lastestPostTime);
				lastestPostTimeScore -= 10.0;
			} else if (diff >= ONE_HOUR && diff < ONE_MONTH) {
				lastestPostTimeScore += 3.0;
			} else if (diff >= 0 && diff < ONE_HOUR) {
				lastestPostTimeScore += 10.0;
			}

			// scoring vietnamese posts
			double percent = 0.0;
			if (totalPosts != 0) {
				percent = (double) vietnamesePostCounter / totalPosts;
			}
			if (percent >= 0.8) {
				vietnamesePostsScore += 10.0;
			} else if (percent >= 0.5 && percent < 0.8) {
				vietnamesePostsScore += 5.0;
			} else if (percent <= 0.1) {
				vietnamesePostsScore -= 10.0;
			}
		}
		// scoring base language
		Language profileLanguage = storedProfileInfo.getLanguage();
		double ls = 0.0;
		if (profileLanguage.equals(Language.VIETNAMESE)) {
			ls += 10.0;
		} else if (profileLanguage.equals(Language.FOREIGN)) {
			ls -= 20.0;
		} else if (profileLanguage.equals(Language.UNKNOWN)) {
			ls -= 10.0;
		} else {
			ls -= 5.0;
		}
		double profileLanguageScore = LANGUAGE_COEFF * ls;
		// scoring base orm priority
		double ormScore = 0.0;
		if (isOrm) {
			// not support for now
		}
		// sum scores
		double totalScore = BASE_SCORES + membersScore + crawledPostsScore + lastestPostTimeScore + vietnamesePostsScore + profileLanguageScore + ormScore;
		double freqTime = AVERAGE_TIME;
		try {
			freqTime = calTime(totalScore);
			if (freqTime >= 0.0 && freqTime < MIN_TIME) {
				freqTime = AVERAGE_TIME;
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		LOG.info("SCORE of {} is {} - freq time is {}", new Object[]{profileId, totalScore, (long) freqTime});
		return freqTime;
	}

	private static double calTime(double score) {
		// check if the score is in range
		if (score < MIN_SCORE) {
			return MAX_TIME;
		}
		if (score > MAX_SCORE) {
			return MIN_TIME;
		}

		double duration = MAX_TIME - MIN_TIME;
		if (duration <= 0) {
			return AVERAGE_TIME; // invalid
		}

		double beta = (MIN_TIME * MAX_SCORE - MAX_TIME * MIN_SCORE) / duration;
		if (beta + score == 0) {
			return AVERAGE_TIME; // invalid
		}

		double alpha = MAX_TIME * MIN_TIME * (MAX_SCORE - MIN_SCORE) / duration;

		return (alpha / (beta + score));
	}

	public static void main(String[] args) {
		double score = 85.0;
		System.out.println((long) calTime(score));
		double member = Math.log(10000);
		System.out.println(member);
	}
}
