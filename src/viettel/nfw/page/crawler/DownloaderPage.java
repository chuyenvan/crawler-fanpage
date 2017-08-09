/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viettel.nfw.page.crawler;

import com.jayway.jsonpath.JsonPath;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.timer.Timer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.RootLogger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import viettel.nfw.group.crawler.HttpRequest;
import viettel.nfw.group.crawler.HttpRequestComment;
import viettel.nfw.group.crawler.MasterService;
import viettel.nfw.group.crawler.InputPoller;
import viettel.nfw.social.utils.EngineConfiguration;
import vn.viettel.utils.SimpleTimer;

/**
 *
 * @author hoangvv
 */
public class DownloaderPage implements Runnable {

//	private static final int corePoolSize = EngineConfiguration.get().getInt("number.thread", 16);
//	private static final int maxPoolSize = corePoolSize * 2;
//	private static final long keepAliveTime = 2;
//	private static final LinkedBlockingQueue<Runnable> queueInExecutor = new LinkedBlockingQueue<>();
//	private static final ExecutorService executorService = new ThreadPoolExecutor(
//			corePoolSize,
//			maxPoolSize,
//			keepAliveTime,
//			TimeUnit.MINUTES,
//			queueInExecutor
//	);
	private static final boolean isRunning = true;
	private static final int NUM_THREAD = EngineConfiguration.get().getInt("number.thread", 16);
	private static final Logger logger = RootLogger.getLogger(DownloaderPage.class);
	private static final ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREAD);
	private static final String SAVE_DIR = "tmppage";
	private static final String SAVE_READ = "outputpage";
	private static BlockingQueue queue = new LinkedBlockingQueue();
	private static final String MODE_CONFIG = EngineConfiguration.get().get("mode.config.page", "ON");
