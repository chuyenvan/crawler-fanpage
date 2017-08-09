package viettel.nfw.social.facebook.pgcrawler.planner;

import java.util.ArrayList;
import java.util.Collection;
import org.ocpsoft.prettytime.shade.org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileSortedSet;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author Duong
 */
public class PlannerGetProfileIdFromRedis implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(PlannerGetProfileIdFromRedis.class);
	private static final int MAX_WAIT_IN_TO_CRAWL_QUEUE = 2000;
	private static final long PROFILE_IN_CRAWLING_TOO_LONG = 1 * 60 * 60 * 1000L; // 1 hour
	private final ProfileSortedSet pageGroupSortedSet;

	public PlannerGetProfileIdFromRedis(ProfileSortedSet pageGroupSortedSet) {
		this.pageGroupSortedSet = pageGroupSortedSet;
	}

	@Override
	public void run() {
		while (!Planner.isTerminating.get()) {
			try {
				if (Planner.toCrawlQueue.size() > MAX_WAIT_IN_TO_CRAWL_QUEUE) {
					Funcs.sleep(Funcs.randInt(60 * 1000, 5 * 60 * 1000));
					continue;
				}
				Collection<String> foundKeys = new ArrayList<>();
				for (Collection<String> pageGroup = pageGroupSortedSet.poll(1);
					pageGroup != null && !pageGroup.isEmpty();
					pageGroup = pageGroupSortedSet.poll(1)) {
					foundKeys.addAll(pageGroup);
				}
				if (foundKeys.size() > 0) {
					LOG.info("Found keys {}", foundKeys.size());
				}
				for (String profileId : foundKeys) {
					if (StringUtils.isEmpty(profileId)) {
						continue;
					}
					// check somethings then send to crawl if need
					// if this key is in crawling
					if (Planner.profileInCrawlingMap.contains(profileId)) {
						Long startTime = Planner.profileInCrawlingMap.get(profileId);
						if (startTime != null && startTime > 0) {
							if (System.currentTimeMillis() - startTime > PROFILE_IN_CRAWLING_TOO_LONG) {
								// stay in map too long
								LOG.warn("Profile {} stay in crawling map too long", profileId);
								Planner.profileInCrawlingMap.remove(profileId);
							} else {
								Funcs.sleep(1000);
								continue;
							}
						}
					}
					try {
						LOG.info("Polling {} to crawl", profileId);
						Planner.toCrawlQueue.put(profileId);
						Planner.profileInCrawlingMap.put(profileId, System.currentTimeMillis());
					} catch (InterruptedException ex) {
						LOG.error(ex.getMessage(), ex);
					}
				}
			} catch (Exception e) {
				LOG.error("Error while do PlannerGetProfileIdFromRedis", e);
			}
			Funcs.sleep(Funcs.randInt(200, 500));
		}
	}
}
