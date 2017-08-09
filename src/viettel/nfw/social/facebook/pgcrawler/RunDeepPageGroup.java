package viettel.nfw.social.facebook.pgcrawler;

import viettel.nfw.social.facebook.pgcrawler.planner.Planner;
import viettel.nfw.social.facebook.pgcrawler.crawler.CrawlerManager;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.pgcrawler.monitoring.MonitoringStatistics;
import viettel.nfw.social.utils.EngineConfiguration;
import viettel.nfw.social.facebook.pgcrawler.web.WebUIServer;
import viettel.nfw.social.utils.DateUtils;
import viettel.nfw.social.utils.Funcs;
import vn.itim.detector.LanguageDetector;
import vn.viettel.utils.SimpleTimer;

/**
 *
 * @author duongth5
 */
public class RunDeepPageGroup {

	private static final Logger LOG = LoggerFactory.getLogger(RunDeepPageGroup.class);

	static {
		try {
			PropertyConfigurator.configure("conf/log4j.cfg");
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	public static void main(String[] args) {
		new RunDeepPageGroup().run();
	}

	private final Thread shutdownInterceptor;
	private final AtomicBoolean terminating = new AtomicBoolean(false);

	private Planner planner;
	private CrawlerManager crawlerManager;
	private WebUIServer webUIServer;
	private ProfileDatabaseHandler db;
	private static final int SCHEDULED_EXECUTOR_THREAD_POOL_SIZE = 1;
	private static final int SCHEDULED_TIME_SMS_UPDATE = EngineConfiguration.get().getInt("facebookdeep.sms.scheduledtime", 60);
	private static final int WEB_PORT = EngineConfiguration.get().getInt("facebookdeep.web.port", 7018);
	private static final boolean ENABLE_CRAWL_WITH_ACCOUNT = EngineConfiguration.get().getBoolean("facebookdeep.crawluser", false); // for crawl user

	private final LanguageDetector languageDetector;

	private final ScheduledExecutorService scheduledExecutor;

	public RunDeepPageGroup() {
		this.languageDetector = new LanguageDetector();
		shutdownInterceptor = new Thread(
				new ShutdownInterceptor(this), ShutdownInterceptor.class.getName() + ".Thread");
		Runtime.getRuntime().addShutdownHook(shutdownInterceptor);
		this.scheduledExecutor = Executors.newScheduledThreadPool(SCHEDULED_EXECUTOR_THREAD_POOL_SIZE);
	}

	public void run() {
		// database handler
		LOG.info("Initing db!");
		db = ProfileDatabaseHandler.getInstance();
		if (db == null) {
			LOG.error("Error while loading db");
			return;
		}
		LOG.info("Done init db!");

		Funcs.sleep(50);

		// Crawler
		crawlerManager = new CrawlerManager(db, ENABLE_CRAWL_WITH_ACCOUNT);
		crawlerManager.run();
		LOG.info("Started CrawlerManager!");

		Funcs.sleep(50);

		// Planner
		planner = new Planner(db, languageDetector);
		planner.run();
		LOG.info("Started Planner!");

		// web service
		webUIServer = new WebUIServer(WEB_PORT, db);
		try {
			webUIServer.run();
			LOG.info("Web UI Server started at {}", WEB_PORT);
		} catch (Exception ex) {
			LOG.error("Error while start web ui server", ex);
		}
		LOG.info("Started Web server!");

		long currentMinutes = DateUtils.getCurrentMinutes();
//		long initDelayForSMS = (currentMinutes <= 15) ? (15 - currentMinutes) : (60 - currentMinutes + 15);
		long initDelayForSMS = 60 - currentMinutes;
		scheduledExecutor.scheduleAtFixedRate(new AlertServiceImpl(), initDelayForSMS, SCHEDULED_TIME_SMS_UPDATE, TimeUnit.MINUTES);
		LOG.info("Started Alert Service!");

		watcherInBackground();
	}

	private void watcherInBackground() {
		Executors.newSingleThreadExecutor().execute(new Runnable() {

			@Override
			public void run() {
				Thread.currentThread().setName("Monitor");
				while (!terminating.get()) {
					MonitoringStatistics monitoringStatistics = new MonitoringStatistics();
					LOG.info("\n------------\n{}", fillStatistic(monitoringStatistics).toString());

					Funcs.sleep(30000);
				}
				shutdown();
			}
		});
	}

	public MonitoringStatistics fillStatistic(MonitoringStatistics stat) {
		crawlerManager.fillStatistic(stat);
		planner.fillStatistic(stat);
		return stat;
	}

	/**
	 * Will set terminating to true
	 */
	public void sigTerminate() {
		terminating.set(true);
	}

	public void shutdown() {
		SimpleTimer st = new SimpleTimer();
		crawlerManager.shutdown();
		LOG.info("Shutdown crawlerManager in {} ms", st.getTimeAndReset());
		planner.shutdown();
		LOG.info("Shutdown planner in {} ms", st.getTimeAndReset());
		webUIServer.shutdown();
		LOG.info("Shutdown webUIServer in {} ms", st.getTimeAndReset());
		try {
			db.shutdown();
			LOG.info("Shutdown db in {} ms", st.getTimeAndReset());
			scheduledExecutor.shutdown();
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}
	}

	private static class ShutdownInterceptor implements Runnable {

		private RunDeepPageGroup owner = null;

		public ShutdownInterceptor(RunDeepPageGroup owner) {
			this.owner = owner;
		}

		@Override
		public void run() {
			owner.sigTerminate();
			owner.shutdown();
		}
	}
}
