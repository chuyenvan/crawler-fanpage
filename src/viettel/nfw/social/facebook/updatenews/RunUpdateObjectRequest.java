package viettel.nfw.social.facebook.updatenews;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.common.ConfigurationChangeListner;
import viettel.nfw.social.controller.ControllerReporter;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import viettel.nfw.social.facebook.updatenews.repo.ObjectRequestRepository;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.utils.SerializeObjectUtils;
import vn.viettel.utils.SimpleTimer;

/**
 *
 * @author duongth5
 */
public class RunUpdateObjectRequest {

    private static final Logger LOG = LoggerFactory.getLogger(RunUpdateObjectRequest.class);
    private static final String CONF_FILE_PATH = "conf/app-update-news.properties";
    public static final int MAX_CAPACITY = 5000000;
    public static final String FORMAT_KEY_OBJ_REQ = "%s_%s";
    private static final String TOPIC_NAME = "FB_UPDATE_NEWS";

    public static BlockingQueue<ObjectRequest> receivedFromActiveMQ = new ArrayBlockingQueue<>(MAX_CAPACITY);
    public static ObjectRequestRepository objRequestRepository = null;

    public static void main(String[] args) {

        SimpleTimer st = new SimpleTimer();
        objRequestRepository = ObjectRequestRepository.getInstance();
        LOG.info("Init ObjectRequestRepository in: {} ms", st.getTimeAndReset());

        // Thread watch configuration
        ConfigurationChangeListner configListner = new ConfigurationChangeListner(CONF_FILE_PATH);
        new Thread(configListner).start();
        Funcs.sleep(2000);

        // Thread get object requests from activeMQ
        String host = ApplicationConfiguration.getInstance().getConfiguration("activemq.ip");
        int port = Integer.parseInt(ApplicationConfiguration.getInstance().getConfiguration("activemq.port"));
        String activeMQUrl = String.format("tcp://%s:%d", host, port);
        String activeMQUsername = ApplicationConfiguration.getInstance().getConfiguration("activemq.username");
        String activeMQPassword = ApplicationConfiguration.getInstance().getConfiguration("activemq.password");
        GetObjectRequestConsumer getORConsumer = new GetObjectRequestConsumer(activeMQUrl, activeMQUsername, activeMQPassword);
        new Thread(getORConsumer).start();

        // Thread store object request from activeMQ to database
        StoreObjectRequestToDbImpl storeToDbImpl = new StoreObjectRequestToDbImpl();
        new Thread(storeToDbImpl).start();
    }

    private static class StoreObjectRequestToDbImpl implements Runnable {

        private static final Logger LOG = LoggerFactory.getLogger(StoreObjectRequestToDbImpl.class);

        @Override
        public void run() {
            Thread.currentThread().setName("StoreObjectRequestToDbImpl");
            while (true) {
                ObjectRequest objectRequest = receivedFromActiveMQ.poll();
                if (objectRequest == null) {
                    Funcs.sleep(800);
                } else {
                    // write to db
                    try {
                        String key = String.format(FORMAT_KEY_OBJ_REQ, objectRequest.socialType, objectRequest.objectID);
                        byte[] keyByteArr = key.getBytes();
                        byte[] valueByteArr = SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(objectRequest);
                        objRequestRepository.write(keyByteArr, valueByteArr);
                        LOG.info("Saved {} to db with key {}", objectRequest.toString(), key);
                    } catch (IOException ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
            }
        }
    }
}
