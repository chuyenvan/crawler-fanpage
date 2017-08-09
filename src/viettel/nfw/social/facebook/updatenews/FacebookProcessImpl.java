package viettel.nfw.social.facebook.updatenews;

import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.common.SMSAlertService;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class FacebookProcessImpl implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(FacebookProcessImpl.class);
	public static BlockingQueue<ObjectRequest> toCrawlObjects = new ArrayBlockingQueue<>(RunUpdateNews.MAX_CAPACITY);
	public static ConcurrentHashMap<String, Thread> monitorThreads = new ConcurrentHashMap<>();

	@Override
	public void run() {
		Thread.currentThread().setName("FacebookProcessImpl");
		statistic();

		List<GraphCrawler> graphCrawlers = new ArrayList<>();
		// load all app info
		Map<String, FacebookApp> appInfos = RunUpdateNews.facebookAppRepository.getAllData();
		for (Map.Entry<String, FacebookApp> entrySet : appInfos.entrySet()) {
			String appId = entrySet.getKey();
			FacebookApp appInfo = entrySet.getValue();
			LOG.info("print {}", appInfo.toString());
			GraphCrawler graphCrawler = new GraphCrawler(appInfo);
			graphCrawlers.add(graphCrawler);
			Thread threadApp = new Thread(graphCrawler);
			monitorThreads.put(appId, threadApp);
			threadApp.start();
		}

		while (true) {

			ObjectRequest objectRequest = toCrawlObjects.poll();
			if (objectRequest != null) {
				try {
					int graphCrawlerSize = graphCrawlers.size();
					if (graphCrawlerSize > 0) {
						String objectID = objectRequest.objectID;
						int hash = Math.abs(objectID.hashCode());
						int mod = hash % graphCrawlerSize;
						// send to crawl
						GraphCrawler graphCrawler = graphCrawlers.get(mod);
						if (graphCrawler.isSleepOrReachLimit()) {
                            // by pass
							// LOG.info("App {} reach limit in day or time to sleep", graphCrawler.appID);
							graphCrawler.addToBigQueue(objectRequest);
						} else {
							graphCrawler.addToBigQueue(objectRequest);
						}
					} else {
						throw new RuntimeException("NO graph crawled active");
					}
				} catch (Exception ex) {
					LOG.error(ex.getMessage(), ex);
				}
			} else {
				Funcs.sleep(Funcs.randInt(400, 800));
			}
		}
	}

	private void statistic() {
		Runnable checkQueueRunnable = new Runnable() {
			@Override
			public void run() {
				Thread.currentThread().setName("FacebookProcessImpl");
				LOG.info("toCrawlObjects Queue size {}", toCrawlObjects.size());
			}
		};

		Runnable monitorDownloaderRunnable = new Runnable() {

			@Override
			public void run() {
				Thread.currentThread().setName("Monitor");
				int totalSize = monitorThreads.size();
				LOG.info("Total Apps in monitor: {}", totalSize);
				int countAppAlive = 0;
				for (Map.Entry<String, Thread> entrySet : monitorThreads.entrySet()) {
					String appName = entrySet.getKey();
					Thread thread = entrySet.getValue();
					// java.lang.Thread.State can be NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED
					LOG.info("App: {} - State: {} - Is alive?: {}", new Object[]{appName, thread.getState(), thread.isAlive()});
					if (thread.isAlive()) {
						countAppAlive++;
					}
				}
				String message = String.format("Application Alive: %d/%d", countAppAlive, totalSize);
				LOG.info(message);

				SMSAlertService.SMSRequest smsRequest = new SMSAlertService.SMSRequest();
				smsRequest.mobile = ApplicationConfiguration.getInstance().getConfiguration("mobiles");
				smsRequest.sms = "CRAWLER_FB_GRAPH " + message;
				SMSAlertService.offer(SMSAlertService.SMS_GATEWAY_INTERNAL, SMSAlertService.USERNAME, SMSAlertService.PASSWORD, smsRequest);
			}
		};

		ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
		executor.scheduleAtFixedRate(checkQueueRunnable, 0, 3, TimeUnit.MINUTES);
		executor.scheduleAtFixedRate(monitorDownloaderRunnable, 0, 30, TimeUnit.MINUTES);
	}

}