//	private static final int SLEEP_TIME_THREAD = EngineConfiguration.get().getInt("time.sleep.thread", 1);
	public static final AtomicLong count = new AtomicLong();
	private static long lastLoadingQueue = 0L;
	
	public DownloaderPage() throws IOException, FileNotFoundException, ParseException {
		File file = new File(SAVE_DIR);
		if (!file.exists()) {
			file.mkdir();
		}
	}
	
	@Override
	public void run() {
		try {
			start();
		} catch (IOException | InterruptedException | ParseException ex) {
			logger.error(ex.getMessage(), ex);
		}
	}
	
	public void start() throws IOException, InterruptedException, FileNotFoundException, ParseException {
		try {
			if (MODE_CONFIG.equals("ON")) {
				while (isRunning) {
					int sizeofQueue = ((ThreadPoolExecutor) executorService).getQueue().size();
					Long current = System.currentTimeMillis();
					if (sizeofQueue == 0 && queue.isEmpty() && current - lastLoadingQueue > 10 * Timer.ONE_SECOND) {
						lastLoadingQueue = current;
						queue.addAll(InputPoller.loadDataPage());
					}
					final String ID = (String) queue.poll();
					if (ID != null) {
						executorService.execute(new taskDownloadPage(ID));
//						logger.info("Size of queue inMem Page " + sizeofQueue);
//						try {
//							future.get(2, TimeUnit.MINUTES);
//						} catch (ExecutionException | TimeoutException ex) {
//							logger.error(ex.getMessage(), ex);
//							future.cancel(true);
//						}
					}
					Thread.sleep(5);
//					File file = new File(SAVE_READ);
//					if (file.listFiles().length > 1000) {
//						Thread.sleep(SLEEP_TIME_THREAD * Timer.ONE_SECOND);
//					}
				}
			} else {
				logger.info("Mode downloader page is off, please turn on in config");
			}
		} catch (InterruptedException e) {
			logger.error(e.getMessage(), e);
		}
	}
	
	private static BlockingQueue loadDataPage() throws FileNotFoundException, IOException, ParseException, InterruptedException {
		BlockingQueue queuePage = new LinkedBlockingQueue();
		logger.info("loadData Page to Queue");
		String response = MasterService.getPage("500"); //get 1000 page from master service
		if (response != null) {
			JSONParser json = new JSONParser();
			JSONArray array = (JSONArray) json.parse(response);
			for (Object array1 : array) {
				JSONObject object = (JSONObject) array1;
				String ID = (String) object.get("page_id");
				boolean isDone = queuePage.add(ID);
				if (!isDone) {
					logger.info(ID + " :is failed");
				}
			}
//			logger.info("Size of queue: " + queueGroup.size());
		} else {
			logger.error("Master Service is failed");
			Thread.sleep(1 * Timer.ONE_MINUTE);
			response = MasterService.getPage("100");
			if (response != null) {
				JSONParser json = new JSONParser();
				JSONArray array = (JSONArray) json.parse(response);
				for (Object array1 : array) {
					JSONObject object = (JSONObject) array1;
					String ID = (String) object.get("page_id");
					boolean isDone = queuePage.add(ID);
					if (!isDone) {
						logger.info(ID + " :is failed");
					}
				}
			} else {
				logger.error("Master Service is falling down!!!");
			}
		}
		
		return queuePage;
	}
	
	private static void saveTimeline(String text, String ID) throws FileNotFoundException, IOException, InterruptedException {
		File folder = new File(SAVE_DIR + "/" + ID);
		folder.mkdirs();
		File folderRead = new File(SAVE_READ);
		folderRead.mkdirs();
		File fileOutput = new File(SAVE_DIR + "/" + ID + "/" + ID + ".timelines.json");
		FileOutputStream fop = null;
		try {
			fop = new FileOutputStream(fileOutput);
			fop.write(text.getBytes());
			fop.flush();
		} catch (IOException ex) {
			logger.error(ex.getMessage(), ex);
		} finally {
			if (fop != null) {
				fop.close();
				fop = null;
			}
		}
		
	}
	
	private static String downloadTimeline(String ID) throws IOException, ParseException, MalformedURLException, InterruptedException {
		try {
			String request = URLBuilder.builderPage(ID);
			HttpRequest httpRequest = new HttpRequest();
			String data = httpRequest.sendGet(request);
			if (data == null) {
				logger.error("Check Page ID is null!! " + ID);
				Thread.sleep(5 * Timer.ONE_SECOND);
				data = httpRequest.sendGet(request);
			}
			return data;
		} catch (IOException | InterruptedException e) {
			logger.error(e.getMessage() + "\t" + ID);
		}
		return null;
	}
	
	private static void getIDPostAndDownloadComment(String data, String ID) throws ParseException, IOException, MalformedURLException, InterruptedException {
		JSONParser json = new JSONParser();
		int index = StringUtils.indexOf(data, "{");
		if (index != -1) {
			data = StringUtils.substring(data, index);
			if (data != null) {
				try {
					JSONObject obj = (JSONObject) json.parse(data);
					if (obj.get("error") == null) {
						JSONArray array = (JSONArray) obj.get("domops");
						String html = ((HashMap) ((JSONArray) array.get(0)).get(3)).get("__html").toString();
						Document doc = Jsoup.parse(html);
						Elements postFulls = doc.select("div.userContentWrapper._5pcr");
						for (Element postFull : postFulls) {
							String postID = postFull.getElementsByAttributeValueContaining("name", "ft_ent_identifier").attr("value");
							int countComment = getCommentCountByPostId(data, postID);
							if (countComment > 0) {
								logger.debug("Number of comment in post " + postID + "\t :" + countComment);
								String feed_context = extractFeedContext(postID, obj);
								String comment = downloadComment(postID, feed_context, countComment);
								logger.debug("download comment of post " + postID + " in timeline of " + ID);
								if (comment != null) {
									saveComment(comment, postID, ID);
								}
								logger.debug("save comment of post " + postID + " in timeline of " + ID);
							}
						}
						
					} else {
						logger.warn("Page isn't existed " + ID);
//						if (MasterService.removePage(ID)) {
//							logger.info("ID " + ID + " has been deleted");
//						}
					}
				} catch (ParseException | IOException e) {
					logger.error("Error in parse comment " + e.getMessage(), e);
				}
				
			}
		}
	}
	
	public static int getCommentCountByPostId(String timelineJson, String postID) {
		List<Map<String, Object>> details = JsonPath.read(timelineJson, "$..feedbacktargets[?(@.entidentifier == '" + postID + "')]");
		if (details.size() > 0) {
			Map<String, Object> detail = details.get(0);
			Integer commentCount = (Integer) detail.get("commentcount");
			return commentCount;
		}
		return 0;
	}
	
	public static String extractFeedContext(String id, JSONObject obj) {
		JSONParser json = new JSONParser();
		try {
			JSONObject obj1 = (JSONObject) obj.get("jsmods");
			JSONArray array = (JSONArray) obj1.get("require");
			for (Object array3 : array) {
				JSONArray array1 = (JSONArray) array3;
				if ("UFIController".equals(array1.get(0).toString())) {
					JSONArray array2 = (JSONArray) array1.get(3);
					JSONObject obj2 = (JSONObject) array2.get(1);
					String feedContext = (String) obj2.get("feedcontext");
					JSONObject obj4 = (JSONObject) json.parse(feedContext);
					if (obj2.get("ftentidentifier").equals(id)) {
						return feedContext;
					}
				}
			}
		} catch (ParseException e) {
			return null;
		}
		return null;
	}
	
	public static String downloadComment(String IDPost, String feed_context, int countComment) throws MalformedURLException, IOException, InterruptedException {
		String url_start = "https://www.facebook.com/ajax/ufi/comment_fetch.php?dpr=1";
		int offset;
		long timeout = 2 * Timer.ONE_SECOND;
		if (countComment > 50) {
			offset = countComment - 50;
		} else {
			offset = 0;
		}
		StringBuilder postData = new StringBuilder();
		postData.append(url_start);
		Map<String, Object> arguments = new LinkedHashMap<>();
		arguments.put("ft_ent_identifier", IDPost);
		arguments.put("offset", offset);
		arguments.put("length", 50);
		arguments.put("orderingmode", "recent_activity");
		arguments.put("feed_context", feed_context);
		arguments.put("__a", "1");
		arguments.put("__dyn", "7AzHK4GgN1t2u6XgmwCCwKAKGzEy4SC11xG3F6xucxu13wmeexZoK6UnG2ibyEjKewExmt0h9VojxCaxnUCu5omyp8swJwnoCiu6Egx62q2m2qm7Q589o8p8Z12");
		for (Map.Entry<String, Object> argument : arguments.entrySet()) {
			if (postData.length() != 0) {
				postData.append('&');
			}
			postData.append(argument.getKey());
			postData.append('=');
			postData.append(argument.getValue());
		}
//		URL url = new URL(postData.toString());
////		System.setProperty("https.proxySet", "true");
////		System.setProperty("https.proxyHost", "203.113.152.1");
////		System.setProperty("https.proxyPort", "7020");
//		HttpURLConnection con = (HttpURLConnection) url.openConnection();
//		con.setRequestMethod("GET");
//		con.setConnectTimeout((int) timeout);
//		con.setRequestProperty("accept-language", "en-US,en;q=0.5");
//		con.setRequestProperty("content-type", "application/x-www-form-urlencoded");
//		con.setRequestProperty("user-agent", USER_AGENT);
//		con.setRequestProperty("cookie", "datr=MajjV22L31tpAsCK1rm3_9_9; sb=PqjjVxThD6NWW-6FjXbilHAW; locale=en_GB; fr=0vcOpGNqACTnKXSPC..BX_0YR.pV.AAA.0.0.BX_0YR.AWXv6yHq");
//		// For POST only - END
//		try {
//			int responseCode = con.getResponseCode();
////		System.out.println("GET Response Code :: " + responseCode);
//			if (responseCode == HttpURLConnection.HTTP_OK) {
//				StringBuilder response;
//				InputStreamReader input = null;
//				BufferedReader buffer = null;
//				try {
//					input = new InputStreamReader(con.getInputStream());
//					buffer = new BufferedReader(input);
//					String inputLine;
//					response = new StringBuilder();
//					while ((inputLine = buffer.readLine()) != null) {
//						response.append(inputLine);
//					}
//				} finally {
//					if (buffer != null || input != null) {
//						buffer.close();
//						input.close();
//						buffer = null;
//						input = null;
//					}
//				}
//				String s = response.toString();
//				return s;
//			} else {
//				return null;
//			}
//		} catch (IOException e) {
//			logger.error("Error in send request to and try again: " + e.getMessage(), e);
//			Thread.sleep(10 * Timer.ONE_SECOND);
//			return downloadComment(IDPost, feed_context, countComment);
//		}
		logger.info("Link download Comment: " + postData.toString());
		String comment = HttpRequestComment.sendGet(postData.toString());
		if (comment != null) {
			return comment;
		} else {
			Thread.sleep(2 * Timer.ONE_SECOND);
			comment = HttpRequestComment.sendGet(postData.toString());
			if (comment != null) {
				return comment;
			} else {
				logger.error("Check Post: " + IDPost);
				return null;
			}
		}
	}
	
	private static void saveComment(String comment, String IDPost, String ID) throws FileNotFoundException, IOException {
		if (comment != null) {
			File fileOutput = new File(SAVE_DIR + "/" + ID + "/" + ID + "." + IDPost + ".comment.json");
			FileOutputStream fop = null;
			try {
				fop = new FileOutputStream(fileOutput);
				fop.write(comment.getBytes());
				fop.flush();
				fop.close();
			} catch (IOException ex) {
				logger.error(ex.getMessage(), ex);
			} finally {
				if (fop != null) {
					fop.close();
					fop = null;
				}
			}
		}
	}
	
	public static void main(String[] args) throws FileNotFoundException, IOException, ParseException, MalformedURLException, InterruptedException {
//		JSONParser json = new JSONParser();
//		FileInputStream fisTargetFile = new FileInputStream(new File("H:/error.json"));
//		String data = IOUtils.toString(fisTargetFile, "UTF-8");
//		int index = StringUtils.indexOf(data, "{");
//		data = StringUtils.substring(data, index);
//		JSONObject obj = (JSONObject) json.parse(data);
//		if (obj.get("error") == null) {
//			JSONArray array = (JSONArray) obj.get("domops");
//			String html = ((HashMap) ((JSONArray) array.get(0)).get(3)).get("__html").toString();
//			Document doc = Jsoup.parse(html);
//			Elements postFulls = doc.select("div.userContentWrapper._5pcr");
//			for (Element postFull : postFulls) {
//				String postID = postFull.getElementsByAttributeValueContaining("name", "ft_ent_identifier").attr("value");
//
//			}
//		} else {
//			System.out.println(obj.get("error"));
//		
		String data = downloadTimeline("195572877178527");
		getIDPostAndDownloadComment(data, "195572877178527");
	}
	
	class taskDownloadPage implements Runnable {
		
		private final String ID;
		
		public taskDownloadPage(String ID) {
			this.ID = ID;
		}
		
		@Override
		public void run() {
			try {
				SimpleTimer timer = new SimpleTimer();
				long i = count.getAndAdd(1);
				String text = null;
				text = downloadTimeline(ID);
				logger.info("Time to download Page sucess: " + timer.getTimeAndReset());
				logger.info("downloaded Page " + ID);
				
				if (text != null) {
					saveTimeline(text, ID);
					logger.info("Time to save Page timeline sucess: " + timer.getTimeAndReset());
					getIDPostAndDownloadComment(text, ID);
					logger.info("Time to download comment Page: " + timer.getTimeAndReset());
				}
				File file_save = new File((SAVE_READ + "/" + ID));
				File file_temp = new File((SAVE_DIR + "/" + ID));
				if (!file_save.exists()) {
					try {
						if (file_temp.exists()) {
							Files.move(new File(SAVE_DIR + "/" + ID).toPath(), new File(SAVE_READ + "/" + ID).toPath(), StandardCopyOption.REPLACE_EXISTING);
						}
					} catch (IOException ex) {
						logger.error(ex.getMessage(), ex);
					}
				} else {
					logger.info(ID + " has existed in SaveFolder. Delete in folder temp");
					if (file_temp.exists()) {
						try {
							FileUtils.forceDelete(file_temp);
						} catch (IOException ex) {
							logger.error(ex.getMessage(), ex);
						}
					}
				}
				logger.info("Time to cut and save folder Page: " + timer.getTimeAndReset());
				if (i % 1000 == 0) {
					logger.info("Page crawler: " + i);
				}
			} catch (IOException | InterruptedException | ParseException e) {
				logger.error(e.getMessage(), e);
			}
		}
		
	}
}
