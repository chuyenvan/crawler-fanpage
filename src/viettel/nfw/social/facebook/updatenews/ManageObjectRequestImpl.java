package viettel.nfw.social.facebook.updatenews;

import viettel.nfw.social.facebook.updatenews.graph.entities.SocialType;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class ManageObjectRequestImpl implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ManageObjectRequestImpl.class);

    private static final long WAITING_TOO_LONG_MILLIS = 12 * 60 * 60 * 1000;
    private static final int DELAY_QUERY_MILLS = 40 * 60 * 1000;

    public static ConcurrentHashMap<String, Long> justSentMap = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Long> key2LastCrawledTime = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, ObjectRequest> allObjectsMap = new ConcurrentHashMap<>();

    @Override
    public void run() {
        Thread.currentThread().setName("ManageObjectRequest");

        // start social threads
        FacebookProcessImpl fbProcessImpl = new FacebookProcessImpl();
        new Thread(fbProcessImpl).start();
        // end 

        Funcs.sleep(10 * 1000);

        try {
            // start read last crawled time, query object request database
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(3);
            executor.scheduleAtFixedRate(new QueryObjectRequestDatabaseImpl(), 0, 15, TimeUnit.MINUTES);
            executor.scheduleAtFixedRate(new ReadLastCrawledRepositoryImpl(), 0, 3, TimeUnit.SECONDS);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }

        Funcs.sleep(30 * 1000);

        // query all objects 
        while (true) {

            try {
                ConcurrentHashMap<String, ObjectRequest> unvisitedObjectsMap = new ConcurrentHashMap<>();
                ConcurrentHashMap<String, ObjectRequest> schedulerObjectsMap = new ConcurrentHashMap<>();

                for (Map.Entry<String, ObjectRequest> entrySet : allObjectsMap.entrySet()) {
                    String key = entrySet.getKey();
                    ObjectRequest value = entrySet.getValue();

                    if (StringUtils.isEmpty(key)) {
                        continue;
                    }

                    // if loop time is 0, by pass
                    long loopTime = value.loopTimeTimeMillis;
                    if (loopTime == 0) {
                        continue;
                    }

                    // check objectRequest is valid, if not, by pass
                    String compositeKey = String.format(RunUpdateNews.FORMAT_COMPOSITE_KEY,
                            value.socialType, value.objectID, value.objectType);
                    if (RunUpdateNews.failedObjRepository.getFailedObjectRequestsMap().containsKey(compositeKey)) {
                        continue;
                    }

                    // check in justSentMap
                    if (!justSentMap.containsKey(key)) {
                        // check time in lastCrawledRepo
                        if (key2LastCrawledTime.containsKey(key)) {
                            // existed in repo
                            long lastCrawledTime = key2LastCrawledTime.get(key);
                            long currentTime = System.currentTimeMillis();
                            if ((currentTime - lastCrawledTime) > value.loopTimeTimeMillis) {
                                schedulerObjectsMap.put(key, value);
                            }
                        } else {
                            // not existed in repo
                            unvisitedObjectsMap.put(key, value);
                        }
                    } else {
                        // if it contains in just send, but it stay too long in just send, so remove it
                        long lastTimeSent = justSentMap.get(key);
                        if ((System.currentTimeMillis() - lastTimeSent) > WAITING_TOO_LONG_MILLIS) {
                            justSentMap.remove(key);
                        }
                    }
                }

                LOG.info("Unvisited: {} objs - Scheduler: {} objs", unvisitedObjectsMap.size(), schedulerObjectsMap.size());

                // start doing sent unvisited object, then sent schedular object
                for (Map.Entry<String, ObjectRequest> entrySet : unvisitedObjectsMap.entrySet()) {
                    String key = entrySet.getKey();
                    ObjectRequest value = entrySet.getValue();

                    // send to crawler and add to justSentMap
                    sendToCrawl(value);
                    justSentMap.put(key, System.currentTimeMillis());
                    Funcs.sleep(Funcs.randInt(1, 20));
                }
                Funcs.sleep(Funcs.randInt(100, 300));
                for (Map.Entry<String, ObjectRequest> entrySet : schedulerObjectsMap.entrySet()) {
                    String key = entrySet.getKey();
                    ObjectRequest value = entrySet.getValue();

                    // send to crawler and add to justSentMap
                    sendToCrawl(value);
                    justSentMap.put(key, System.currentTimeMillis());
                    Funcs.sleep(Funcs.randInt(1, 20));
                }

                if (unvisitedObjectsMap.size() < 100 || schedulerObjectsMap.size() < 100) {
                    Funcs.sleep(Funcs.randInt(200, 600));
                } else {
                    Funcs.sleep(Funcs.randInt(DELAY_QUERY_MILLS - (10 * 60 * 1000), DELAY_QUERY_MILLS));
                }

                unvisitedObjectsMap.clear();
                schedulerObjectsMap.clear();
            } catch (Exception ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
    }

    private void sendToCrawl(ObjectRequest value) {
        SocialType socialType = value.socialType;
        switch (socialType) {
            case FACEBOOK:
                FacebookProcessImpl.toCrawlObjects.add(value);
                break;
            case GOOGLE:
                LOG.info("{} has not supported yet", socialType);
                break;
            case TWITTER:
                LOG.info("{} has not supported yet", socialType);
                break;
            case YOUTUBE:
                LOG.info("{} has not supported yet", socialType);
                break;
            case UNDEFINED:
                LOG.info("{} has not supported yet", socialType);
                break;
            default:
                LOG.info("We are working on this");
                break;
        }
    }

    private static class ReadLastCrawledRepositoryImpl implements Runnable {

        @Override
        public void run() {
            Thread.currentThread().setName("ReadLastCrawledRepositoryImpl");
            Map<String, String> currentData = RunUpdateNews.lastCrawledRepository.getAllData();
            for (Map.Entry<String, String> entrySet : currentData.entrySet()) {
                String key = entrySet.getKey();
                String value = entrySet.getValue();
                long lastCrawledTime = Long.parseLong(value);
                key2LastCrawledTime.put(key, lastCrawledTime);
            }
        }
    }

    private static class QueryObjectRequestDatabaseImpl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(QueryObjectRequestDatabaseImpl.class);

        @Override
        public void run() {
            Thread.currentThread().setName("QueryObjectRequestDatabaseImpl");
            Map<String, ObjectRequest> allData = RunUpdateNews.objRequestRepository.getAllData();
            LOG.info("Object Request REPO size {}", allData.size());
            allObjectsMap.putAll(allData);
            LOG.info("ALL Object Request current size {}", allObjectsMap.size());
        }
    }
}
