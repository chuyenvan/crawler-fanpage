package viettel.nfw.social.facebook.evaluation;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.nigma.engine.util.Funcs;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.facebook.nologin.RunNoLogin;
import viettel.nfw.social.utils.FileUtils;

/**
 *
 * @author duongth5
 */
public class EvaluateProfilesByWebImpl implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(EvaluateProfilesByWebImpl.class);
	private final Account account;
	private final Proxy proxy;

	private boolean isBan;
	private boolean isLogOut;
	public static final int NUM_RETRY = 5;
	public static final String FORMAT_DATA_FILENAME = "%s_%s.data";
	public static final String DIR_STORAGE = "storage/evalweb/";

	public EvaluateProfilesByWebImpl(Account account, Proxy proxy) {
		this.account = account;
		this.proxy = proxy;
		isBan = false;
		isLogOut = false;
	}

	@Override
	public void run() {

		PhantomJSDriver driver = startDriver(RunEvaluation.proxyString, RunEvaluation.USER_AGENT_DF);

		// do Login
		for (int i = 0; i < NUM_RETRY; i++) {
			try {
				try {
					driver.get("https://facebook.com/");
				} catch (TimeoutException ex) {
					driver.navigate().refresh();
				} catch (Exception ex) {
				}
				Funcs.sleep(5000);
				driver.findElementById("email").sendKeys(account.getUsername());
				driver.findElementById("pass").sendKeys(account.getPassword());
				try {
					driver.findElementById("u_0_v").click();
				} catch (NoSuchElementException ex) {
					driver.findElementById("pass").sendKeys(Keys.ENTER);
					ex.printStackTrace();
				}
				Funcs.sleep(5000 + new Random().nextInt(3000));
				break;
			} catch (Exception ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}

		// check login
		if (!checkLogin(driver, account)) {
			try {
				String pageSource = driver.getPageSource();
				FileUtils.write(new File(account.getUsername() + ".data"), pageSource);
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
			}
			stopDriver(driver);
			return;
		}
		if (driver.getPageSource().length() < 80 * 1024) {
			LOG.error("LOGIN (MAYBE) FAIL FOR ACCOUNT {}, LENGTH = ", account.getUsername(), driver.getPageSource().length());
			stopDriver(driver);
			return;
		}
		LOG.error("LOGIN SUCCESSFULLY FOR ACCOUNT {}", account.getUsername());

		// do crawl profile
		while (true) {

			try {
				String crawlUrl = RunEvaluation.urlQueue.poll();

				if (StringUtils.isNotEmpty(crawlUrl)) {

					try {
						driver.get(crawlUrl);
					} catch (TimeoutException ex) {
						driver.navigate().refresh();
					} catch (UnreachableBrowserException ex) {
						stopDriver(driver);
						break;
					}

					Thread.sleep(600);

					String pageTitle = driver.getTitle();
					LOG.info("Title {}", pageTitle);

					// check redirect to security check page -> send current crawl link to priority queue, quit driver, init new once
					if (StringUtils.containsIgnoreCase(pageTitle, "Yêu cầu kiểm tra bảo mật")
						|| StringUtils.containsIgnoreCase(pageTitle, "Security Check Required")) {
						LOG.warn("Security Check - {}", crawlUrl);
						continue;
					}

					// check redirect to home page -> send current crawl link to priority queue, clear all cookies
					if (StringUtils.containsIgnoreCase(pageTitle, "Page Not Found")
						|| StringUtils.containsIgnoreCase(pageTitle, "Không Tìm Thấy Trang")
						|| StringUtils.containsIgnoreCase(pageTitle, "Welcome to Facebook")
						|| StringUtils.containsIgnoreCase(pageTitle, "Chào mừng bạn đến với Facebook")) {
						LOG.warn("Redirect to homepage - {}", crawlUrl);
						continue;
					}

					try {
						JavascriptExecutor jse = (JavascriptExecutor) driver;
						for (int second = 0;; second++) {
							if (second >= 5) {
								break;
							}
							jse.executeScript("window.scrollBy(0,800)", ""); //y value '800' can be altered
							Thread.sleep(3000);
						}
					} catch (Exception e) {
						LOG.error("Error in scroll down");
					}

					Funcs.sleep(5000);
					String currentUrl = driver.getCurrentUrl();
					String pageSource = driver.getPageSource();
					// add raw HTML to parse and push to bigdata
					RunNoLogin.crawledDataQueue.add(pageSource);
					// write to file
					String filename = String.format(FORMAT_DATA_FILENAME,
						String.valueOf(currentUrl.hashCode()),
						String.valueOf(System.currentTimeMillis()));
					String fileStorage = DIR_STORAGE + filename;
					FileUtils.write(new File(fileStorage), pageSource);
					LOG.info("DONE Write to file: {}", currentUrl);

				} else {
					Thread.sleep(1000);
				}
			} catch (Exception ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
	}

	public static boolean checkLogin(PhantomJSDriver driver, Account account) {
		try {
			if (driver.findElementById("email") != null) {
				LOG.error("LOGIN FAIL FOR ACCOUNT {}", account.getUsername());
				return false;
			}
		} catch (Exception ex) {
			// Skip nosuch elements
		}
		try {
			if (driver.findElementByXPath("//a[@title='Profile']") == null) {
				LOG.error("LOGIN FAIL FOR ACCOUNT {}", account.getUsername());
				return false;
			}
		} catch (Exception ex) {
			// Skip nosuch elements
		}
		return true;
	}

	private static PhantomJSDriver startDriver(String proxyString, String userAgent) {
		DesiredCapabilities cap = DesiredCapabilities.phantomjs();
		if (proxyString != null) {
			org.openqa.selenium.Proxy p = new org.openqa.selenium.Proxy();
			p.setHttpProxy(proxyString).setFtpProxy(proxyString).setSslProxy(proxyString);
			cap.setCapability(CapabilityType.PROXY, p);
		}
		cap.setJavascriptEnabled(true);
		cap.setCapability("phantomjs.page.settings.userAgent", userAgent);

		PhantomJSDriver driver = new PhantomJSDriver(cap);
		try {
			driver.manage().deleteAllCookies();
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}
		driver.manage().timeouts().pageLoadTimeout(1, TimeUnit.MINUTES);
		driver.manage().timeouts().setScriptTimeout(40, TimeUnit.SECONDS);
		driver.manage().timeouts().implicitlyWait(10, TimeUnit.SECONDS);

		return driver;
	}

	private static void stopDriver(PhantomJSDriver driver) {
		try {
			driver.quit();
		} catch (Exception ex) {
			LOG.error("Error in Stop Driver. Maybe, it's already dead!", ex);
		}
	}

}
