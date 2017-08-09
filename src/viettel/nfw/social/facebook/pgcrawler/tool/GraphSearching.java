package viettel.nfw.social.facebook.pgcrawler.tool;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Proxy;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.nigma.engine.web.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.graph.FacebookAppManager;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.utils.AsyncFileWriter;
import viettel.nfw.social.utils.Funcs;
import vn.itim.detector.InputType;
import vn.itim.detector.Language;
import vn.itim.detector.LanguageDetector;
import vn.viettel.social.utils.HttpResponseInfo;

/**
 *
 * @author duongth5
 */
public class GraphSearching {

	private static final Logger LOG = LoggerFactory.getLogger(GraphSearching.class);

	public static void main(String[] args) {
		String facebookAppsFilename = args[0];
		String keywordsFilename = args[1];
		String searchTypesFilename = args[2];

		if (StringUtils.isNotEmpty(facebookAppsFilename) && StringUtils.isNotEmpty(keywordsFilename) && StringUtils.isNotEmpty(searchTypesFilename)) {
			new GraphSearching(facebookAppsFilename, keywordsFilename, searchTypesFilename).run();
		} else {
			LOG.info("Invalid params!");
		}

//		filterResult();
	}

	private static void filterResult() {
		String dirStr = "D:\\git\\social-actions\\input\\updatenews\\a";
		File dir = new File(dirStr);
		File[] files = dir.listFiles();
		Set<ResultObject> resultObjects = new HashSet<>();
		for (File file : files) {
			try {
				try (BufferedReader br = new BufferedReader(new FileReader(file))) {
					String line;
					while ((line = br.readLine()) != null) {
						line = line.trim();
						if (StringUtils.isNotEmpty(line) && !StringUtils.startsWith(line, "#")) {
							if (StringUtils.startsWith(line, "group") || StringUtils.startsWith(line, "page")) {
								String[] parts = line.split("\t");
								if (parts.length == 5) {
									resultObjects.add(new ResultObject(parts[1], parts[2], parts[0], parts[4], parts[3]));
								}
							}
						}
					}
				}
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}

		System.out.println("size " + resultObjects.size());
		Set<ResultObject> pages = new HashSet<>();
		Set<ResultObject> publicGroups = new HashSet<>();
		Set<ResultObject> privateGroups = new HashSet<>();

		Set<ResultObject> muaBanChos = new HashSet<>();
		Set<ResultObject> foreigns = new HashSet<>();
		LanguageDetector languageDetector = new LanguageDetector();
		for (ResultObject resultObject : resultObjects) {
//			int check = checkText(languageDetector, resultObject.name);
			int check = 1;
			switch (check) {
				case 0:
					foreigns.add(resultObject);
					break;
				case 2:
					muaBanChos.add(resultObject);
					break;
				case 1:
					if (resultObject.type.equals("group")) {
						if (resultObject.privacy.equalsIgnoreCase("OPEN")) {
							publicGroups.add(resultObject);
						} else if (resultObject.privacy.equalsIgnoreCase("CLOSED")) {
							privateGroups.add(resultObject);
						}
					} else if (resultObject.type.equals("page")) {
						pages.add(resultObject);
					}
					break;
				default:
					break;
			}
		}

		Set<ResultObject> trashPage = new HashSet<>();
		Set<ResultObject> trashGroup = new HashSet<>();
		for (ResultObject foreign : foreigns) {
			if (foreign.type.equals("group")) {
				if (foreign.privacy.equalsIgnoreCase("OPEN")) {
					trashGroup.add(foreign);
				}
			} else if (foreign.type.equals("page")) {
				trashPage.add(foreign);
			}
		}

		for (ResultObject muaBanCho : muaBanChos) {
			if (muaBanCho.type.equals("group")) {
				if (muaBanCho.privacy.equalsIgnoreCase("OPEN")) {
					trashGroup.add(muaBanCho);
				}
			} else if (muaBanCho.type.equals("page")) {
				trashPage.add(muaBanCho);
			}
		}

		System.out.println("########## PUBLIC PAGE");
		for (ResultObject page : pages) {
			System.out.println(page.print());
		}

		System.out.println("########## PUBLIC GROUP");
		for (ResultObject publicGroup : publicGroups) {
			System.out.println(publicGroup.print());
		}
		
		System.out.println("########## PRIVATE GROUP");
		for (ResultObject privateGroup : privateGroups) {
			System.out.println(privateGroup.print());
		}
	}

	private static int checkText(LanguageDetector languageDetector, String text) {
		Language language = languageDetector.detect(text, null, InputType.PLAIN);
		if (language.equals(Language.VIETNAMESE)) {
			if (text.toLowerCase().contains("mua") || text.toLowerCase().contains("bán") || text.toLowerCase().contains("chợ")) {
				// shopping
				return 2;
			} else {
				// vietnamese
				return 1;
			}
		} else {
			// foreign
			return 0;
		}
	}

	private final String facebookAppsFilename;
	private final String keywordsFilename;
	private final String searchTypesFilename;
	public static BlockingQueue<SearchObject> searchObjectQueue = new ArrayBlockingQueue<>(1000);
	public static BlockingQueue<ResultObject> resultObjectQueue = new ArrayBlockingQueue<>(3000);
	public static AtomicBoolean stopPut = new AtomicBoolean(false);
	public static AtomicBoolean stopDownload = new AtomicBoolean(false);

	public GraphSearching(String facebookAppsFilename, String keywordsFilename, String searchTypesFilename) {
		this.facebookAppsFilename = facebookAppsFilename;
		this.keywordsFilename = keywordsFilename;
		this.searchTypesFilename = searchTypesFilename;
	}

	public void run() {
		// get facebook apps and load to downloader
		List<FacebookApp> facebookApps = FacebookAppManager.readAppInfoFromFile(facebookAppsFilename);
		new Thread(new DownloaderManager(facebookApps), "DownloaderManager").start();

		// get search object and load to queue
		Set<SearchObject> searchObjects = readAndShufferObjectSearch();
		LOG.info("SearchObjects size {}", searchObjects.size());
		new Thread(new PutSearchObjectsToQueue(searchObjects), "PutSearchObjectsToQueue").start();

		new Thread(new WriteToFile(), "WriteToFile").start();
	}

	private static class WriteToFile implements Runnable {

		private static AsyncFileWriter afwDataWriter;

		@Override
		public void run() {
			try {
				String filename = "page-group-" + String.valueOf(System.currentTimeMillis());
				afwDataWriter = new AsyncFileWriter(new File(filename));
				afwDataWriter.open();
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
			}
			while (resultObjectQueue.size() > 0 || !stopDownload.get()) {
				ResultObject resultObject = resultObjectQueue.poll();
				if (resultObject != null) {
					StringBuilder sb = new StringBuilder();
					sb.append(resultObject.type).append("\t");
					sb.append(resultObject.id).append("\t");
					sb.append(resultObject.name).append("\t");
					sb.append(resultObject.privacy).append("\t");
					sb.append(resultObject.category).append("\n");
					afwDataWriter.append(sb.toString());
				}
			}
			afwDataWriter.close();
			LOG.info("DONE_WRITE_RULES");
		}
	}

	private static class DownloaderManager implements Runnable {

		private final BlockingQueue<Downloader> downloaderPool;
		private final List<FacebookApp> facebookApps;
		private static final int NUMBER_THREAD = 10;
		private static final int NUMBER_RETRY = 3;
		private static final int MAX_PENDING_FORSERIALIZATION_TASKS = 30;
		private static final long TASK_TIMEOUT = 10 * 60 * 1000L;
		private static final long MAX_TIME_SLEEP_BETWEEN_QUERIES = 2 * 60 * 1000L; // 2 minutes
		//private static final Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("192.168.4.13", 3128));
		private static final Proxy PROXY = null;

		public DownloaderManager(List<FacebookApp> facebookApps) {
			this.facebookApps = facebookApps;
			this.downloaderPool = new ArrayBlockingQueue<>(facebookApps.size());
		}

		@Override
		public void run() {
			// load to downloader pool
			for (FacebookApp facebookApp : facebookApps) {
				try {
					downloaderPool.put(new Downloader(facebookApp, -1));
				} catch (InterruptedException ex) {
					LOG.info(ex.getMessage(), ex);
				}
			}
			LOG.info("DONE loading downloader to pool");

			ExecutorService pool = Executors.newFixedThreadPool(NUMBER_THREAD, new ThreadFactory() {

				private final AtomicLong threadNumber = new AtomicLong(1);

				@Override
				public Thread newThread(Runnable r) {
					if (threadNumber.get() > 1000L) {
						threadNumber.set(1);
					}
					String threadName = "Downloader" + "-" + String.valueOf(threadNumber.getAndIncrement());
					Thread t = new Thread(r, threadName);
					return t;
				}
			});

			Map<Future, Long> taskInWork = new HashMap<>();
			int pendingForSerializationTasks = 0;
			int totalTaskTimeout = 0;
			while (searchObjectQueue.size() > 0 || !stopPut.get()) {

				// check pending tasks
				int tTaskTimeout = 0;
				for (Iterator<Map.Entry<Future, Long>> it = taskInWork.entrySet().iterator(); it.hasNext();) {
					Map.Entry<Future, Long> mapElement = it.next();
					Future f = mapElement.getKey();
					Long startTime = mapElement.getValue();
					if (f.isDone() || f.isCancelled()) {
						it.remove();
						--pendingForSerializationTasks;
					} else if (System.currentTimeMillis() - startTime > TASK_TIMEOUT) { // check task timeout
						tTaskTimeout++;
					}
				}
				totalTaskTimeout = tTaskTimeout;

				if (pendingForSerializationTasks < MAX_PENDING_FORSERIALIZATION_TASKS) {
					final SearchObject searchObject = searchObjectQueue.poll();
					if (searchObject != null) {
						Future ft = pool.submit(new Runnable() {
							@Override
							public void run() {
								String query = searchObject.query;
								String type = searchObject.type;
								Downloader downloader = null;
								try {
									downloader = downloaderPool.take();
									LOG.info("BORROW_APP_{}", downloader.appInfo.getAppID());
									long lastQueryTime = downloader.lastQueryTime;
									if (lastQueryTime == -1 || (System.currentTimeMillis() - lastQueryTime) > MAX_TIME_SLEEP_BETWEEN_QUERIES) {
										// do query
										String accessToken = downloader.appInfo.getUserAccessToken();
										String queryUrl = null;
										try {
											queryUrl = generateQueryUrl(query, type, accessToken);
										} catch (UnsupportedEncodingException ex) {
											LOG.error(ex.getMessage(), ex);
										}
										if (StringUtils.isNotEmpty(queryUrl)) {
											List<String> queryUrls = new ArrayList<>();
											queryUrls.add(queryUrl);
											int i = 0;
											int foundIdCount = 0;
											while (queryUrls.size() > 0) {
												String nextUrl = queryUrls.remove(0);
												// only get 3 pages
												if (i > 2) {
													break;
												}
												for (int j = 0; j < NUMBER_RETRY; j++) {
													HttpResponseInfo response = vn.viettel.social.utils.Utils.singleGet(nextUrl, PROXY);
													if (response.getStatus() == 200) {
														String responseBody = response.getBody();
														if (StringUtils.isNotEmpty(responseBody)) {
															Pair<Set<ResultObject>, String> parsedData = parsing(responseBody, type);
															Set<ResultObject> resultObjects = parsedData.first;
															if (resultObjects != null) {
																for (ResultObject resultObject : resultObjects) {
																	try {
																		resultObjectQueue.put(resultObject);
																		foundIdCount++;
																	} catch (InterruptedException e) {
																		LOG.error(e.getMessage(), e);
																	}
																}
															}
															String nextPageUrl = parsedData.second;
															if (StringUtils.isNotEmpty(nextPageUrl)) {
																queryUrls.add(nextPageUrl);
															}
															break;
														}
													} else {
														int ransec = Funcs.randInt(5, 15);
														Funcs.sleep(ransec * 1000L);
													}
												}
												i++;
												if (queryUrls.size() > 0) {
													int ransec = Funcs.randInt(40, 80);
													Funcs.sleep(ransec * 1000L);
												}
											}
											LOG.info("DONE_QUERY {} found {}", searchObject.toString(), foundIdCount);
											downloader.lastQueryTime = System.currentTimeMillis();
										}
									} else {
//										LOG.info("APP_NEED_SLEEP {} last query time is {}", downloader.appInfo.getAppID(), new Date(lastQueryTime).toString());
										// put back search object
										searchObjectQueue.put(searchObject);
									}
								} catch (InterruptedException ex) {
									LOG.error(ex.getMessage(), ex);
								} finally {
									if (downloader != null) {
										// put back download to pool
										LOG.info("RETURN_APP_{}", downloader.appInfo.getAppID());
										try {
											downloaderPool.put(downloader);
										} catch (InterruptedException ex) {
											LOG.error(ex.getMessage(), ex);
										}
									} else {
										LOG.warn("Downloader is null!");
									}
								}
							}
						});
						taskInWork.put(ft, System.currentTimeMillis());
						++pendingForSerializationTasks;
					}
				} else {
					Funcs.sleep(5);
				}
			}

			LOG.info("WAITING_BEFOR_CALLING_SHUTDOWN_TASK_DOWNLOADING ...");
			Funcs.sleep(60 * 1000L); // wait 1 minute
			LOG.info("CALLING_SHUTDOWN_TASK_DOWNLOADING ...");
			pool.shutdown();
			LOG.info("AWAIT_TERMINATION_TASK_DOWNLOADING ...");
			try {
				pool.awaitTermination(5, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				LOG.error(e.getMessage(), e);
			}
			Funcs.sleep(60 * 1000L);
			stopDownload.set(true);

		}
	}

	private static Pair<Set<ResultObject>, String> parsing(String jsonStr, String type) {
		Set<ResultObject> resultObjects = new HashSet<>();
		String nextPageUrl = null;

		JsonParser jsonParser = new JsonParser();
		JsonElement jsonElement = jsonParser.parse(jsonStr);
		JsonObject jsonObject = jsonElement.getAsJsonObject();

		try {
			JsonElement dataElement = jsonObject.get("data");
			if (dataElement != null) {
				JsonArray dataJsonArray = dataElement.getAsJsonArray();
				for (int i = 0; i < dataJsonArray.size(); i++) {
					JsonElement itemElement = dataJsonArray.get(i);
					if (itemElement != null) {
						JsonObject itemObject = itemElement.getAsJsonObject();
						String id = null;
						String name = null;
						String category = null;
						String privacy = null;

						JsonElement idElement = itemObject.get("id");
						if (idElement != null) {
							id = idElement.getAsString();
						}

						JsonElement nameElement = itemObject.get("name");
						if (nameElement != null) {
							name = nameElement.getAsString();
						}

						JsonElement categoryElement = itemObject.get("category");
						if (categoryElement != null) {
							category = categoryElement.getAsString();
						}

						JsonElement privacyElement = itemObject.get("privacy");
						if (privacyElement != null) {
							privacy = privacyElement.getAsString();
						}

						if (StringUtils.isNotEmpty(id)) {
							resultObjects.add(new ResultObject(id, name, type, category, privacy));
						}
					}

				}
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
			LOG.error("JSON_ERROR {}", jsonStr);
		}

		try {
			JsonElement pagingElement = jsonObject.get("paging");
			if (pagingElement != null) {
				JsonObject pagingJsonObject = pagingElement.getAsJsonObject();
				JsonElement nextElement = pagingJsonObject.get("next");
				if (nextElement != null) {
					nextPageUrl = nextElement.getAsString();
				}
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
			LOG.error("JSON_ERROR {}", jsonStr);
		}

		return new Pair<>(resultObjects, nextPageUrl);
	}

	private static class ResultObject {

		public String id;
		public String name;
		public String type;
		public String category;
		public String privacy;

		public ResultObject(String id, String name, String type, String category, String privacy) {
			this.id = id;
			this.name = name;
			this.type = type;
			this.category = category;
			this.privacy = privacy;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof ResultObject)) {
				return false;
			}
			if (obj == this) {
				return true;
			}

			ResultObject rhs = (ResultObject) obj;
			return new EqualsBuilder().
				// if deriving: appendSuper(super.equals(obj)).
				append(id, rhs.id).
				append(type, rhs.type).
				isEquals();
		}

		@Override
		public int hashCode() {
			return new HashCodeBuilder(17, 31). // two randomly chosen prime numbers
				// if deriving: appendSuper(super.hashCode()).
				append(id).
				append(type).
				toHashCode();
		}

		public String print() {
			return type + "\t" + id + "\t" + name;
		}

	}

	private static final String FORMAT_GRAPH_QUERY = "https://graph.facebook.com/search?q=%s&type=%s&access_token=%s"; // query, type, access_token

	private static String generateQueryUrl(String query, String type, String accessToken) throws UnsupportedEncodingException {
		String url = String.format(FORMAT_GRAPH_QUERY,
			URLEncoder.encode(query, "UTF-8"),
			URLEncoder.encode(type, "UTF-8"),
			URLEncoder.encode(accessToken, "UTF-8"));
		LOG.info("URL {}", url);
		return url;
	}

	private static class Downloader {

		public FacebookApp appInfo;
		public long lastQueryTime;

		public Downloader(FacebookApp appInfo, long lastQueryTime) {
			this.appInfo = appInfo;
			this.lastQueryTime = lastQueryTime;
		}

	}

	private static class PutSearchObjectsToQueue implements Runnable {

		private final Set<SearchObject> searchObjects;

		public PutSearchObjectsToQueue(Set<SearchObject> searchObjects) {
			this.searchObjects = searchObjects;
		}

		@Override
		public void run() {
			if (searchObjects != null) {
				LOG.info("Start pushing search object");
				for (SearchObject searchObject : searchObjects) {
					try {
						searchObjectQueue.put(searchObject);
					} catch (InterruptedException ex) {
						LOG.error(ex.getMessage(), ex);
					}
				}
			} else {
				LOG.warn("Search objects is null!");
			}

			try {
				Thread.sleep(60 * 1000L);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
			stopPut.set(true);
			LOG.info("SET_STOP_PUT true");
		}

	}

	private Set<SearchObject> readAndShufferObjectSearch() {
		Set<SearchObject> searchObjects = new HashSet<>();
		// read data
		Set<String> keywords = readList(keywordsFilename);
		Set<String> searchTypes = readList(searchTypesFilename);
		// shuffer
		for (String searchType : searchTypes) {
			for (String keyword : keywords) {
				searchObjects.add(new SearchObject(keyword, searchType));
			}
		}
		return searchObjects;
	}

	private static Set<String> readList(String filename) {
		Set<String> result = new HashSet<>();

		File file = new File(filename);
		try {
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (StringUtils.isNotEmpty(line)) {
						result.add(line);
					}
				}
			}
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}
		return result;
	}

	private static class SearchObject {

		public String query;
		public String type;

		public SearchObject(String query, String type) {
			this.query = query;
			this.type = type;
		}

		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof SearchObject)) {
				return false;
			}
			if (obj == this) {
				return true;
			}

			SearchObject rhs = (SearchObject) obj;
			return new EqualsBuilder().
				// if deriving: appendSuper(super.equals(obj)).
				append(query, rhs.query).
				append(type, rhs.type).
				isEquals();
		}

		@Override
		public int hashCode() {
			return new HashCodeBuilder(17, 31). // two randomly chosen prime numbers
				// if deriving: appendSuper(super.hashCode()).
				append(query).
				append(type).
				toHashCode();
		}

		@Override
		public String toString() {
			return type + "::" + query;
		}
	}
}
