package viettel.nfw.social.facebook.pgcrawler.crawler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.pgcrawler.entities.ProfileStat;
import viettel.nfw.social.facebook.pgcrawler.entities.StoredProfileInfo;
import viettel.nfw.social.facebook.pgcrawler.planner.Planner;
import viettel.nfw.social.facebook.updatenews.graph.entities.ProfileType;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.model.facebook.Profile;
import viettel.nfw.social.utils.Pair;

/**
 *
 * @author duongth5
 */
public class Downloader implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(Downloader.class);

	private static final long RECRAWL_PROFILE_INFO_TIME = 5 * 24 * 60 * 60 * 1000L; // 5 days

	private final String profileId;
	private final ProfileDatabaseHandler db;
	private final CrawlersPool crawlersPool;
	private final boolean crawlWithAccount;

	public Downloader(String profileId, ProfileDatabaseHandler db, CrawlersPool crawlersPool, boolean crawlWithAccount) {
		this.profileId = profileId;
		this.db = db;
		this.crawlersPool = crawlersPool;
		this.crawlWithAccount = crawlWithAccount;
	}

	@Override
	public void run() {

		// get detail info of this profile ID
		StoredProfileInfo storedProfileInfo;
		try {
			storedProfileInfo = db.getStoredProfileInfo(profileId);
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
			return;
		}

		if (storedProfileInfo == null) {
			return;
		}

		String profileUrl = storedProfileInfo.getUrl();
		ProfileType profileType = storedProfileInfo.getProfileType();
		String[] crawledPostIdsArray = storedProfileInfo.getCrawledPostIds();
		List<String> crawledPostIds = new ArrayList<>();
		if (crawledPostIdsArray != null) {
			crawledPostIds.addAll(Arrays.asList(crawledPostIdsArray));
		}
		long lastCrawlingInfoTime = storedProfileInfo.getLastCrawlingInfoTime();

		// if type if public page and public group, using graph to crawl
		if (profileType.equals(ProfileType.PAGE_REAL)
			|| profileType.equals(ProfileType.GROUP_PUBLIC)) {
			if (crawlersPool.getGraphPoolSize() > 0) {
				NewGraphCrawler newGraphCrawler;
				try {
					newGraphCrawler = crawlersPool.pollGraph();
					LOG.info("Borrow_App_{}", newGraphCrawler.getAppID());
				} catch (InterruptedException ex) {
					LOG.error(ex.getMessage(), ex);
					return;
				}
				// check Token
				try {
					boolean isCheckAndRefreshOK = newGraphCrawler.checkToken(false);
					if (isCheckAndRefreshOK) {
						LOG.debug("App_{} still OK!", newGraphCrawler.getAppID());
					} else {
						LOG.warn("App_{}_NOT_OK: Remove_App", newGraphCrawler.getAppID());
						CrawlersPool.problemAppIds.add(newGraphCrawler.getAppID());
						CrawlersPool.activeAppIds.remove(newGraphCrawler.getAppID());
						return;
					}
				} catch (Exception e) {
					LOG.error("Error while checking token! Remove_App_" + newGraphCrawler.getAppID(), e);
					return;
				}
				ProfileStat profileStat = new ProfileStat(profileId);
				try {
					boolean isRecrawlProfileInfo = false;
					if (lastCrawlingInfoTime != -1) {
						if ((System.currentTimeMillis() - lastCrawlingInfoTime) > RECRAWL_PROFILE_INFO_TIME) {
							isRecrawlProfileInfo = true;
						}
					} else {
						isRecrawlProfileInfo = true;
					}
					FacebookObject fbObj = newGraphCrawler.doIt(profileId, profileType, crawledPostIds, isRecrawlProfileInfo);
					if (fbObj != null) {
						if (!isRecrawlProfileInfo) {
							// if is not recrawl profile info, get the current saved info
							Profile profile = db.getProfileInfo2(profileId);
							fbObj.setInfo(profile);
						} else {
							// if is recrawl profile info, update lastCrawlingInfoTime to db, and save the new one
							Profile profile = fbObj.getInfo();
							try {
								db.updateProfileInfo(profileId, profile);
							} catch (IOException e) {
								LOG.error(e.getMessage(), e);
							}
						}
						// send back to Planner
						Planner.crawledQueue.add(new Pair<>(profileId, fbObj));
						int countDiscoverdPosts = fbObj.getPosts() == null ? 0 : fbObj.getPosts().size();
						int countDiscoverdComments = fbObj.getComments() == null ? 0 : fbObj.getComments().size();
						CrawlerManager.discoveredPostsToday.addAndGet(countDiscoverdPosts);
						CrawlerManager.discoveredCommentsToday.addAndGet(countDiscoverdComments);
						profileStat.discoveredPostsToday = countDiscoverdPosts;
						profileStat.discoveredCommentsToday = countDiscoverdComments;
					} else {
						CrawlerManager.failProfilesToday.incrementAndGet();
						profileStat.visitedFailToday = 1;
					}
				} catch (Exception ex) {
					LOG.error(ex.getMessage(), ex);
					CrawlerManager.failProfilesToday.incrementAndGet();
					profileStat.visitedFailToday = 1;
				} finally {
					CrawlerManager.visitedProfilesToday.incrementAndGet();
					profileStat.visitedToday = 1;
					profileStat.lastVisitedTimeToday = System.currentTimeMillis();
					try {
						crawlersPool.addGraph(newGraphCrawler);
						LOG.info("Return_App_{}", newGraphCrawler.getAppID());
					} catch (InterruptedException ex) {
						LOG.error(ex.getMessage(), ex);
					}
					// remove from inCrawlingSet
					Planner.profileInCrawlingMap.remove(profileId);
				}
				try {
					CrawlerManager.profileStatsQueue.put(profileStat);
				} catch (InterruptedException ex) {
					LOG.error(ex.getMessage(), ex);
				}
			} else {
				LOG.warn("Graph crawler pool is empty!");
			}
		} else {
			// not supported yet!
			LOG.warn("Not support this profile {} {} yet", profileType.getShortName(), profileId);
		}
	}
}
