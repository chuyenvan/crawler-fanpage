package viettel.nfw.social.facebook.deeptracking;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import viettel.nfw.social.common.QueryFakeAccount;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.gfw.vn.producer.producer.MessageInfo;
import viettel.gfw.vn.producer.producer.ProducerORMWeb;
import viettel.gfw.vn.social.FaceBookProto;
import viettel.gfw.vn.social.FaceBookProtoTrans;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.common.ConfigurationChangeListner;
import viettel.nfw.social.controller.ControllerReporter;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class RunDeepTracking {

	private static final Logger LOG = LoggerFactory.getLogger(RunDeepTracking.class);

	private static final String CONF_FILE_PATH = "conf/app-deep-tracking.properties";
	private static final String TOPIC_NAME = "PROFILE_DATA_RECEIVE";
	private static final String TOPIC_NAME_2 = "PROFILE_DATA_RECEIVE_FULL";
	private static final int MAX_CAPACITY = 2000000;

	private static final BlockingQueue<Account> accQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
	private static final Proxy proxy = null;
	public static ConcurrentHashMap<String, String> activeAccounts = new ConcurrentHashMap<>();

	public static BlockingQueue<String> facebookUrlsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
	public static BlockingQueue<String> facebookTrungNT3UrlsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);

	public static BlockingQueue<String> googleUrlsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
	public static BlockingQueue<String> twitterUrlsQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);

	public static BlockingQueue<ProfileInfo> profileInfoQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);
	public static BlockingQueue<FacebookObject> facebookObjectQueue = new ArrayBlockingQueue<>(MAX_CAPACITY);

	public static ConcurrentHashMap<String, Thread> monitorThreads = new ConcurrentHashMap<>();

	public static void main(String[] args) {

		// Monitor thread
		MonitorThreadImpl monitorThreadImpl = new MonitorThreadImpl(60);
		new Thread(monitorThreadImpl).start();

		// Thread watch configuration
		ConfigurationChangeListner listner = new ConfigurationChangeListner(CONF_FILE_PATH);
		Thread configListnerThread = new Thread(listner);
		monitorThreads.put("ConfigurationChangeListner", configListnerThread);
		configListnerThread.start();
		LOG.info("Started ConfigurationChangeListner");
		Funcs.sleep(2000);

		// Thread get url
		String host = ApplicationConfiguration.getInstance().getConfiguration("activemq.ip");
		int port = Integer.parseInt(ApplicationConfiguration.getInstance().getConfiguration("activemq.port"));
		String activeMQUrl = String.format("tcp://%s:%d", host, port);
		String activeMQUsername = ApplicationConfiguration.getInstance().getConfiguration("activemq.username");
		String activeMQPassword = ApplicationConfiguration.getInstance().getConfiguration("activemq.password");
		GetProfileUrlsConsumer getUrls = new GetProfileUrlsConsumer(activeMQUrl, activeMQUsername, activeMQPassword);
		Thread getProfileUrlsThread = new Thread(getUrls);
		monitorThreads.put("getProfileUrlsThread", getProfileUrlsThread);
		getProfileUrlsThread.start();

		// Thread send profile info to ActiveMQ
		SendProfileInfoImpl sendProfileInfoImpl = new SendProfileInfoImpl(host, port, activeMQUsername, activeMQPassword);
		Thread sendProfileInfoThread = new Thread(sendProfileInfoImpl);
		monitorThreads.put("sendProfileInfoThread", sendProfileInfoThread);
		sendProfileInfoThread.start();

		// Thread send facebook object to bigdata
		SendObjectToBGImpl sendObjectToBGImpl = new SendObjectToBGImpl(host, port, activeMQUsername, activeMQPassword);
		Thread sendObjectToBGThread = new Thread(sendObjectToBGImpl);
		monitorThreads.put("sendObjectToBGThread", sendObjectToBGThread);
		sendObjectToBGThread.start();

		// Thread query accounts
		// QueryAccountsImpl queryImpl = new QueryAccountsImpl();
		QueryAccountsByFileImpl queryImpl = new QueryAccountsByFileImpl();
		Thread queryAccountsThread = new Thread(queryImpl);
		monitorThreads.put("queryAccountsThread", queryAccountsThread);
		queryAccountsThread.start();

		// Thread take account from queue
		TakeAccountsImpl takeImpl = new TakeAccountsImpl();
		Thread takeAccountsThread = new Thread(takeImpl);
		monitorThreads.put("takeAccountsThread", takeAccountsThread);
		takeAccountsThread.start();

	}

	private static class MonitorThreadImpl implements Runnable {

		private static final Logger LOG = LoggerFactory.getLogger(MonitorThreadImpl.class);
		private final int second;
		private boolean run = true;

		public MonitorThreadImpl(int delay) {
			this.second = delay;
		}

		public void shutdown() {
			this.run = false;
		}

		@Override
		public void run() {
			Thread.currentThread().setName("MonitorThread");
			while (run) {

				try {
					LOG.info("Number thread in monitor: {}", monitorThreads.size());
					for (Map.Entry<String, Thread> entrySet : monitorThreads.entrySet()) {
						String threadName = entrySet.getKey();
						Thread thread = entrySet.getValue();
						// java.lang.Thread.State can be NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED
						LOG.info("Thread: {} - State: {} - Is alive?: {}", new Object[]{threadName, thread.getState(), thread.isAlive()});
					}
				} catch (Exception ex) {
					LOG.error(ex.getMessage(), ex);
				}

				Funcs.sleep(second * 1000);
			}
		}
	}

	private static class SendObjectToBGImpl implements Runnable {

		private static final ProducerORMWeb producer = new ProducerORMWeb("orm_web");

		private final String host;
		private final int port;
		private final String username;
		private final String password;

		public SendObjectToBGImpl(String host, int port, String username, String password) {
			this.host = host;
			this.port = port;
			this.username = username;
			this.password = password;
		}

		@Override
		public void run() {
			Thread.currentThread().setName("SendObjectToBGImpl");

			// Thread reporter
			ControllerReporter reporter = ControllerReporter.getDefault(host, port, username, password, TOPIC_NAME_2);
			reporter.start();

			Funcs.sleep(2000);
			while (true) {
				try {
					FacebookObject fbObj = facebookObjectQueue.poll();
					if (fbObj == null) {
						Thread.sleep(1000);
					} else {
						String profileId = fbObj.getInfo().getId();
						String profileUrl = fbObj.getInfo().getUrl();
						if (StringUtils.isEmpty(profileId)) {
							LOG.info("PROFILE_ID_EMPTY {}", JSON.encode(fbObj));
							continue;
						}
						if (StringUtils.isNotEmpty(profileId) && StringUtils.isEmpty(profileUrl)) {
							LOG.info("PROFILE_URL_EMPTY {}", JSON.encode(fbObj));
							fbObj.getInfo().setUrl("https://www.facebook.com/profile.php?id=" + profileId);
						}
						MessageInfo message = new MessageInfo();
						FaceBookProto.FBSocialObject fbSocialObject = FaceBookProtoTrans.facebookObjectTo(fbObj);
						message.setDataSocial(fbSocialObject, MessageInfo.MESSAGE_TYPE_SOCIAL_FACEOOK);
						producer.sendMessageORMWeb(message);
						try {
							reporter.offer(fbObj);
						} catch (Exception e) {
							LOG.error("Error send full object to activemq", e);
						}
					}
				} catch (Exception ex) {
					LOG.error(ex.getMessage(), ex);
				}
			}
		}

	}

	private static class SendProfileInfoImpl implements Runnable {

		private final String host;
		private final int port;
		private final String username;
		private final String password;

		public SendProfileInfoImpl(String host, int port, String username, String password) {
			this.host = host;
			this.port = port;
			this.username = username;
			this.password = password;
		}

		@Override
		public void run() {
			Thread.currentThread().setName("SendProfileInfoImpl");

			// Thread reporter
			ControllerReporter reporter = ControllerReporter.getDefault(host, port, username, password, TOPIC_NAME);
			reporter.start();

			Funcs.sleep(2000);

			while (true) {
				try {
					ProfileInfo profile = profileInfoQueue.poll();
					if (profile == null) {
						Thread.sleep(1000);
					} else {
						reporter.offer(profile);
					}
				} catch (Exception ex) {
					LOG.error(ex.getMessage(), ex);
				}

			}
		}

	}

	private static class TakeAccountsImpl implements Runnable {

		private static final Logger LOG = LoggerFactory.getLogger(TakeAccountsImpl.class);

		@Override
		public void run() {
			Thread.currentThread().setName("TakeAccountsImpl");

			while (true) {
				try {
					Thread.sleep(4 * 1000);
				} catch (InterruptedException ex) {
					LOG.error(ex.getMessage(), ex);
				}
				LOG.info("Queue size {}", accQueue.size());
				Account nextAcc = accQueue.poll();
				if (nextAcc == null) {
					// LOG.info("Queue is empty");
					try {
						Thread.sleep(15 * 1000);
					} catch (InterruptedException ex) {
						LOG.error(ex.getMessage(), ex);
					}
				} else {
					LOG.info("Start account {}", nextAcc.getUsername());
					DeepTrackingProfilesImpl deepTracking = new DeepTrackingProfilesImpl(nextAcc, proxy);
					new Thread(deepTracking).start();
				}
			}
		}

	}

	private static class QueryAccountsImpl implements Runnable {

		private static final Logger LOG = LoggerFactory.getLogger(QueryAccountsImpl.class);

		private static final String SERVER_IP = "82deep";

		@Override
		public void run() {
			Thread.currentThread().setName("QueryAccountsImpl");
			while (true) {

				List<Account> fbAccounts = new ArrayList<>();
				List<String> lockedAccounts = new ArrayList<>();

				try {
					// query accounts by ip and type
					fbAccounts.addAll(QueryFakeAccount.getByIp(SERVER_IP, "m.facebook.com"));
					fbAccounts.addAll(QueryFakeAccount.getByIp("83eval", "m.facebook.com"));

					// query list locked accounts
					lockedAccounts = ServiceOutlinks.getLockedAccounts();
				} catch (Exception ex) {
					LOG.error(ex.getMessage(), ex);
				}

				for (Account fbAccount : fbAccounts) {
					String username = fbAccount.getUsername();
					if (lockedAccounts.contains(username)) {
						LOG.info("LOCKED {}", username);
						continue;
					}
					if (activeAccounts.containsKey(username)) {
						LOG.info("Account {} is active ...", username);
						continue;
					}
					accQueue.add(fbAccount);
					activeAccounts.put(fbAccount.getUsername(), fbAccount.getPassword());
				}

				try {
					LOG.info("Sleep for 1 hour");
					Thread.sleep(60 * 60 * 1000);
				} catch (InterruptedException ex) {
					LOG.error(ex.getMessage(), ex);
				}
			}
		}

	}

	private static class QueryAccountsByFileImpl implements Runnable {

		private static final Logger LOG = LoggerFactory.getLogger(QueryAccountsByFileImpl.class);

		@Override
		public void run() {
			Thread.currentThread().setName("QueryAccountsByFileImpl");

			List<Account> fbAccounts = new ArrayList<>();
			List<String> lockedAccounts = new ArrayList<>();

			String filename = "input/facebook-accounts-deep.txt";
			try {
				try (BufferedReader br = new BufferedReader(new FileReader(new File(filename)))) {
					String line;
					while ((line = br.readLine()) != null) {
						String temp = line.trim();
						String[] parts = StringUtils.split(temp, "\t");
						Account acc = new Account();
						acc.setUsername(parts[0]);
						acc.setPassword(parts[1]);
						acc.setUserAgent(parts[2]);
						fbAccounts.add(acc);
					}
				}
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
			}

			for (Account fbAccount : fbAccounts) {
				String username = fbAccount.getUsername();
				if (lockedAccounts.contains(username)) {
					LOG.info("LOCKED {}", username);
					continue;
				}
				if (activeAccounts.containsKey(username)) {
					LOG.info("Account {} is active ...", username);
					continue;
				}
				accQueue.add(fbAccount);
				activeAccounts.put(fbAccount.getUsername(), fbAccount.getPassword());
			}

			try {
				LOG.info("Sleep for 1 hour");
				Thread.sleep(60 * 60 * 1000);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}

	}

}
