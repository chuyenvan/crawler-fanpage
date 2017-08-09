package viettel.nfw.social.tool;

import java.io.File;
import java.net.Proxy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.itim.engine.parser.specialsites.SpecialSiteServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.utils.Pair;
import vn.viettel.utils.CustomizedFixedThreadPool;
import vn.viettel.utils.SimpleTimer;

/**
 *
 * @author duongth5
 */
public class SocialService {

	private static final Logger LOG = LoggerFactory.getLogger(SocialService.class);
	private static final String CONF_FILE_PATH = "conf/app-social-service.properties";

	public static void main(String[] args) {
		File configFile = new File(CONF_FILE_PATH);
		if (configFile.exists() && !configFile.isDirectory()) {
			ApplicationConfiguration.getInstance().initilize(CONF_FILE_PATH);
			String portStr = ApplicationConfiguration.getInstance().getConfigurationWithDefaultValue("social.service.port", "3333");

			int servicePort = -1;
			try {
				servicePort = Integer.parseInt(portStr);
			} catch (NumberFormatException ex) {
				LOG.error(ex.getMessage(), ex);
			}
			if (servicePort != -1) {
				new SocialService(servicePort, null).start();
			} else {
				LOG.info("Service port is error");
			}
		} else {
			LOG.error("Not found config file at {}", CONF_FILE_PATH);
		}
	}

	private static final int MAX_CAPACITY = 50000;
	private static final int NUMBER_DOWNLOADER = 5;

	private org.eclipse.jetty.server.Server jetty;
	private final int port;
	private final Proxy proxy;
	private DatabaseHandler db;

	private final CustomizedFixedThreadPool dowloaderPool = new CustomizedFixedThreadPool(NUMBER_DOWNLOADER, 10, "Downloader");
	private final Thread shutdownInterceptor;
	private final AtomicBoolean terminating = new AtomicBoolean(false);

	public static BlockingQueue<Pair<String, Boolean>> needToDownload = new ArrayBlockingQueue<>(MAX_CAPACITY);
	public static ConcurrentHashSet<String> inProgressSet = new ConcurrentHashSet<>();

	public SocialService(int port, Proxy proxy) {
		this.port = port;
		this.proxy = proxy;
		shutdownInterceptor = new Thread(
			new ShutdownInterceptor(this), ShutdownInterceptor.class.getName() + ".Thread");
		Runtime.getRuntime().addShutdownHook(shutdownInterceptor);
	}

	public void sigTerminate() {
		terminating.set(true);
	}

	public boolean isTerminating() {
		return terminating.get();
	}

	public void start() {
		SimpleTimer st = new SimpleTimer();

		db = DatabaseHandler.getInstance();
		if (db == null) {
			LOG.warn("Error with init database");
			return;
		}

		// start jetty server
		jetty = new org.eclipse.jetty.server.Server(port);

		SpecialSiteServer.ThreadPool pool = new SpecialSiteServer.ThreadPool();
		pool.setMaxThreads(128);
		pool.setMaxQueued(10000);
		jetty.setThreadPool(pool);

		ContextHandler getIdFromUsernameHandler = new ContextHandler();
		getIdFromUsernameHandler.setContextPath("/getid");
		getIdFromUsernameHandler.setHandler(new GetIdFromUsernameHandler(db));

		HandlerCollection collection = new HandlerCollection();
		collection.addHandler(getIdFromUsernameHandler);
		jetty.setHandler(collection);

		try {
			jetty.start();
		} catch (Exception ex) {
			LOG.error("Error while starting jetty" + ex.getMessage(), ex);
			return;
		}

		// start thread downloader
		for (int i = 0; i < NUMBER_DOWNLOADER; i++) {
			dowloaderPool.execute(new FindIdDownloader(db, proxy));
		}
	}

	private static class ShutdownInterceptor implements Runnable {

		private SocialService owner = null;

		public ShutdownInterceptor(SocialService owner) {
			this.owner = owner;
		}

		@Override
		public void run() {
			LOG.warn("Starting shutdown process ...");
			try {
				owner.jetty.stop();
				LOG.warn("Shutdown jetty.");
			} catch (Exception ex) {
				LOG.error(ex.getMessage(), ex);
			}
			owner.dowloaderPool.shutdown();
			LOG.warn("Shutdown downloader pool.");
			owner.db.shutdown();
			LOG.warn("Shutdown database.");
		}
	}

}
