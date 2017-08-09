package viettel.nfw.social.facebook.pgcrawler.planner.rabitmq;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author chuyennd2
 */
public class TestRabitMQ {

	public static void main(String[] args) throws IOException, TimeoutException {
//		System.setProperty("http.proxyHost", "203.113.152.15");
//		System.setProperty("http.proxyPort", "3456");
		RabbitMQFactory rbmq = new RabbitMQFactory();
		String msg = "TestRabit";
		rbmq.publishMessage(msg.getBytes());
	}
}
