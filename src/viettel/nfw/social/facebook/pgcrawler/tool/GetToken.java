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
import viettel.nfw.social.utils.TParser;

/**
 *
 * @author duongth5
 */
public class GetToken {

	private static final Logger LOG = LoggerFactory.getLogger(GetToken.class);
//	private static final String PROXY = null;
	private static final String PROXY = "203.113.152.1:7020";

	public static void main(String[] args) throws IOException {

		List<String> rows = FileUtils.readList(new File("deep-accounts.txt"));
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
			facebookApps.addAll(extendTokenOfAccount(new Pair<>(account, password), port));
			LOG.info("#########################");
		}

		List<String> results = new ArrayList<>();
		for (FacebookApp facebookApp : facebookApps) {
			String text = facebookApp.getAccountName() + "\t" + facebookApp.getAccountPass() + "\t"
				+ facebookApp.getAppID() + "\t" + facebookApp.getUserAccessToken() + "\t" + facebookApp.getAppAccessToken();
			results.add(text);
		}
		FileUtils.write(new File("app-infos.text"), results);

	}

	private static List<FacebookApp> extendTokenOfAccount(Pair<String, String> accountPassword, int chromeDriverPort) {
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
			driver.findElementById("email").sendKeys(account);
			Funcs.sleep(Funcs.randInt(500, 1000));
			driver.findElementById("pass").sendKeys(password);
			Funcs.sleep(Funcs.randInt(500, 1000));
//			driver.findElementById("u_0_l").click();
			 try {
                driver.findElementById("pass").sendKeys(Keys.ENTER);
            } catch (NoSuchElementException ex) {
                ex.printStackTrace();
            }

			// go to tool accesstoken
			Funcs.sleep(Funcs.randInt(8000, 11000));
			driver.get("https://developers.facebook.com/tools/accesstoken/");
			Funcs.sleep(Funcs.randInt(5000, 7000));
			List<WebElement> divRoleMains = driver.findElements(By.xpath("//div[@role='main']"));
			List<String> appTableStrings = new ArrayList<>();
			if (divRoleMains != null) {
				if (divRoleMains.size() == 1) {
					List<WebElement> appTables = divRoleMains.get(0).findElements(By.cssSelector("div.noPadding"));
					if (appTables != null) {
						for (WebElement appTable : appTables) {
							String appTableString = appTable.getText();
							if (StringUtils.isNotEmpty(appTableString)) {
								appTableStrings.add(appTableString);
							}
						}
					} else {
						System.out.println("AppTable NULL");
					}
				} else {
					System.out.println("more than div role main");
				}
			} else {
				System.out.println("NULL");
			}

			List<AppTokensInfo> appTokensInfos = new ArrayList<>();

			for (String appTableString : appTableStrings) {
//				System.out.println(appTableString);
				String[] parts = appTableString.split("\n");
				if (parts.length >= 6) {
//					System.out.println("Title: " + parts[0]);
//					System.out.println(parts[1] + ": " + parts[2]);
//					System.out.println(parts[4] + ": " + parts[5]);
					String debugUrl = "https://developers.facebook.com/tools/debug?q=" + parts[2];

					appTokensInfos.add(new AppTokensInfo(parts[0], parts[2], parts[5], debugUrl));
				}
			}

			// debug all apps
			Funcs.sleep(Funcs.randInt(5000, 7000));
			for (AppTokensInfo appTokensInfo : appTokensInfos) {
				FacebookApp facebookApp = new FacebookApp();
				facebookApp.setAccountName(account);
				facebookApp.setAccountPass(password);
				facebookApp.setAppName(appTokensInfo.appName);
				facebookApp.setAppAccessToken(appTokensInfo.appAccessToken);
				try {
					System.out.println(appTokensInfo.debugTokenUrl);
					driver.get(appTokensInfo.debugTokenUrl);
					Funcs.sleep(Funcs.randInt(5000, 7000));
					WebElement extendButton = null;
					List<WebElement> extendButtons = driver.findElementsByTagName("button");
					for (WebElement el : extendButtons) {
						String text = el.getText();
						if (StringUtils.containsIgnoreCase(text, "Extend Access Token")) {
							extendButton = el;
							break;
						}
					}
					if (extendButton != null) {
						extendButton.click();
						System.out.println("extendButton CLICKED");
					} else {
						System.out.println("extendButton NULL");
					}
					Funcs.sleep(5000L);
					List<WebElement> divRoleMain2s = driver.findElements(By.xpath("//div[@role='main']"));
					if (divRoleMain2s != null) {
						if (divRoleMain2s.size() > 0) {
							String text = divRoleMain2s.get(0).getText();
							if (StringUtils.isNotEmpty(text)) {
								String[] parts = text.split("\n");
								for (int i = 0; i < parts.length; i++) {
//									if (parts[i].startsWith("App ID")) {
									if (parts[i].startsWith("Application ID")) {
										String appId = TParser.getOneInGroup(parts[i], "[0-9]{10,}");
										facebookApp.setAppID(appId);
										System.out.println("Application ID: " + appId);
									}
									if (parts[i].startsWith("This new long-lived access token")) {
										String longLiveToken = parts[i + 1];
										facebookApp.setUserAccessToken(longLiveToken);
									}
								}
							}
						} else {
							System.out.println("EMPTY");
						}
					} else {
						System.out.println("extendButton div role main NULL");
					}

					Funcs.sleep(10000L);
					System.out.println("@@@@@@@@@@@");
				} catch (Exception ex) {
					LOG.error(ex.getMessage(), ex);
				}
				facebookApps.add(facebookApp);
			}

		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		} finally {
			Funcs.sleep(10000L);
			if (driver != null) {
				System.out.println("SHUTDOWN");
				driver.quit();
			}
		}
		return facebookApps;
	}

	private static class AppTokensInfo {

		public String appName;
		public String shortAccessToken;
		public String appAccessToken;
		public String debugTokenUrl;

		public AppTokensInfo(String appName, String shortAccessToken, String appAccessToken, String debugTokenUrl) {
			this.appName = appName;
			this.shortAccessToken = shortAccessToken;
			this.appAccessToken = appAccessToken;
			this.debugTokenUrl = debugTokenUrl;
		}

		@Override
		public String toString() {
			return "AppTokensInfo{" + "appName=" + appName + ", shortAccessToken=" + shortAccessToken
				+ ", appAccessToken=" + appAccessToken + ", debugTokenUrl=" + debugTokenUrl + '}';
		}

	}
}
