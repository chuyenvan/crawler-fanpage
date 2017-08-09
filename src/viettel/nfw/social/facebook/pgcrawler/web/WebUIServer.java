package viettel.nfw.social.facebook.pgcrawler.web;

import java.util.concurrent.RejectedExecutionException;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileSortedSet;

/**
 *
 * @author duongth5
 */
public class WebUIServer {

	private static final Logger LOG = LoggerFactory.getLogger(WebUIServer.class);
	private static final String MONITOR_UI = "web/";
	private static final int MAX_THREADS = 128;
	private static final int MAX_QUEUED = 10000;
	private final int serverPort;
	private org.eclipse.jetty.server.Server jetty;
	private final ProfileDatabaseHandler db;
	private final ProfileSortedSet pageGroupSortedSet;

	public WebUIServer(int port, ProfileDatabaseHandler db) {
		this.serverPort = port;
		this.db = db;
		this.pageGroupSortedSet = new ProfileSortedSet(this.db.getRedisConnPool(), this.db);
	}

	public void run() throws Exception {
		jetty = new org.eclipse.jetty.server.Server(serverPort);

		ThreadPool pool = new ThreadPool();
		pool.setMaxThreads(MAX_THREADS);
		pool.setMaxQueued(MAX_QUEUED);
		ResourceHandler resource = new ResourceHandler();
		resource.setDirectoriesListed(false);
		resource.setWelcomeFiles(new String[]{
			"index.html"
		});

		resource.setResourceBase(MONITOR_UI);
		HandlerCollection collection = new HandlerCollection();
		collection.addHandler(resource);

		ContextHandler adminHandler = new ContextHandler();
		adminHandler.setContextPath("/admin");
		adminHandler.setHandler(new AdminHandler(db));
		collection.addHandler(adminHandler);

		ContextHandler profileHandler = new ContextHandler();
		profileHandler.setContextPath("/profile");
		profileHandler.setHandler(new ProfileHandler(db, pageGroupSortedSet));
		collection.addHandler(profileHandler);
		
		ContextHandler appHandler = new ContextHandler();
		appHandler.setContextPath("/app");
		appHandler.setHandler(new AppHandler(db));
		collection.addHandler(appHandler);

		jetty.setHandler(collection);
		jetty.setThreadPool(pool);
		jetty.start();
		LOG.info("Jetty started!");
	}

	public void shutdown() {
		try {
			jetty.stop();
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
	}

	public static class ThreadPool extends org.eclipse.jetty.util.thread.QueuedThreadPool {

		@Override
		public boolean dispatch(Runnable job) {
			if (!super.dispatch(job)) {
				throw new RejectedExecutionException();
			}
			return true;
		}
	}
}
