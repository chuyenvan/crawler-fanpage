package viettel.nfw.social.facebook.pgcrawler.crawler;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.pgcrawler.entities.ProfileStat;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class AutoUpdateStats implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(AutoUpdateStats.class);

	private static final SimpleDateFormat SDF_YMD = new SimpleDateFormat("yyyyMMdd");

	private final ProfileDatabaseHandler db;

	public AutoUpdateStats(ProfileDatabaseHandler db) {
		this.db = db;
	}

	@Override
	public void run() {
		long currentTime = System.currentTimeMillis();
		while (!CrawlerManager.isTerminating.get()) {
			try {
				ProfileStat stat = CrawlerManager.profileStatsQueue.poll();
				if (stat != null) {
					try {
						String redisHashKey = buildRedisHashKeyForStat(System.currentTimeMillis(), stat.id);
						if (StringUtils.isNotEmpty(redisHashKey)) {
							// get from redis
							ProfileStat previousStat = db.getProfileStat(redisHashKey, stat.id);
							// calculate
							ProfileStat lastestUpdateStat = calculate(previousStat, stat);
							// save stat
							db.saveProfileStat(redisHashKey, lastestUpdateStat);
						}
					} catch (Exception e) {
						LOG.error(e.getMessage(), e);
					}
				} else {
					Funcs.sleep(5 * 1000L);
				}
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
			}

			Funcs.sleep(10);

			if ((System.currentTimeMillis() - currentTime) >= (30 * 1000L)) {
				try {
					// auto update crawler stats
					Map<String, String> downloaderStats = new HashMap<>();
					downloaderStats.put(ProfileDatabaseHandler.KEY_STAT_VISITED_PROFILE_TODAY, String.valueOf(CrawlerManager.visitedProfilesToday.get()));
					downloaderStats.put(ProfileDatabaseHandler.KEY_STAT_PREVIOUS_VISITED_PROFILE_TODAY, String.valueOf(CrawlerManager.previousVisitedProfilesToday.get()));
					downloaderStats.put(ProfileDatabaseHandler.KEY_STAT_VISITED_PROFILE_FAIL_TODAY, String.valueOf(CrawlerManager.failProfilesToday.get()));
					downloaderStats.put(ProfileDatabaseHandler.KEY_STAT_DISCOVERED_POST_TODAY, String.valueOf(CrawlerManager.discoveredPostsToday.get()));
					downloaderStats.put(ProfileDatabaseHandler.KEY_STAT_DISCOVERED_COMMENT_TODAY, String.valueOf(CrawlerManager.discoveredCommentsToday.get()));
					db.saveDownloaderStat(downloaderStats);
				} catch (Exception e) {
					LOG.error(e.getMessage(), e);
				} finally {
					currentTime = System.currentTimeMillis();
				}
			}
		}
	}

	private static ProfileStat calculate(ProfileStat previousStat, ProfileStat currentStat) {
		ProfileStat stat = new ProfileStat(previousStat);
		if (StringUtils.equalsIgnoreCase(stat.id, currentStat.id)) {
			stat.visitedToday += currentStat.visitedToday;
			stat.visitedFailToday += currentStat.visitedFailToday;
			stat.discoveredPostsToday += currentStat.discoveredPostsToday;
			stat.discoveredCommentsToday += currentStat.discoveredCommentsToday;
			stat.lastDiffVisitedTimeToday = currentStat.lastVisitedTimeToday - previousStat.lastVisitedTimeToday;
			stat.listDiffVisitedTimeToday.add(stat.lastDiffVisitedTimeToday);
			if (currentStat.lastVisitedTimeToday != -1) {
				stat.lastVisitedTimeToday = currentStat.lastVisitedTimeToday;
			}
		} else {
			LOG.warn("currentStat has different ID with previousStat");
		}
		return stat;
	}

	private static String buildRedisHashKeyForStat(long currentTimeInMillis, String profileId) {
		String dateInString = SDF_YMD.format(new Date(currentTimeInMillis));
		if (StringUtils.isNotEmpty(dateInString) && StringUtils.isNotEmpty(profileId)) {
			StringBuilder sb = new StringBuilder();
			sb.append("stat:").append(dateInString).append(":").append(profileId);
			return sb.toString();
		} else {
			return null;
		}
	}
}
