package viettel.nfw.social.facebook.pgcrawler.tool;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
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
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.facebook.nologin.RunNoLogin;
import viettel.nfw.social.utils.AsyncFileWriter;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.Funcs;
import vn.itim.detector.InputType;
import vn.itim.detector.Language;
import vn.itim.detector.LanguageDetector;
import vn.viettel.utils.CustomizedFixedThreadPool;

/**
 *
 * @author duongth5
 */
public class QuickVisitPageWithPhantomjs {

	public static ArrayBlockingQueue<String> idsQueue;
	public static ArrayBlockingQueue<String> resultsQueue = new ArrayBlockingQueue<>(30000);
//	public static Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("203.113.152.12", 3333));
	public static Proxy proxy = null;

	public static void main(String[] args) throws IOException {

		String filename = args[0];
		List<String> ids = FileUtils.readList(new File(filename));
		int idsSize = ids.size();
		idsQueue = new ArrayBlockingQueue<>(idsSize + 100);
		idsQueue.addAll(ids);

		final LanguageDetector languageDetector = new LanguageDetector();
		CustomizedFixedThreadPool pool = new CustomizedFixedThreadPool(70, 70, "Downloader");

		for (int i = 0; i < 70; i++) {
			pool.execute(new Runnable() {

				@Override
				public void run() {
					while (idsQueue.size() > 0) {
						List<String> toCrawlIds = new ArrayList<>();
						while (toCrawlIds.size() < 10) {
							String toCrawlId = idsQueue.poll();
							if (StringUtils.isNotEmpty(toCrawlId)) {
								toCrawlIds.add(toCrawlId);
							} else {
								break;
							}
						}
						QuickVisitPageWithPhantomjs job = new QuickVisitPageWithPhantomjs(languageDetector, proxy);
						PhantomJSDriver driver = job.startDriver(job.getProxyString(), USER_AGENT);
						for (String toCrawlId : toCrawlIds) {
							String url = "https://www.facebook.com/" + toCrawlId;
							String result = job.quickVisitPage(driver, toCrawlId, url);
							if (StringUtils.isNotEmpty(result)) {
								try {
									resultsQueue.put(result);
								} catch (InterruptedException ex) {
									LOG.error(ex.getMessage(), ex);
								}
							}
						}
						job.stopDriver(driver);
					}
				}
			});
		}

		// write to file
		AsyncFileWriter afwCrawledUrls = new AsyncFileWriter(new File(filename + ".result.txt"));
		afwCrawledUrls.open();
		while (true) {
			try {
				String result = resultsQueue.poll();
				if (StringUtils.isEmpty(result)) {
					Thread.sleep(10);
				} else {
					afwCrawledUrls.append(result + "\n");
				}
			} catch (Exception ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
	}

	private static final Logger LOG = LoggerFactory.getLogger(QuickVisitPageWithPhantomjs.class);
	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.0";
	private static final String SITE = "https://www.facebook.com/";
	private String proxyString = null;
	private final LanguageDetector languageDetector;

	public QuickVisitPageWithPhantomjs(LanguageDetector languageDetector, Proxy proxy) {
		this.languageDetector = languageDetector;
		if (proxy != null) {
			proxyString = proxy.address().toString().replace("/", "");
		}
	}

	public String getProxyString() {
		return proxyString;
	}

	public String quickVisitPage(PhantomJSDriver driver, String pageID, String facebookPageUrl) {
		StringBuilder sbResult = new StringBuilder();
		sbResult.append(pageID).append("\t");

		try {
			driver.get(facebookPageUrl);
		} catch (TimeoutException ex) {
			driver.navigate().refresh();
		} catch (UnreachableBrowserException ex) {
			sbResult.append("ERROR_BROWSER");
			return sbResult.toString();
		}

		Funcs.sleep(100);

		String pageTitle = driver.getTitle();
		LOG.info("Title {}", pageTitle);
		sbResult.append(pageTitle).append("\t");

		// check redirect to security check page
		if (StringUtils.contains(pageTitle, "Yêu cầu kiểm tra bảo mật")
				|| StringUtils.contains(pageTitle, "Security Check Required")
				|| StringUtils.contains(pageTitle, "Page Not Found")
				|| StringUtils.contains(pageTitle, "Welcome to Facebook")
				|| StringUtils.contains(pageTitle, "Chào mừng bạn đến với Facebook")
				|| StringUtils.equalsIgnoreCase(pageTitle, "Facebook")) {
			LOG.warn("Cannot access this profile - {} - title {}", facebookPageUrl, pageTitle);
			return sbResult.toString();
		}

		// get current url
		String currentUrl = driver.getCurrentUrl();
		sbResult.append(currentUrl).append("\t");

		try {
			ServiceOutlinks.addCrawledUrl("NO-LOGIN", currentUrl);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}

		// get page source and parse
		String body = driver.getPageSource();
		Funcs.sleep(700);

		// parsing: ID, 
		String newId = null;
		String likes = null;
		String pageType = null;
		String timelineBody = null;
		if (StringUtils.isNotEmpty(body)) {
			Document doc = null;
			try {
				doc = Jsoup.parse(body);
			} catch (Exception ex) {
				LOG.error("Error while parsing body", ex);
			}
			if (doc != null) {
				Elements divIdElements = doc.select("div[id^=PageHeaderPageletController]");
				if (!divIdElements.isEmpty()) {
					Element divIdElement = divIdElements.get(0);
					String divID = divIdElement.attr("id");
					if (StringUtils.isNotEmpty(divID)) {
						newId = divID.replace("PageHeaderPageletController_", "");
					}
				}
				Element likesCountElement = doc.getElementById("PagesLikesCountDOMID");
				if (likesCountElement != null) {
					String elValue = likesCountElement.text();
					if (StringUtils.isNotEmpty(elValue)) {
						likes = elValue.replace("likes", "").replace("like", "")
								.replace("lượt thích", "")
								.replace(",", "").replace(".", "").trim();
					}
				}
				if (StringUtils.contains(currentUrl, "/places/")) {
					pageType = "REMOVE";
				} else {
					Elements detectPageTypeElememts = doc.select("div[id^=VertexDisclaimerSection]");
					if (!detectPageTypeElememts.isEmpty()) {
						Element detectPageTypeElement = detectPageTypeElememts.get(0);
						String textInElement = detectPageTypeElement.text();
						if (StringUtils.containsIgnoreCase(textInElement, "This Page is automatically generated based on what Facebook users are interested in, and not affiliated with or endorsed by anyone associated with the topic.")
								|| StringUtils.containsIgnoreCase(textInElement, "Trang này được tự động tạo dựa trên những gì người dùng Facebook quan tâm và không có liên kết hoặc tán thành với bất kỳ ai liên quan đến chủ đề này.")) {
							pageType = "PAGE_AUTOGEN";
						} else {
							pageType = "PAGE";
						}
					} else {
						pageType = "PAGE";
					}
				}
				Elements timelineIdElements = doc.select("div[id^=PagePostsSectionPagelet]");
				if (!timelineIdElements.isEmpty()) {
					Element timelineIdElement = timelineIdElements.get(0);
					timelineBody = timelineIdElement.text();
				}
			}
		}
		sbResult.append(pageType).append("\t");
		sbResult.append(likes).append("\t");
		sbResult.append(newId).append("\t");

		Language pageNameLanguage = Language.UNKNOWN;
		Language pageTimelineLanguage = Language.UNKNOWN;
		if (StringUtils.isNotEmpty(pageTitle)) {
			pageNameLanguage = languageDetector.detect(pageTitle, null, InputType.PLAIN);
		}
		if (StringUtils.isNotEmpty(timelineBody)) {
			pageTimelineLanguage = languageDetector.detect(timelineBody, null, InputType.PLAIN);
		}

		sbResult.append(pageNameLanguage.getShortName()).append("\t");
		sbResult.append(pageTimelineLanguage.getShortName());

		return sbResult.toString();
	}

	public PhantomJSDriver startDriver(String proxyString, String userAgent) {
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

	public void stopDriver(PhantomJSDriver driver) {
		try {
			driver.quit();
		} catch (Exception ex) {
			LOG.error("Error in Stop Driver. Maybe, it's already dead!", ex);
		}
	}
}
