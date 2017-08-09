package viettel.nfw.social.facebook.deeptracking;

import com.google.gson.Gson;
import java.net.URI;
import java.util.Date;
import java.util.List;
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
public class GetProfileUrlsConsumer implements Runnable {

    // dia chi day cu cua activeMQ
    private final String url;
    private final String username;
    private final String password;
    //topic name
    private final String RECEIVED_LIST_PROFILES = "PROFILE_DEEPTRACKING";
    // log cua class
    private static final Logger LOG = LoggerFactory.getLogger(GetProfileUrlsConsumer.class);
    // session ket noi va lay msg tu activeMQ
    private Session session;
    // comsumer lay cac trang thai controller gui sang
    private final String consumerName = "deeptracking_profiles_consumer";
    private final String clientId = "clientId_deeptracking_profiles";

//    public static void main(String[] args) {
//        GetProfileUrlsConsumer urlConsumer = new GetProfileUrlsConsumer("tcp://192.168.6.84:61616");
//        urlConsumer.run();
//    }
    public GetProfileUrlsConsumer(String url) {
        LOG.info("[StateConsumer] Start!");
        this.url = url;
        this.username = null;
        this.password = null;
    }

    public GetProfileUrlsConsumer(String url, String username, String password) {
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
                            String listLinks = txtMsg.getText();
                            LOG.info("[{}]Received: {} ", new Date(), listLinks);

                            if (StringUtils.equalsIgnoreCase(listLinks, "{}")) {
                                LOG.info("Received: {} ", message);
                                return;
                            }

                            Gson gson = new Gson();
                            ObjectDataSendCrawler trackingUrl = gson.fromJson(listLinks, ObjectDataSendCrawler.class);

                            // 1: chuyennd2
                            // 2: trungnt3
                            String type = trackingUrl.type;
                            List<String> socialLinks = trackingUrl.listProfile;
                            for (String socialLink : socialLinks) {
                                try {
                                    URI uri = new URI(socialLink);
                                    String host = uri.getHost();

                                    if (StringUtils.equalsIgnoreCase(host, "facebook.com")
                                            || StringUtils.equalsIgnoreCase(host, "m.facebook.com")
                                            || StringUtils.equalsIgnoreCase(host, "www.facebook.com")) {
                                        String url;
                                        if (StringUtils.equalsIgnoreCase(host, "facebook.com")) {
                                            url = StringUtils.replace(socialLink, "//facebook.com/", "//m.facebook.com/");
                                        } else if (StringUtils.equalsIgnoreCase(host, "www.facebook.com")) {
                                            url = StringUtils.replace(socialLink, "//www.facebook.com/", "//m.facebook.com/");
                                        } else {
                                            url = socialLink;
                                        }
                                        if (StringUtils.isNotEmpty(url)) {
                                            if (type.equals("2")) {
                                                RunDeepTracking.facebookTrungNT3UrlsQueue.put(url);
                                            } else {
                                                RunDeepTracking.facebookUrlsQueue.put(url);
                                            }
                                        }
                                    } else if (StringUtils.equalsIgnoreCase(host, "plus.google.com")) {
                                        String url = socialLink;
                                        if (StringUtils.isNotEmpty(url)) {
                                            // RunDeepTracking.googleUrlsQueue.put(url);
                                        }
                                    } else if (StringUtils.equalsIgnoreCase(host, "twitter.com")
                                            || StringUtils.equalsIgnoreCase(host, "mobile.twitter.com")
                                            || StringUtils.equalsIgnoreCase(host, "www.twitter.com")) {
                                        String url;
                                        if (StringUtils.equalsIgnoreCase(host, "twitter.com")) {
                                            url = StringUtils.replace(socialLink, "//twitter.com/", "//mobile.twitter.com/");
                                        } else if (StringUtils.equalsIgnoreCase(host, "www.twitter.com")) {
                                            url = StringUtils.replace(socialLink, "//www.twitter.com/", "//mobile.twitter.com/");
                                        } else {
                                            url = socialLink;
                                        }
                                        if (StringUtils.isNotEmpty(url)) {
                                            // RunDeepTracking.twitterUrlsQueue.put(url);
                                        }
                                    } else {
                                        LOG.warn("UNKNOWN URL TYPE - {}", socialLink);
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
