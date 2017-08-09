package viettel.nfw.social.facebook.pgcrawler.crawler;

import java.io.IOException;
import java.net.Proxy;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.eclipse.jetty.util.ConcurrentHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author Duong
 */
public class CrawlersPool {

	private static final Logger LOG = LoggerFactory.getLogger(CrawlersPool.class);
	private static final int DEFAULT_QUEUE_SIZE = 1000;
	private static final BlockingQueue<NewGraphCrawler> graphPool = new ArrayBlockingQueue<>(DEFAULT_QUEUE_SIZE);
	public static final ConcurrentHashSet<String> activeAppIds = new ConcurrentHashSet<>();
	public static final ConcurrentHashSet<String> problemAppIds = new ConcurrentHashSet<>();
	public static final ConcurrentHashMap<String, FacebookApp> appId2UpdateAppInfo = new ConcurrentHashMap<>();
        
	private final ProfileDatabaseHandler db;
	private final Proxy proxy;

	public CrawlersPool(ProfileDatabaseHandler db, Proxy proxy) {
		this.db = db;
		this.proxy = proxy;
		init();
	}

	public int getGraphPoolSize() {
		return graphPool.size();
	}

	public NewGraphCrawler pollGraph() throws InterruptedException {
		NewGraphCrawler graphCrawler = graphPool.take();
		String appId = graphCrawler.getAppID();
		if (appId2UpdateAppInfo.containsKey(appId)) {
			// this app need to update
			FacebookApp appInfo = appId2UpdateAppInfo.get(appId);
			graphCrawler.updateApp(appInfo);
			Funcs.sleep(500);
			appId2UpdateAppInfo.remove(appId);
		}
		return graphCrawler;
	}

	public void addGraph(NewGraphCrawler graphCrawler) throws InterruptedException {
		if (graphCrawler != null) {
			graphPool.put(graphCrawler);
		}
	}

	private void init() {
		Executors.newSingleThreadExecutor().execute(new Runnable() {

			@Override
			public void run() {
				Thread.currentThread().setName("ReviewApps");
				while (!CrawlerManager.isTerminating.get()) {
					try {
						// get list appId from mem
						Set<String> appIds = db.getAllInAppsList();
						FacebookApp facebookApp = null;
						for (String appId : appIds) {
							// check in active list
							if (activeAppIds.contains(appId)) {
								continue;
							}
							// get info in disk
							try {
								facebookApp = db.getFacebookApp(appId);
							} catch (IOException ex) {
								LOG.error("Error while getting facebook app info of " + appId, ex);
							}
							if (facebookApp != null) {
								String savedAppId = facebookApp.getAppID();
								if (!savedAppId.equalsIgnoreCase(appId)) {
									LOG.info("App {} has strange problem", savedAppId);
									continue;
								}
								// init NewGraphCrawler
								NewGraphCrawler newGraphCrawler = new NewGraphCrawler(facebookApp, db, proxy);
								boolean isGraphOK = false;
								try {
									isGraphOK = newGraphCrawler.checkToken(true);
								} catch (Exception e) {
									LOG.error(e.getMessage(), e);
								}
								if (isGraphOK) {
									graphPool.add(newGraphCrawler);
									activeAppIds.add(savedAppId);
								} else {
									LOG.info("App {} has problem", savedAppId);
									problemAppIds.add(savedAppId);
								}
							}
						}
						Funcs.sleep(60 * 60 * 1000L);
					} catch (Exception ex) {
						LOG.error(ex.getMessage(), ex);
						Funcs.sleep(10 * 60 * 1000L);
					}
				}
			}
		});
	}

}
