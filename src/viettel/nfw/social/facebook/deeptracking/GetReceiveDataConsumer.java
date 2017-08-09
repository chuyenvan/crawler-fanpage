package viettel.nfw.social.facebook.deeptracking;

import java.util.Date;
import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ApplicationConfiguration;

/**
 *
 * @author duongth5
 */
public class GetReceiveDataConsumer implements Runnable {

    // dia chi day cu cua activeMQ
    private final String url;
    //topic name
    private final String RECEIVED_LIST_PROFILES = "PROFILE_DATA_RECEIVE";
    // log cua class
    private static final Logger LOG = LoggerFactory.getLogger(GetProfileUrlsConsumer.class);
    // session ket noi va lay msg tu activeMQ
    private Session session;
    // comsumer lay cac trang thai controller gui sang
    private final String consumerName = "deeptracking_receive_consumer";
    private final String clientId = "clientId_deeptracking_receive";

    public static void main(String[] args) {
        GetReceiveDataConsumer urlConsumer = new GetReceiveDataConsumer("tcp://192.168.6.84:61616");
        urlConsumer.run();
    }

    public GetReceiveDataConsumer(String url) {
        LOG.info("[StateConsumer] Start!");
        this.url = url;
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
            String username = ApplicationConfiguration.getInstance().getConfiguration("controller.username");
            String password = ApplicationConfiguration.getInstance().getConfiguration("controller.password");
            connectionFactory.setUserName(username);
            connectionFactory.setPassword(password);

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
                            String listLinks = txtMsg.getText();
                            LOG.info("[{}]Received: {} ", new Date(), listLinks);
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
