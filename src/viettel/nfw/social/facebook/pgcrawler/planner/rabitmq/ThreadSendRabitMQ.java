package viettel.nfw.social.facebook.pgcrawler.planner.rabitmq;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.MissedHeartbeatException;
import java.util.concurrent.ArrayBlockingQueue;
import net.arnx.jsonic.JSON;
import org.ocpsoft.prettytime.shade.org.apache.commons.lang.StringUtils;
import viettel.nfw.social.model.facebook.FacebookObject;

/**
 *
 * @author chuyennd2
 */
public class ThreadSendRabitMQ {

	public static final ArrayBlockingQueue queueMsg = new ArrayBlockingQueue(100000);
	public static RabbitMQFactory rbmq;

	public void addQueueRabitMq(FacebookObject fbObject) {
//		try {
//			queueMsg.add(JSON.encode(fbObject));
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
	}

	public void startWorking() {
		try {
//			rbmq = new RabbitMQFactory();
//			new Thread(new Runnable() {
//				@Override
//				public void run() {
//					Thread.currentThread().setName("ThreadSendMsg2Rabit");
//					while (true) {
//						try {
//							String msg = (String) queueMsg.take();
//							if (!StringUtils.isEmpty(msg)) {
//								try {
//									rbmq.publishMessage(msg.getBytes());
//								} catch (AlreadyClosedException | MissedHeartbeatException e) {
//									e.printStackTrace();
//									System.out.println("Recreate connection.");
//									rbmq = new RabbitMQFactory();
//								}
//							}
//						} catch (Exception e) {
//							e.printStackTrace();
//						}
//					}
//				}
//			}).start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {

	}
}
