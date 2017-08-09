//package viettel.nfw.social.common;
//
//import java.io.File;
//import java.io.IOException;
//import java.io.UnsupportedEncodingException;
//import java.net.URLEncoder;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//import java.util.concurrent.ArrayBlockingQueue;
//import java.util.concurrent.BlockingQueue;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//import java.util.concurrent.atomic.AtomicLong;
//import org.apache.commons.lang3.StringUtils;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import viettel.nfw.social.utils.Constant;
//import viettel.nfw.social.utils.FileUtils;
//import viettel.nfw.social.utils.TCrawler;
//import viettel.nfw.social.utils.Funcs;
//import vn.viettel.utils.CustomizedFixedThreadPool;
//import vn.viettel.utils.SimpleTimer;
//
///**
// *
// * @author duongth5
// */
//public class PushGoogleTwitterUrls {
//
//	private static final Logger LOG = LoggerFactory.getLogger(PushGoogleTwitterUrls.class);
//
//	private static String HOST = "";
//
//	static {
//		ApplicationConfiguration.getInstance().initilize(Constant.COMMON_CONF_FILE_PATH);
//		String masterHostname = ApplicationConfiguration.getInstance().getConfiguration("server.master.hostname");
//		String masterPort = ApplicationConfiguration.getInstance().getConfiguration("server.master.port");
//		HOST = "http://" + masterHostname + ":" + masterPort;
//	}
//	private static final int MAX_CAPACITY = 3000000;
//	private static final int NUM_THREADS_PUSH_GP = 5;
//	private static final int NUM_THREADS_PUSH_TW = 5;
//	private static final int TOTAL_THREADS = NUM_THREADS_PUSH_GP + NUM_THREADS_PUSH_TW;
//	private final CustomizedFixedThreadPool pool = new CustomizedFixedThreadPool(TOTAL_THREADS, 100000, "Pusher");
//
//	public BlockingQueue<String> googlePlusUrls = new ArrayBlockingQueue<>(MAX_CAPACITY);
//	public BlockingQueue<String> twitterUrls = new ArrayBlockingQueue<>(MAX_CAPACITY);
//
//	public AtomicLong gpAddedSuccess = new AtomicLong(0);
//	public AtomicLong gpAddedFailed = new AtomicLong(0);
//
//	public AtomicLong twAddedSuccess = new AtomicLong(0);
//	public AtomicLong twAddedFailed = new AtomicLong(0);
//
//	public PushGoogleTwitterUrls() {
//	}
//
//	public static void main(String[] args) throws IOException {
//		PushGoogleTwitterUrls pusher = new PushGoogleTwitterUrls();
//		pusher.run();
//	}
//
//	public enum ReadMode {
//
//		BOTH, GP, TW
//	}
//
//	public void run() throws IOException {
//
//		SimpleTimer st = new SimpleTimer();
//		// run first time
//		readCrawedDataFile(ReadMode.BOTH);
//		LOG.info("Done read Crawled Data file first time in {} ms", st.getTimeAndReset());
//
//		pushGooglePlusUrls();
//		LOG.info("Done started Push Google Plus");
//		pushTwitterUrls();
//		LOG.info("Done started Push Twitter");
//
//		// service check queue empty
//		ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
//		executor.scheduleAtFixedRate(new Runnable() {
//
//			@Override
//			public void run() {
//				if (googlePlusUrls.size() <= 0 && twitterUrls.size() <= 0) {
//					try {
//						readCrawedDataFile(ReadMode.BOTH);
//					} catch (IOException ex) {
//						LOG.error(ex.getMessage(), ex);
//					}
//				} else if (googlePlusUrls.size() <= 0) {
//					try {
//						readCrawedDataFile(ReadMode.GP);
//					} catch (IOException ex) {
//						LOG.error(ex.getMessage(), ex);
//					}
//				} else if (twitterUrls.size() <= 0) {
//					try {
//						readCrawedDataFile(ReadMode.TW);
//					} catch (IOException ex) {
//						LOG.error(ex.getMessage(), ex);
//					}
//				}
//			}
//		}, 0, 1, TimeUnit.HOURS);
//		executor.scheduleAtFixedRate(new Runnable() {
//
//			@Override
//			public void run() {
//				LOG.info("Google Plus added: {}, failed: {}", gpAddedSuccess.get(), gpAddedFailed.get());
//				LOG.info("Twitter     added: {}, failed: {}", twAddedSuccess.get(), twAddedFailed.get());
//			}
//		}, 0, 30, TimeUnit.SECONDS);
//		LOG.info("Done started SchedulerService");
//
//	}
//
//	private void pushGooglePlusUrls() {
//		for (int i = 0; i < NUM_THREADS_PUSH_GP; i++) {
//			pool.execute(new Runnable() {
//
//				@Override
//				public void run() {
//					while (true) {
//						String url = googlePlusUrls.poll();
//						if (StringUtils.isNoneEmpty(url)) {
//							try {
//								String ret = TCrawler.getContentFromUrl(
//										HOST + "/priority/?url=" + URLEncoder.encode(url, "UTF-8") + "&isForced=true");
//								if (StringUtils.equalsIgnoreCase(ret, "ADDED")) {
//									gpAddedSuccess.incrementAndGet();
//								} else {
//									gpAddedFailed.incrementAndGet();
//								}
//							} catch (UnsupportedEncodingException ex) {
//								LOG.error(ex.getMessage(), ex);
//								gpAddedFailed.incrementAndGet();
//							}
//						} else {
//							Funcs.sleep(10);
//						}
//					}
//				}
//			});
//		}
//	}
//
//	private void pushTwitterUrls() {
//		for (int i = 0; i < NUM_THREADS_PUSH_TW; i++) {
//			pool.execute(new Runnable() {
//
//				@Override
//				public void run() {
//					while (true) {
//						String url = twitterUrls.poll();
//						if (StringUtils.isNoneEmpty(url)) {
//							try {
//								String ret = TCrawler.getContentFromUrl(
//										HOST + "/priority/?url=" + URLEncoder.encode(url, "UTF-8") + "&isForced=true");
//								if (StringUtils.equalsIgnoreCase(ret, "ADDED")) {
//									twAddedSuccess.incrementAndGet();
//								} else {
//									twAddedFailed.incrementAndGet();
//								}
//							} catch (UnsupportedEncodingException ex) {
//								LOG.error(ex.getMessage(), ex);
//								twAddedFailed.incrementAndGet();
//							}
//						} else {
//							Funcs.sleep(10);
//						}
//					}
//				}
//			});
//		}
//	}
//
//	private void readCrawedDataFile(ReadMode mode) throws IOException {
//		String filename = "data2/crawled.profiles";
//		File file = new File(filename);
//		List<String> rows = FileUtils.readList(file);
//
//		Set<String> pGPUrls = new HashSet<>();
//		Set<String> pTWUrls = new HashSet<>();
//
//		for (String row : rows) {
//			if (StringUtils.startsWithIgnoreCase(row, "p:https://plus.google.com/")) {
//				String[] parts = StringUtils.split(row.trim(), "|");
//				String firstPos = parts[0];
//				String profileUrl = StringUtils.substring(firstPos, 2);
//				pGPUrls.add(profileUrl);
//			} else if (StringUtils.startsWithIgnoreCase(row, "p:https://mobile.twitter.com")) {
//				String[] parts = StringUtils.split(row.trim(), "|");
//				String firstPos = parts[0];
//				String profileUrl = StringUtils.substring(firstPos, 2);
//				pTWUrls.add(profileUrl);
//			}
//		}
//		if (mode.equals(ReadMode.BOTH)) {
//			googlePlusUrls.addAll(pGPUrls);
//			twitterUrls.addAll(pTWUrls);
//			LOG.info("DONE read file: GP size {}, TW size {}", pGPUrls.size(), pTWUrls.size());
//		} else if (mode.equals(ReadMode.GP)) {
//			googlePlusUrls.addAll(pGPUrls);
//			LOG.info("DONE read file: GP size {}", pGPUrls.size());
//		} else if (mode.equals(ReadMode.TW)) {
//			twitterUrls.addAll(pTWUrls);
//			LOG.info("DONE read file: TW size {}", pTWUrls.size());
//		}
//	}
//}
