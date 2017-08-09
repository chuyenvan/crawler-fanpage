package viettel.nfw.hotfix;

import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class GetPageGroupConsumerHotFix implements Runnable {

    // dia chi day cu cua activeMQ
    private final String url;
    private final String username;
    private final String password;
    //topic name
    private final String RECEIVED_LIST_PROFILES = "COLLECT_PAGE_GROUP";
    // log cua class
    private static final Logger LOG = LoggerFactory.getLogger(GetPageGroupConsumerHotFix.class);
    // session ket noi va lay msg tu activeMQ
    private Session session;
    // comsumer lay cac trang thai controller gui sang
    private final String consumerName = "collect_pg_consumer_2";
    private final String clientId = "clientId_collect_pg_2";
    private static ConcurrentHashMap<String, String> existedUrls = new ConcurrentHashMap<>();

    public GetPageGroupConsumerHotFix(String url) {
        LOG.info("[StateConsumer] Start!");
        this.url = url;
        this.username = null;
        this.password = null;
    }

    public GetPageGroupConsumerHotFix(String url, String username, String password) {
        LOG.info("[StateConsumer] Start!");
        this.url = url;
        this.username = username;
        this.password = password;
    }

    // start
    public void startService() throws Exception {
        run();
    }

    // stop
    public void stopService() throws Exception {
        // do nothing
    }

    @Override
    public void run() {
        try {
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(url);

            // set up authentication
            if (StringUtils.isNotEmpty(username) && StringUtils.isNotEmpty(password)) {
                connectionFactory.setUserName(username);
                connectionFactory.setPassword(password);
            }

            Connection connection = connectionFactory.createConnection();
            connection.setClientID(clientId);
            connection.start();
            // tao ket noi
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Topic topic = session.createTopic(RECEIVED_LIST_PROFILES);
            // mo 1 consumer lay msg tu topic detect
            MessageConsumer consumer = session.createDurableSubscriber(topic, consumerName);
            consumer.setMessageListener(new MessageListener() {
                @Override
                public void onMessage(Message message) {
                    try {
                        if (message instanceof TextMessage) {
                            TextMessage txtMsg = (TextMessage) message;
                            String objectRequestStr = txtMsg.getText();
                            LOG.info("[{}]Received: {} ", new Date(), objectRequestStr);
                            if (StringUtils.isNotEmpty(objectRequestStr)) {
                                try {
                                    String str = objectRequestStr;
                                    if (StringUtils.contains(objectRequestStr, "\"")) {
                                        str = StringUtils.replace(objectRequestStr, "\"", "");
                                    }
                                    if (StringUtils.isNotEmpty(str)) {
                                        String[] parts = StringUtils.split(str, ",");
                                        for (String part : parts) {
                                            if (existedUrls.containsKey(part)) {
                                                continue;
                                            } else {
                                                System.out.println(part);
                                                existedUrls.put(part, part);
                                            }
                                        }
                                    }
                                } catch (Exception ex) {
                                    LOG.error(ex.getMessage(), ex);
                                }
                            }
                        } else {
                            LOG.info("Received: {} ", message);
                        }
                    } catch (JMSException e) {
                        LOG.error(e.getMessage(), e);
                    } finally {

                    }
                }
            });
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
