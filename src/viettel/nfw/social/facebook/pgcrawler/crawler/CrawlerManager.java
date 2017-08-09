package viettel.nfw.social.facebook.pgcrawler.crawler;

import java.net.Proxy;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.planner.Planner;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.pgcrawler.entities.ProfileStat;
import viettel.nfw.social.facebook.pgcrawler.monitoring.MonitoringStatistics;
import viettel.nfw.social.utils.EngineConfiguration;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author Duong
 */
public class CrawlerManager {

	private static final Logger LOG = LoggerFactory.getLogger(CrawlerManager.class);

	private static final long TASK_TIMEOUT = 10 * 60 * 1000L;
	private static final long MAX_INDEX = 1000L;
	private static final int MAX_PENDING_FOR_DOWNLOAD_TASKS = EngineConfiguration.get().getInt("facebookdeep.downloader.maxpendingtasks", 70);
	private static final int NUMBER_THREAD = EngineConfiguration.get().getInt("facebookdeep.downloader.size", 60);
	private static final String POOL_PREFIX_NAME = "Downloader";
	private static final int MAX_CAPACITY = 3000;
	private static final Proxy PROXY = null;

	public static AtomicBoolean isTerminating = new AtomicBoolean(false);
	public static AtomicLong visitedProfilesToday = new AtomicLong(0);
	public static AtomicLong previousVisitedProfilesToday = new AtomicLong(0);
	public static AtomicLong discoveredPostsToday = new AtomicLong(0);
	public static AtomicLong discoveredCommentsToday = new AtomicLong(0);
	public static AtomicLong failProfilesToday = new AtomicLong(0);
	public static AtomicInteger totalTaskTimeout = new AtomicInteger(0);
	public static BlockingQueue<ProfileStat> profileStatsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);

	private final boolean crawlWithAccount;
	private final ProfileDatabaseHandler db;

	private static CrawlersPool crawlersPool;

	public CrawlerManager(ProfileDatabaseHandler db, boolean crawlWithAccount) {
		this.db = db;
		this.crawlWithAccount = crawlWithAccount;
	}

	public void run() {
		getPreviousStats();

		crawlersPool = new CrawlersPool(db, PROXY);

		Funcs.sleep(30 * 1000L);

		new Thread(new DownloaderPool(crawlersPool, db, crawlWithAccount), "DownloaderPool").start();

		new Thread(new AutoUpdateStats(db), "AutoUpdateStats").start();
	}

	private static class DownloaderPool implements Runnable {

		private final CrawlersPool crawlersPool;
		private final ProfileDatabaseHandler db;
		private final boolean crawlWithAccount;

		public DownloaderPool(CrawlersPool crawlersPool, ProfileDatabaseHandler db, boolean crawlWithAccount) {
			this.crawlersPool = crawlersPool;
			this.db = db;
			this.crawlWithAccount = crawlWithAccount;
		}

		@Override
		public void run() {
			ExecutorService downloaderPool = Executors.newFixedThreadPool(NUMBER_THREAD, new ThreadFactory() {

				private final AtomicLong threadNumber = new AtomicLong(1);

				@Override
				public Thread newThread(Runnable r) {
					if (threadNumber.get() > MAX_INDEX) {
						threadNumber.set(1);
					}
					String threadName = POOL_PREFIX_NAME + "-" + String.valueOf(threadNumber.getAndIncrement());
					Thread t = new Thread(r, threadName);
					return t;
				}
			});

			Map<Future, Long> taskInWork = new HashMap<>();
			int pendingForSerializationTasks = 0;
			while (!isTerminating.get() || Planner.toCrawlQueue.size() > 0) {

				// check pending tasks: Kiểm tra việc cấp phát và crawler
				int tTaskTimeout = 0;
				for (Iterator<Map.Entry<Future, Long>> it = taskInWork.entrySet().iterator(); it.hasNext();) {
					Map.Entry<Future, Long> mapElement = it.next();
					Future f = mapElement.getKey();
					Long startTime = mapElement.getValue();
					if (f.isDone() || f.isCancelled()) {
						it.remove();
						--pendingForSerializationTasks;
					} else if (System.currentTimeMillis() - startTime > TASK_TIMEOUT) { // check task timeout
						tTaskTimeout++;
					}
				}
				CrawlerManager.totalTaskTimeout.set(tTaskTimeout);

				if (pendingForSerializationTasks < MAX_PENDING_FOR_DOWNLOAD_TASKS) {
					final String profileId = Planner.toCrawlQueue.poll();
					if (StringUtils.isNotEmpty(profileId)) {
						Future ft = downloaderPool.submit(new Downloader(profileId, db, crawlersPool, crawlWithAccount));
						taskInWork.put(ft, System.currentTimeMillis());
						++pendingForSerializationTasks;
						Funcs.sleep(Funcs.randInt(100, 200));
					}
				} else {
					Funcs.sleep(5);
				}
			}

			LOG.info("WAITING_BEFOR_CALLING_SHUTDOWN_TASK_DOWNLOADING ...");
			Funcs.sleep(60 * 1000L); // wait 1 minute
			LOG.info("CALLING_SHUTDOWN_TASK_DOWNLOADING ...");
			downloaderPool.shutdown();
			LOG.info("AWAIT_TERMINATION_TASK_DOWNLOADING ...");
			try {
				downloaderPool.awaitTermination(10, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				LOG.error(e.getMessage(), e);
			}
			Funcs.sleep(60 * 1000L);
		}

	}

	private void getPreviousStats() {
		Map<String, Long> downloaderStats = db.getDownloaderStat();
		visitedProfilesToday.set(
			downloaderStats.containsKey(ProfileDatabaseHandler.KEY_STAT_VISITED_PROFILE_TODAY)
				? downloaderStats.get(ProfileDatabaseHandler.KEY_STAT_VISITED_PROFILE_TODAY) : 0);
		previousVisitedProfilesToday.set(
			downloaderStats.containsKey(ProfileDatabaseHandler.KEY_STAT_PREVIOUS_VISITED_PROFILE_TODAY)
				? downloaderStats.get(ProfileDatabaseHandler.KEY_STAT_PREVIOUS_VISITED_PROFILE_TODAY) : visitedProfilesToday.get()
		);
		failProfilesToday.set(
			downloaderStats.containsKey(ProfileDatabaseHandler.KEY_STAT_VISITED_PROFILE_FAIL_TODAY)
				? downloaderStats.get(ProfileDatabaseHandler.KEY_STAT_VISITED_PROFILE_FAIL_TODAY) : 0);
		discoveredPostsToday.set(
			downloaderStats.containsKey(ProfileDatabaseHandler.KEY_STAT_DISCOVERED_POST_TODAY)
				? downloaderStats.get(ProfileDatabaseHandler.KEY_STAT_DISCOVERED_POST_TODAY) : 0);
		discoveredCommentsToday.set(
			downloaderStats.containsKey(ProfileDatabaseHandler.KEY_STAT_DISCOVERED_COMMENT_TODAY)
				? downloaderStats.get(ProfileDatabaseHandler.KEY_STAT_DISCOVERED_COMMENT_TODAY) : 0);
	}

	public void fillStatistic(MonitoringStatistics stat) {
		stat.setProperty("crawler.visitedProfilesToday", visitedProfilesToday.get());
		stat.setProperty("crawler.previousVisitedProfilesToday", previousVisitedProfilesToday.get());
		stat.setProperty("crawler.failProfilesToday", failProfilesToday.get());
		stat.setProperty("crawler.discoveredPostsToday", discoveredPostsToday.get());
		stat.setProperty("crawler.discoveredCommentsToday", discoveredCommentsToday.get());
		stat.setProperty("crawler.crawlersPool", crawlersPool.getGraphPoolSize());
		stat.setProperty("crawler.totalTaskTimeout", totalTaskTimeout.get());
		stat.setProperty("crawler.profileStatsQueue", profileStatsQueue.size());
		stat.setProperty("crawler.isTerminating", isTerminating.get());
		stat.setProperty("crawlerspool.activeAppIds", CrawlersPool.activeAppIds.size());
		stat.setProperty("crawlerspool.problemAppIds", CrawlersPool.problemAppIds.size());
		stat.setProperty("crawlerspool.appId2UpdateAppInfo", CrawlersPool.appId2UpdateAppInfo.size());
	}

	public void shutdown() {
		isTerminating.set(true);
	}

}
