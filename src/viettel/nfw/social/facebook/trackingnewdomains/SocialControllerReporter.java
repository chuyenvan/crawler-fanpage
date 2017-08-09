package viettel.nfw.social.facebook.trackingnewdomains;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.jms.Connection;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import net.arnx.jsonic.JSON;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.Funcs;

/**
 * Controller reporter for social crawler
 *
 * @author duongth5
 * @version 1.0
 * @since 1.0
 */
public class SocialControllerReporter extends Thread {

    /**
     * Logger for SocialControllerReporter class
     */
    private static final Logger LOG = LoggerFactory.getLogger(SocialControllerReporter.class);

    public static final String TOPIC_NEW_DOMAINS = "NEW_DOMAINS_TRACK";
    public static final String TOPIC_DUONGTH5 = "DUONGTH5";

    private final ActiveMQConnectionFactory connectionFactory;
    private final AtomicLong sequenceFailConnection = new AtomicLong(0L);
    private Connection connection = null;
    private final ConcurrentLinkedQueue<Pair<String, Object>> queue = new ConcurrentLinkedQueue<>();
    private static final int MAX_PAGE_PER_SESSION = 20;
    private final AtomicBoolean pause = new AtomicBoolean(false);

    public SocialControllerReporter(String host, int port) {
        Funcs.disableLog("org.apache.activemq.transport.InactivityMonitor");
        Funcs.disableLog("apache.activemq.transport.WireFormatNegotiator");

        String url = buildUrl(host, port);
        connectionFactory = new ActiveMQConnectionFactory(url);
        connectionFactory.setUserName("chuyennd");
        connectionFactory.setPassword("chUyenNd2@");
        LOG.info("Start ControllerReporter in {}:{}", host, port);
        if (!createConnection()) {
            LOG.error("Error in creating connection to controller!");
        }
    }

    public void pause() {
        pause.set(true);
    }

    public void unpause() {
        pause.set(false);
    }

    public synchronized void offer(Pair<String, Object> pair) {
        queue.add(pair);
        LOG.info("Topic {}, Added {} to queue!", pair.first, pair.second.toString());
    }

    private static String buildUrl(String host, int port) {
        return String.format("tcp://%s:%d", host, port);
    }

    private boolean createConnection() {
        LOG.info("Start creating connection...");
        try {
            if (connection != null) {
                connection.close();
            }
            connection = connectionFactory.createConnection();
            connection.start();
            LOG.info("Started connection...");
            sequenceFailConnection.set(0);
            return true;
        } catch (JMSException ex) {
            LOG.error("Fail connection...", ex);
            sequenceFailConnection.incrementAndGet();
            return false;
        }

    }

    /**
     * Send black page to controller
     *
     * @param bp
     * @return status
     * @throws JMSException
     */
    private boolean flushQueue() throws JMSException {
        Session session;
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (JMSException ex) {
            LOG.error(ex.getMessage(), ex);
            if (!createConnection()) {
                return false;
            }
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        }

        int count = 0;
        while (count < MAX_PAGE_PER_SESSION && !queue.isEmpty()) {

            Pair<String, Object> pair = queue.poll();
            String topicName = pair.first;
            Object obj = pair.second;
            Topic topic = session.createTopic(topicName);
            // Create the producer.
            MessageProducer producer = session.createProducer(topic);
            producer.setDeliveryMode(DeliveryMode.PERSISTENT);
            TextMessage message = session.createTextMessage(JSON.encode(obj));
            producer.send(message);
            LOG.info("Topic {}, Sent {} to controller!", topicName, obj.toString());
            count++;
        }
        session.close();
        return true;
    }

    @Override
    public void run() {
        while (!stop.get()) {
            while (pause.get()) {
                Funcs.sleep(20);
            }
            if (!queue.isEmpty()) {
                try {
                    if (!flushQueue()) {
                        LOG.error("Fail in flushing queue!");
                    }
                } catch (JMSException ex) {
                    LOG.error("Error in flushing queue ", ex);
                } catch (Exception ex) {
                    LOG.error("Unknown error in flushing queue ", ex);
                }
            }
            Funcs.sleep(500);
        }
    }

    private final AtomicBoolean stop = new AtomicBoolean(false);

    public void shutdown() {
        stop.set(true);

        if (connection != null) {
            try {
                flushQueue();
                Funcs.sleep(10000);
                connection.close();
            } catch (JMSException ignore) {
                LOG.error(ignore.getMessage(), ignore);
            }
        }
    }

    public static SocialControllerReporter getDefault() {
        String host = "192.168.9.62";
        int port = 61616;
        return new SocialControllerReporter(host, port);
    }
}
