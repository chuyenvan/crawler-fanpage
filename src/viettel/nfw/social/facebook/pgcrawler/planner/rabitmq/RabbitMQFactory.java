package viettel.nfw.social.facebook.pgcrawler.planner.rabitmq;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.nigma.engine.util.EngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author thiendn2
 *
 * Created on Feb 15, 2016, 2:06:53 PM
 */
public class RabbitMQFactory {

	private static final EngineConfiguration conf = EngineConfiguration.get();

	static {
		conf.addResource("rabitmq.xml");
	}

	private static final String EXCHANGE_NAME = conf.getStringOrFail("rabitmq.exchange_name");
	private static final String QUEUE_NAME = conf.getStringOrFail("rabitmq.queue_name");
	private static final String ROUTING_KEY = conf.getStringOrFail("rabitmq.queue_name");
	private final ConnectionFactory factory;
	private final AtomicInteger counterRecord = new AtomicInteger(0);
	private Connection connection;
	private static final Logger LOG = LoggerFactory.getLogger(RabbitMQFactory.class);
	// Channel size == num concurrent Serialize Task
	private static final int POOL_SIZE = EngineConfiguration.get()
			.getInt("crawler.crawled_result_process.numthreads", 20);
	private final ArrayBlockingQueue<Channel> channelPool = new ArrayBlockingQueue<Channel>(POOL_SIZE);

	public RabbitMQFactory() throws IOException, TimeoutException {
		factory = new ConnectionFactory();
		String userName = conf.getStringOrFail("rabitmq.username");
		factory.setUsername(userName);
		String passWord = conf.getStringOrFail("rabitmq.password");
		factory.setPassword(passWord);
		String virtualHost = conf.getStringOrFail("rabitmq.virtual_host");
		factory.setVirtualHost(virtualHost);
		String rabbitHost = conf.getStringOrFail("rabitmq.host");
		factory.setHost(rabbitHost);
		int rabbitPort = conf.getIntOrFail("rabitmq.port");
		factory.setPort(rabbitPort);
		LOG.info("Start connection to {}:{}", rabbitHost, rabbitPort);
		connection = factory.newConnection();
		factory.setRequestedHeartbeat(1);
		factory.setConnectionTimeout(5000);
		factory.setAutomaticRecoveryEnabled(true);
		factory.setTopologyRecoveryEnabled(true);
		for (int i = 0; i < POOL_SIZE; i++) {
			channelPool.add(createChannel());
		}
	}

	public Channel createChannel() throws IOException {
		Channel channel = connection.createChannel();
		channel.exchangeDeclare(EXCHANGE_NAME, "direct", true);
		String queueName = channel.queueDeclare(QUEUE_NAME, true, false, false, null).getQueue();
		channel.queueBind(queueName, EXCHANGE_NAME, ROUTING_KEY);
		//channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, null, "test_message_content".getBytes());
		return channel;
	}

	public void publishMessage(byte[] content) throws IOException, TimeoutException {
		Channel channel = null;
		try {
			channel = channelPool.take();
			channel.basicPublish(EXCHANGE_NAME, ROUTING_KEY, null, content);
			LOG.info("Send msg to rabitmq: {}", counterRecord.getAndIncrement());
		} catch (InterruptedException ex) {
			ex.printStackTrace();
		} catch (SocketException e) {
			connection = factory.newConnection();
			e.printStackTrace();
		} finally {
			if (channel != null) {
				try {
					channelPool.put(channel);
				} catch (InterruptedException ex) {
					java.util.logging.Logger.getLogger(RabbitMQFactory.class.getName()).log(Level.SEVERE, null, ex);
					ex.printStackTrace();
				}
			}
		}
	}
}
