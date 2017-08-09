package viettel.nfw.social.tool;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.UnreachableBrowserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.TParser;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class SiteFacebookUsingPhantomjsFinder implements IFinder {

	private static final Logger LOG = LoggerFactory.getLogger(SiteFacebookUsingPhantomjsFinder.class);
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.0";
	private static final String SITE = "https://www.facebook.com/";
	private final Proxy proxy;

	public SiteFacebookUsingPhantomjsFinder(Proxy proxy) {
		this.proxy = proxy;
	}

	@Override
	public String getId(String facebookProfileUrl) {

		String proxyString = null;
		if (proxy != null) {
			proxyString = proxy.address().toString().replace("/", "");
		}

		PhantomJSDriver driver = startDriver(proxyString, USER_AGENT);
		Funcs.sleep(600);

		try {
			driver.get(facebookProfileUrl);
		} catch (TimeoutException ex) {
			driver.navigate().refresh();
		} catch (UnreachableBrowserException ex) {
			stopDriver(driver);
			return null;
		}

		Funcs.sleep(600);

		String pageTitle = driver.getTitle();
		LOG.info("Title {}", pageTitle);

		// check redirect to security check page
		if (StringUtils.contains(pageTitle, "Yêu cầu kiểm tra bảo mật")
			|| StringUtils.contains(pageTitle, "Security Check Required")
			|| StringUtils.contains(pageTitle, "Page Not Found")
			|| StringUtils.contains(pageTitle, "Welcome to Facebook")
			|| StringUtils.contains(pageTitle, "Chào mừng bạn đến với Facebook")
			|| StringUtils.equalsIgnoreCase(pageTitle, "Facebook")) {
			LOG.warn("Cannot access this profile - {} - title {}", facebookProfileUrl, pageTitle);
			stopDriver(driver);
			return null;
		}

		// get page source and parse
		String body = driver.getPageSource();
		Funcs.sleep(600);
		stopDriver(driver);

		String id = null;
		if (StringUtils.isNotEmpty(body)) {
			Document doc = null;
			try {
				doc = Jsoup.parse(body);
			} catch (Exception ex) {
				LOG.error("Error while parsing body", ex);
			}
			if (doc != null) {
				Elements metaElements = doc.select("meta[property=\"al:android:url\"]");
				if (!metaElements.isEmpty()) {
					Element metaElement = metaElements.get(0);
					String content = metaElement.attr("content");
					if (StringUtils.isNotEmpty(content)) {
						id = TParser.getOneInGroup(content, "[0-9]{9,}");
					}
				}
				Element mainTimelineElements = doc.getElementById("pagelet_timeline_main_column");
				if (mainTimelineElements != null) {
					String datagt = mainTimelineElements.attr("data-gt");
					if (StringUtils.isNotEmpty(datagt)) {
						id = TParser.getOneInGroup(datagt, "[0-9]{9,}");
					}
				}
			}
		}

		return id;
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

		// set language for Facebook
		try {
			try {
				driver.get(SITE);
			} catch (TimeoutException ex) {
				driver.navigate().refresh();
			}

			List<WebElement> languageATags = driver.findElements(By.xpath("//a[@title]"));
			for (WebElement languageATag : languageATags) {
				String language = languageATag.getAttribute("title");
				if (StringUtils.isEmpty(language)) {
					language = languageATag.getText();
				}
				if (StringUtils.contains(language, "English (US)")) {
					// By click this link, language will set to English in cookies
					languageATag.click();
					LOG.info("[startDriver] DONE set Facebook to English");
					break;
				}
			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}

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
