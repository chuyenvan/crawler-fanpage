package viettel.nfw.social.controller;

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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author thiendn2
 *
 * Created on Dec 10, 2014, 11:43:04 AM
 */
public class ControllerReporter extends Thread {

    private static final Logger LOG = LoggerFactory.getLogger(ControllerReporter.class);

    private final ActiveMQConnectionFactory connectionFactory;
    private final AtomicLong sequenceFailConnection = new AtomicLong(0L);
    private Connection connection = null;
    private final ConcurrentLinkedQueue<Object> queue = new ConcurrentLinkedQueue<>();
    private static final int MAX_PAGE_PER_SESSION = 20;
    private final String topicName;
    private AtomicBoolean pause = new AtomicBoolean(false);

    public ControllerReporter(String host, int port, String topicName) {
        Funcs.disableLog("org.apache.activemq.transport.InactivityMonitor");
        Funcs.disableLog("apache.activemq.transport.WireFormatNegotiator");

        String url = buildUrl(host, port);
        connectionFactory = new ActiveMQConnectionFactory(url);
        this.topicName = topicName;
        LOG.info("Start ControllerReporter in {}:{} for topicName {}", new Object[]{host, port, topicName});
        if (!createConnection()) {
            LOG.error("Error in creating connection to controller!");
        }
    }

    public ControllerReporter(String host, int port, String username, String password, String topicName) {
        Funcs.disableLog("org.apache.activemq.transport.InactivityMonitor");
        Funcs.disableLog("apache.activemq.transport.WireFormatNegotiator");

        String url = buildUrl(host, port);
        connectionFactory = new ActiveMQConnectionFactory(url);
        if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
            connectionFactory.setUserName(username);
            connectionFactory.setPassword(password);
        }
        this.topicName = topicName;
        LOG.info("Start ControllerReporter in {}:{} for topicName {}", new Object[]{host, port, topicName});
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

    public synchronized void offer(Object ob) {
        queue.add(ob);
        LOG.info("Added {} to queue!", ob.toString());
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
        Session session = null;
        try {
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        } catch (JMSException ex) {
            if (!createConnection()) {
                return false;
            }
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        }
        Topic topic = session.createTopic(topicName);

        // Create the producer.
        MessageProducer producer = session.createProducer(topic);
        producer.setDeliveryMode(DeliveryMode.PERSISTENT);
        int count = 0;
        while (count < MAX_PAGE_PER_SESSION && !queue.isEmpty()) {
            Object obj = queue.poll();
            TextMessage message = session.createTextMessage(JSON.encode(obj));
            producer.send(message);
            LOG.info("Sent {} to controller!", obj.toString());
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
            }
        }
    }

    public static ControllerReporter getDefault(String topicName) {
        String host = ApplicationConfiguration.getInstance().getConfiguration("activemq.ip");
        int port = Integer.parseInt(ApplicationConfiguration.getInstance().getConfiguration("activemq.port"));
        return new ControllerReporter(host, port, topicName);
    }

    public static ControllerReporter getDefault(String host, int port, String username, String password, String topicName) {
        return new ControllerReporter(host, port, username, password, topicName);
    }
}
