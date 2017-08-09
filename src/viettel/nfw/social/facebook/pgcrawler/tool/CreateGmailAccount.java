package viettel.nfw.social.facebook.pgcrawler.tool;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.Funcs;
import viettel.nfw.social.utils.Pair;
import org.openqa.selenium.support.ui.Select;

/**
 *
 * @author minhht1
 */
public class CreateGmailAccount {

	private static final Logger LOG = LoggerFactory.getLogger(CreateGmailAccount.class);
//	private static final String PROXY = null;
	private static final String PROXY = "203.113.152.1:7020";
//	private static final String PROXY = "192.168.5.10:3128";

	public static void main(String[] args) throws IOException {
		List<String> rows = FileUtils.readList(new File("email.txt"));
		Map<String, String> account2Password = new HashMap<>();
		for (String row : rows) {
			String[] parts = row.split("\t");
			if (parts.length == 2) {
				account2Password.put(parts[0], parts[1]);
			}
		}

		int port = Funcs.randInt(9000, 9999);
		Runtime.getRuntime().exec("chromedriver.exe --port=" + port);
		createAccFB(new Pair<>("hoangdepzai2017", "abcd@2017"), port);

//		List<FacebookApp> facebookApps = new ArrayList<>();
//		for (Map.Entry<String, String> entrySet : account2Password.entrySet()) {
//			String account = entrySet.getKey();
//			String password = entrySet.getValue();
//			LOG.info("Doing account {}", account);
//			facebookApps.addAll(createAccFB(new Pair<>(account, password), port));
//			LOG.info("#########################");
//		}
//		List<String> results = new ArrayList<>();
//		for (FacebookApp facebookApp : facebookApps) {
//			String text = facebookApp.getAccountName() + "\t" + facebookApp.getAccountPass() + "\t"
//				+ facebookApp.getAppID() + "\t" + facebookApp.getUserAccessToken() + "\t" + facebookApp.getAppAccessToken();
//			results.add(text);
//		}
//		FileUtils.write(new File("app-infos.text"), results);
	}

	private static List<FacebookApp> createAccFB(Pair<String, String> accountPassword, int chromeDriverPort) {
		List<FacebookApp> facebookApps = new ArrayList<>();
		RemoteWebDriver driver = null;
		String account = accountPassword.first;
		String password = accountPassword.second;
		try {
			ChromeOptions options = new ChromeOptions();
			options.addArguments("--user-agent=Mozilla/5.0 (Linux; Android 6.0; LG-H901 Build/MRA58K; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/54.0.2840.85 Mobile Safari/537.36 Zalo/1.0");
//			options.addArguments("Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/56.0.2924.87 Safari/537.36");
			options.addArguments("--lang=en-US");
			DesiredCapabilities cap = new DesiredCapabilities();
			cap.setCapability(ChromeOptions.CAPABILITY, options);

			if (!StringUtils.isEmpty(PROXY)) {
				org.openqa.selenium.Proxy proxy = new org.openqa.selenium.Proxy();
				proxy.setHttpProxy(PROXY)
						.setFtpProxy(PROXY)
						.setSslProxy(PROXY);
				cap.setCapability(CapabilityType.PROXY, proxy);
			}
			driver = new RemoteWebDriver(new URL("http://localhost:" + chromeDriverPort), cap);
			// go to homepage
			driver.get("https://accounts.google.com/SignUp?service=mail");
			Funcs.sleep(Funcs.randInt(1000, 3000));
			driver.findElementById("LastName").sendKeys("Vu"); // Họ
			Funcs.sleep(Funcs.randInt(500, 1000));
			driver.findElementById("FirstName").sendKeys("Viet Hoang"); // Tên
			Funcs.sleep(Funcs.randInt(500, 1000));
			driver.findElementById("GmailAddress").sendKeys(account);
			Funcs.sleep(Funcs.randInt(500, 1000));
			driver.findElementById("Passwd").sendKeys(password);
			Funcs.sleep(Funcs.randInt(500, 1000));
			driver.findElementById("PasswdAgain").sendKeys(password);
			Funcs.sleep(Funcs.randInt(500, 1000));

			driver.findElementById("BirthDay").sendKeys("21");
			driver.findElementById("BirthMonth").sendKeys("May");
			driver.findElementById("BirthYear").sendKeys("1988");
			driver.findElementById("RecoveryPhoneNumber").sendKeys("980000001");
			driver.findElementById("Gender").sendKeys("Male");
//			new Select(driver.findElement(By.xpath("//a[contains(text(), 'Giới tính')]"))).selectByVisibleText("Nam");
			try {
				Funcs.sleep(Funcs.randInt(500, 3000));
				driver.findElementById("submitbutton").sendKeys(Keys.ENTER);
				LOG.info("NEW_ACC: {}\t{}", account, password);
			} catch (NoSuchElementException ex) {
				LOG.error("Create failed: " + account + " " + ex.getMessage(), ex);
			}
//			Funcs.sleep(Funcs.randInt(500, 1000));
//			try {
//				WebElement we = driver.findElementById("code_in_cliff");
//				if (we != null) {
////					String code = "60568";
////					we.sendKeys(code);
////					driver.findElementById("u_9_2").sendKeys(Keys.ENTER);
//
//				} else {
//					LOG.error("Không xuất hiện textbox nhập mã từ SMS");
//				}
//
////				if (driver.findElementById("email") != null) {
////					LOG.info(account + " login NOK");
////				} else {
//				if (driver.findElementById("fbRequestsFlyout") != null) {
//					LOG.info(account + " account OK");
//				} else {
//					LOG.info(account + " account NOK");
//				}
////				}
//			} catch (Exception ex) {
//				LOG.info(account + "/" + password + " login NOK due to catch Exception when findElementById(fbRequestsFlyout)");
//			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		} finally {
//			Funcs.sleep(1000L);
//			if (driver != null) {
//				System.out.println("SHUTDOWN");
//				driver.quit();
//			}
		}
		return facebookApps;
	}
}
