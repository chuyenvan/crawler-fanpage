package viettel.nfw.social.facebook.pgcrawler.planner;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileSortedSet;
import viettel.nfw.social.facebook.pgcrawler.monitoring.MonitoringStatistics;
import viettel.nfw.social.utils.EngineConfiguration;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.utils.Pair;
import vn.itim.detector.LanguageDetector;

/**
 *
 * @author Duong
 */
public class Planner {

	private static final Logger LOG = LoggerFactory.getLogger(Planner.class);
	private static final int TO_CRAWL_QUEUE_SIZE = EngineConfiguration.get().getInt("facebookdeep.tocrawlqueue.size", 10000);
        
	private final ProfileDatabaseHandler db;
	private final ProfileSortedSet pageGroupSortedSet;
	private final LanguageDetector languageDetector;
        
	public static BlockingQueue<String> toCrawlQueue = new ArrayBlockingQueue<>(TO_CRAWL_QUEUE_SIZE);
	public static BlockingQueue<Pair<String, FacebookObject>> crawledQueue = new ArrayBlockingQueue<>(TO_CRAWL_QUEUE_SIZE);
	public static ConcurrentHashMap<String, Long> profileInCrawlingMap = new ConcurrentHashMap<>();
        
	private Thread getProfileIdFromRedisThread;
	private Thread receiveFromCrawlerThread;
        
	public static AtomicBoolean isTerminating = new AtomicBoolean(false);
        
	public Planner(ProfileDatabaseHandler db, LanguageDetector languageDetector) {
		this.db = db;
		this.pageGroupSortedSet = new ProfileSortedSet(this.db.getRedisConnPool(), this.db);
		this.languageDetector = languageDetector;
	}
        
	public void run() {
		PlannerGetProfileIdFromRedis getProfileIdFromRedisImpl = new PlannerGetProfileIdFromRedis(pageGroupSortedSet);
		getProfileIdFromRedisThread = new Thread(getProfileIdFromRedisImpl, "GetProfileIdFromRedis");
		getProfileIdFromRedisThread.start();

		PlannerReceiveFromCrawler receiveFromCrawler = new PlannerReceiveFromCrawler(db, languageDetector);
		receiveFromCrawlerThread = new Thread(receiveFromCrawler, "ReceiveFromCrawler");
		receiveFromCrawlerThread.start();
	}
        
	public void fillStatistic(MonitoringStatistics stat) {
		stat.setProperty("planner.toCrawlQueue", toCrawlQueue.size());
		stat.setProperty("planner.crawledQueue", crawledQueue.size());
		stat.setProperty("planner.getProfileIdFromRedisThread.isAlive", getProfileIdFromRedisThread.isAlive());
		stat.setProperty("planner.receiveFromCrawlerThread.isAlive", receiveFromCrawlerThread.isAlive());
		stat.setProperty("planner.isTerminating", isTerminating.get());
	}

	public void shutdown() {
		isTerminating.set(true);
	}
}
