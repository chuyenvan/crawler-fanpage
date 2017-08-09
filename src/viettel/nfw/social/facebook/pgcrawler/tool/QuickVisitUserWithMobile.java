package viettel.nfw.social.facebook.pgcrawler.tool;

import java.io.File;
import java.io.IOException;
import java.net.CookieManager;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.pgcrawler.crawler.FacebookMobileActions;
import viettel.nfw.social.utils.FileUtils;
import vn.viettel.utils.CustomizedFixedThreadPool;

/**
 * TODO implement this to assign user to an account
 *
 * @author Duong
 */
public class QuickVisitUserWithMobile {

	private static final Logger LOG = LoggerFactory.getLogger(QuickVisitUserWithMobile.class);
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1";

	public static void main(String[] args) throws IOException {
//		quickLoginCheck();
//		quickFilterProfileToCrawlAccount();
		test();
	}

	private static void quickLoginCheck() throws IOException {
		List<Account> accounts = new ArrayList<>();
		// read account file
		List<String> rows = FileUtils.readList(new File("acc-duongth5-201510"));
		for (String row : rows) {
			String[] parts = row.split("\t");
			if (parts.length == 2) {
				String username = parts[0];
				String password = parts[1];
				Account account = new Account();
				account.setUsername(username);
				account.setPassword(password);
				account.setUserAgent(USER_AGENT);
				accounts.add(account);
			} else {
				LOG.warn("Problem with row {}", row);
			}
		}

		Proxy proxy = null;
		for (Account account : accounts) {
			CookieManager cookieManager = new CookieManager();
			FacebookMobileActions crawler = new FacebookMobileActions(account, cookieManager, proxy);
			long startTime = System.currentTimeMillis();

			// do login
			AccountStatus accStatus = crawler.login();
			LOG.info("{}:{}", account.getUsername(), accStatus.toString());
		}
	}

	private static void quickFilterProfileToCrawlAccount() throws IOException {
		List<Account> accounts = new ArrayList<>();
		// read account file
		List<String> rows = FileUtils.readList(new File("acc-duongth5-201510"));
		for (String row : rows) {
			if (!StringUtils.startsWith(row, "#")) {
				String[] parts = row.split("\t");
				if (parts.length == 2) {
					String username = parts[0];
					String password = parts[1];
					Account account = new Account();
					account.setUsername(username);
					account.setPassword(password);
					account.setUserAgent(USER_AGENT);
					accounts.add(account);
				}
			}
		}

		Proxy proxy = null;
		ArrayBlockingQueue<FacebookMobileActions> crawlerPool = new ArrayBlockingQueue<>(70);
		for (Account account : accounts) {
			CookieManager cookieManager = new CookieManager();
			FacebookMobileActions crawler = new FacebookMobileActions(account, cookieManager, proxy);
			long startTime = System.currentTimeMillis();

			// do login
			AccountStatus accStatus = crawler.login();
			LOG.info("{}:{}", account.getUsername(), accStatus.toString());
			if (accStatus.equals(AccountStatus.ACTIVE)) {
				crawlerPool.add(crawler);
			}
		}
	}

	private static void test() {
		final ArrayBlockingQueue<String> pool = new ArrayBlockingQueue<>(100);
		for (int i = 0; i < 100; i++) {
			pool.add("App-" + i);
		}

		CustomizedFixedThreadPool threads = new CustomizedFixedThreadPool(30, 30, "Thread.");
		for (int i = 0; i < 30; i++) {
			threads.execute(new Runnable() {

				@Override
				public void run() {
					while (true) {
						String app = "";
						try {
							app = pool.take();
							LOG.info("Borrow {}", app);
						} catch (InterruptedException ex) {
							LOG.error(ex.getMessage(), ex);
						}
						try {
							Thread.sleep(2000);
						} catch (Exception e) {
						} finally {
							if (app != null) {
								try {
									pool.put(app);
									LOG.info("Retun {}", app);
								} catch (InterruptedException ex) {
									LOG.error(ex.getMessage(), ex);
								}
							}
						}
					}
				}
			});
		}
	}
}
