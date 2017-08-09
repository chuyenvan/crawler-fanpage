package viettel.nfw.social.facebook.updatenews;

import com.viettel.naviebayes.NaiveBayes;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.common.ConfigurationChangeListner;
import viettel.nfw.social.controller.ControllerReporter;
import viettel.nfw.social.facebook.updatenews.repo.CrawledFacebookObjectRepository;
import viettel.nfw.social.facebook.updatenews.repo.FacebookAppRepository;
import viettel.nfw.social.facebook.updatenews.repo.FailedObjectRequest;
import viettel.nfw.social.facebook.updatenews.repo.LastCrawledRepository;
import viettel.nfw.social.facebook.updatenews.repo.MappingUsername2IdRepository;
import viettel.nfw.social.facebook.updatenews.repo.ObjectRequestRepository;
import viettel.nfw.social.facebook.updatenews.repo.ProfilePostsRepository;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.utils.SimpleTimer;

/**
 *
 * @author duongth5
 */
public class RunUpdateNews {

    private static final Logger LOG = LoggerFactory.getLogger(RunUpdateNews.class);
    private static final String CONF_FILE_PATH = "conf/app-update-news.properties";

    public static final int MAX_CAPACITY = 2000000;

    // socialType#objectId#objectType. Ex: FACEBOOK#giaitrionline#PAGE
    public static final String FORMAT_COMPOSITE_KEY = "%s#%s#%s";

    public static final Charset IO_CHARSET = Charset.forName("UTF-8");

    // init controller
    public static ControllerReporter sensitiveReporter;
    private static final String SENSITIVE_TOPIC_NAME = "Black_Profile";

    // init repository
    public static CrawledFacebookObjectRepository crawledFbObjRepository = null;
    public static LastCrawledRepository lastCrawledRepository = null;
    public static MappingUsername2IdRepository mappingUsername2IdRepositpory = null;
    public static ObjectRequestRepository objRequestRepository = null;
    public static ProfilePostsRepository profiePostsRepository = null;
    public static FailedObjectRequest failedObjRepository = null;
    public static FacebookAppRepository facebookAppRepository = null;

    public static StoreObjectRequestToDbImpl storeObjToDb = null;

    private static ExecutorService threadPool;

    public static final Set<String> allBlackSites;

    private static boolean isEnableActiveMQ = false;

    static {
        allBlackSites = new HashSet<>();
        try {
            allBlackSites.addAll(FileUtils.readList(new FileInputStream("data/black_sites/black_site_current.txt"), false));
        } catch (IOException ex) {
            LOG.error("Error in loading black sites {}", ex);
        }
    }

    public static void main(String[] args) {

        // init repository
        SimpleTimer st = new SimpleTimer();
        crawledFbObjRepository = CrawledFacebookObjectRepository.getInstance();
        LOG.info("Init CrawledFacebookObjectRepository in: {} ms", st.getTimeAndReset());
        lastCrawledRepository = LastCrawledRepository.getInstance();
        LOG.info("Init LastCrawledRepository in: {} ms", st.getTimeAndReset());
        mappingUsername2IdRepositpory = MappingUsername2IdRepository.getInstance();
        LOG.info("Init MappingIdUsernameRepository in: {} ms", st.getTimeAndReset());
        objRequestRepository = ObjectRequestRepository.getInstance();
        LOG.info("Init ObjectRequestRepository in: {} ms", st.getTimeAndReset());
        profiePostsRepository = ProfilePostsRepository.getInstance();
        LOG.info("Init ProfilePostsRepository in: {} ms", st.getTimeAndReset());
        failedObjRepository = FailedObjectRequest.getInstance();
        LOG.info("Init FailedObjectRequest in: {} ms", st.getTimeAndReset());
        facebookAppRepository = FacebookAppRepository.getInstance();
        LOG.info("Init FacebookAppRepository in: {} ms", st.getTimeAndReset());
        // ---------------------------------------------------------------------

        // collect object request from ActiveMQ or Facebook Maintain
        storeObjToDb = StoreObjectRequestToDbImpl.getInstance();
        LOG.info("Started StoreObjectRequestToDbImpl in: {} ms", st.getTimeAndReset());

        // init NaiveBayes
        try {
            NaiveBayes.getInstance();
            LOG.info("Finished getting naive bayes instance");
        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
        Funcs.sleep(2000);
        // ---------------------------------------------------------------------

        threadPool = Executors.newFixedThreadPool(5);

        // Thread watch configuration file
        ConfigurationChangeListner configListner = new ConfigurationChangeListner(CONF_FILE_PATH);
        threadPool.execute(configListner);
        Funcs.sleep(2000);

        // Get information of ActiveMQ server
        String host = ApplicationConfiguration.getInstance().getConfiguration("activemq.ip");
        int port = Integer.parseInt(ApplicationConfiguration.getInstance().getConfiguration("activemq.port"));
        String activeMQUrl = String.format("tcp://%s:%d", host, port);
        String activeMQUsername = ApplicationConfiguration.getInstance().getConfiguration("activemq.username");
        String activeMQPassword = ApplicationConfiguration.getInstance().getConfiguration("activemq.password");

        String isEnableActiveMQStr = ApplicationConfiguration.getInstance().getConfigurationWithDefaultValue("activemq.consumer.enable", "false");
        isEnableActiveMQ = Boolean.parseBoolean(isEnableActiveMQStr);

        if (isEnableActiveMQ) {
            // Consumer which get Object_Request from ActiveMQ topic FB_UPDATE_NEWS
            // This will receive Object with format
            GetObjectRequestConsumer getORConsumer = new GetObjectRequestConsumer(activeMQUrl, activeMQUsername, activeMQPassword);
            threadPool.execute(getORConsumer);
            Funcs.sleep(1000);

            // Consumer which get Public Page, Group, Post from Facebook Maintain via topic COLLECT_PAGE_GROUP
            // This will receive Url
            GetPageGroupConsumer getPGConsumer = new GetPageGroupConsumer(activeMQUrl, activeMQUsername, activeMQPassword);
            threadPool.execute(getPGConsumer);
            Funcs.sleep(1000);
        }

        // sensitive reporter
        sensitiveReporter = ControllerReporter.getDefault(host, port, activeMQUsername, activeMQPassword, SENSITIVE_TOPIC_NAME);
        sensitiveReporter.start();
        Funcs.sleep(2000);

        // send facebook object to bigdata
        SendObjectToBigDataImpl sendObjectToBGImpl = new SendObjectToBigDataImpl();
        threadPool.execute(sendObjectToBGImpl);
        Funcs.sleep(40 * 1000);

        ManageObjectRequestImpl manageImpl = new ManageObjectRequestImpl();
        threadPool.execute(manageImpl);
        Funcs.sleep(3 * 1000);

        cleaningGC();
    }

    private static void cleaningGC() {
        try {
            Runnable statRunnable = new Runnable() {
                @Override
                public void run() {
                    Thread.currentThread().setName("CleaningGC");
                    try {
                        System.gc();
                        LOG.info("Cleanup complete ... ");
                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
            };

            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
            executor.scheduleAtFixedRate(statRunnable, 5, 20, TimeUnit.MINUTES);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }

}
