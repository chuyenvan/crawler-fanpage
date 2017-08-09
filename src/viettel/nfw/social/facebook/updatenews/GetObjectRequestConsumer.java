package viettel.nfw.social.facebook.updatenews;

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
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import viettel.nfw.social.facebook.updatenews.graph.entities.SocialType;

/**
 *
 * @author duongth5
 */
public class GetObjectRequestConsumer implements Runnable {

    // dia chi day cu cua activeMQ
    private final String url;
    private final String username;
    private final String password;
    //topic name
    private final String RECEIVED_LIST_PROFILES = "FB_UPDATE_NEWS";
    // log cua class
    private static final Logger LOG = LoggerFactory.getLogger(GetObjectRequestConsumer.class);
    // session ket noi va lay msg tu activeMQ
    private Session session;
    // comsumer lay cac trang thai controller gui sang
    private final String consumerName = "social_updatenews_consumer";
    private final String clientId = "clientId_social_updatenews";
    private static ConcurrentHashMap<String, String> existedObjectRequests = new ConcurrentHashMap<>();
    
    public GetObjectRequestConsumer(String url) {
        LOG.info("[StateConsumer] Start!");
        this.url = url;
        this.username = null;
        this.password = null;
    }

    public GetObjectRequestConsumer(String url, String username, String password) {
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
//                            LOG.info("[{}]Received: {} ", new Date(), objectRequestStr);
                            if (StringUtils.isNotEmpty(objectRequestStr)) {
                                String str = objectRequestStr;
                                if (StringUtils.contains(str, "\"")) {
                                    str = StringUtils.replace(str, "\"", "");
                                }
                                if (StringUtils.contains(str, "[")) {
                                    str = StringUtils.replace(str, "[", "");
                                }
                                if (StringUtils.contains(str, "]")) {
                                    str = StringUtils.replace(str, "]", "");
                                }
                                String[] parts = StringUtils.split(str.trim(), "|");
                                int length = parts.length;
                                if (length == 4) {
                                    String socialTypeStr = parts[0];
                                    String objectIdStr = parts[1];
                                    String objectTypeStr = parts[2];
                                    String timeLoopStr = parts[3];

                                    SocialType socialType = Helper.detectSocialType(socialTypeStr);
                                    if (!socialType.equals(SocialType.UNDEFINED)) {
                                        // With socialType = facebook: objectType = page/group/post
                                        // Others                    : objectType = unknown
                                        if (StringUtils.isNotEmpty(objectIdStr)) {
                                            // check if duplicate request from ActiveMQ
                                            if (existedObjectRequests.containsKey(str.trim())) {
                                                // ignore
                                            } else {
                                                ObjectRequest objectRequest = new ObjectRequest();
                                                objectRequest.socialType = socialType;
                                                objectRequest.objectID = objectIdStr;
                                                objectRequest.objectType = Helper.detectObjectType(objectTypeStr);
                                                objectRequest.loopTimeTimeMillis = Helper.convertTime(timeLoopStr);
                                                RunUpdateNews.storeObjToDb.getReceivedObjectRequestsQueue().add(objectRequest);
                                                existedObjectRequests.put(str.trim(), str.trim());
                                            }
                                        }
                                    }
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
