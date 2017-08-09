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
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Select;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.Funcs;
import viettel.nfw.social.utils.Pair;

/**
 *
 * @author minhht1
 */
public class CreateAccounts {

	private static final Logger LOG = LoggerFactory.getLogger(CreateAccounts.class);
//	private static final String PROXY = null;
	private static final String PROXY = "203.113.152.1:7020";

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

		List<FacebookApp> facebookApps = new ArrayList<>();
		for (Map.Entry<String, String> entrySet : account2Password.entrySet()) {
			String account = entrySet.getKey();
			String password = entrySet.getValue();
			LOG.info("Doing account {}", account);
			facebookApps.addAll(createAccFB(new Pair<>(account, password), port));
			LOG.info("#########################");
		}

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
			driver.get("https://www.facebook.com/");
			Funcs.sleep(Funcs.randInt(5000, 7000));
			// login
			driver.findElementById("u_0_1").sendKeys("Manh"); // Họ
			Funcs.sleep(Funcs.randInt(500, 1000));
			driver.findElementById("u_0_3").sendKeys("Nguyen"); // Tên
			Funcs.sleep(Funcs.randInt(500, 1000));
			driver.findElementById("u_0_5").sendKeys(account);
			Funcs.sleep(Funcs.randInt(500, 1000));
			driver.findElementById("u_0_8").sendKeys(account);
			Funcs.sleep(Funcs.randInt(500, 1000));
			driver.findElementById("u_0_a").sendKeys(password);
			Funcs.sleep(Funcs.randInt(500, 1000));
			new Select(driver.findElement(By.id("day"))).selectByVisibleText("21");
			new Select(driver.findElement(By.id("month"))).selectByVisibleText("Tháng 5");
			new Select(driver.findElement(By.id("year"))).selectByVisibleText("1988");
			driver.findElement(By.id("u_0_e")).click();
//			driver.findElementById("u_0_l").click();
			try {
				driver.findElementById("u_0_i").sendKeys(Keys.ENTER);
			} catch (NoSuchElementException ex) {
				ex.printStackTrace();
			}
			Funcs.sleep(Funcs.randInt(500, 1000));
			try {
//				if (driver.findElementById("email") != null) {
//					LOG.info(account + " login NOK");
//				} else {
				if (driver.findElementById("fbRequestsFlyout") != null) {
					LOG.info(account + " account OK");
				} else {
					LOG.info(account + " account NOK");
				}
//				}
			} catch (Exception ex) {
				LOG.info(account + "/" + password + " login NOK due to catch Exception when findElementById(fbRequestsFlyout)");
			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		} finally {
			Funcs.sleep(1000L);
			if (driver != null) {
				System.out.println("SHUTDOWN");
				driver.quit();
			}
		}
		return facebookApps;
	}
}
