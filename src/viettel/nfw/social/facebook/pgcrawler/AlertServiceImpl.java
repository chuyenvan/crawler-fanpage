package viettel.nfw.social.facebook.pgcrawler;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.crawler.CrawlerManager;
import viettel.nfw.social.utils.DateUtils;
import viettel.nfw.social.utils.EngineConfiguration;
import viettel.nfw.social.utils.Funcs;
import viettel.nfw.social.utils.SMSAlertService;

/**
 *
 * @author duongth5
 */
public class AlertServiceImpl implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(AlertServiceImpl.class);
	private static String SMS_GATEWAY;
	private static String[] PHONES_DEV;
	private static String[] PHONES_VIP;
	private static String USERNAME;
	private static String PASSWORD;

	private static final List<Long> SMS_LIST_HOUR = new ArrayList();
	private static final long SMS_MIN_SPEED = EngineConfiguration.get().getLong("sms.min_speed", 50);
	private static final long RESET_STATS_HOUR = EngineConfiguration.get().getLong("reset_stats.hour", 17);

	private static final SimpleDateFormat SDF_TIME_DATE = new SimpleDateFormat("HH:mm:ss dd/MM/yyyy");

	public AlertServiceImpl() {
		SMS_GATEWAY = EngineConfiguration.get().get("sms.phone.gateway", "http://203.113.152.84:1128/v1.0/sms");
		PHONES_DEV = EngineConfiguration.get().get("sms.phone.dev", "+841689041344").split(","); // HoangVV
		PHONES_VIP = EngineConfiguration.get().get("sms.phone.vip", "+84978011696").split(","); // MinhHT1
		USERNAME = EngineConfiguration.get().get("sms.phone.username", "crawler");
		PASSWORD = EngineConfiguration.get().get("sms.phone.password", "Crawler@1357_**");
		SMS_LIST_HOUR.add(8L);
		SMS_LIST_HOUR.add(12L);
		SMS_LIST_HOUR.add(21L);
		LOG.info("PHONES_DEV: " + Arrays.toString(PHONES_DEV));
		LOG.info("PHONES_VIP: " + Arrays.toString(PHONES_VIP));
		LOG.info("USERNAME  : " + USERNAME);
	} 

	@Override
	public void run() {
		try {
			long currentHour = DateUtils.getCurrentHour();

			// calculate speed
			long previousTotalVisitedProfiles = CrawlerManager.previousVisitedProfilesToday.get();
			long currentTotalVisitedProfiles = CrawlerManager.visitedProfilesToday.get();
			long speedPerHour = currentTotalVisitedProfiles - previousTotalVisitedProfiles;
			if (SMS_LIST_HOUR.contains(currentHour)) {
				sendSms(PHONES_DEV, formatSMS(reportSpeed(speedPerHour)));
				sendSms(PHONES_VIP, formatSMS(reportSpeed(speedPerHour)));
			} else {
				if (speedPerHour < SMS_MIN_SPEED) {
					sendSms(PHONES_DEV, formatSMS(reportSpeed(speedPerHour)));
					sendSms(PHONES_VIP, formatSMS(reportSpeed(speedPerHour)));
				}
			}
			CrawlerManager.previousVisitedProfilesToday.set(currentTotalVisitedProfiles);

			// report stats and reset stats
			if (currentHour == RESET_STATS_HOUR) {
				LOG.info("Start new day! Send report and Reset stats!");
				// send report
				String reportOfDay = reportOfDay(CrawlerManager.visitedProfilesToday.get(), CrawlerManager.failProfilesToday.get(),
						CrawlerManager.discoveredPostsToday.get(), CrawlerManager.discoveredCommentsToday.get());
				sendSms(PHONES_DEV, formatSMS(reportOfDay));
				sendSms(PHONES_VIP, formatSMS(reportOfDay));

				Funcs.sleep(500L);
				// reset stats
				CrawlerManager.visitedProfilesToday.set(0);
				CrawlerManager.previousVisitedProfilesToday.set(0);
				CrawlerManager.failProfilesToday.set(0);
				CrawlerManager.discoveredPostsToday.set(0);
				CrawlerManager.discoveredCommentsToday.set(0);
			}

		} catch (Exception e) {
			LOG.error("Error while do monitoring stats", e);
		}

	}

	private static String reportSpeed(long speed) {
		StringBuilder sb = new StringBuilder();
		sb.append("Deep pages/groups speed: ").append(speed).append(" profiles/hour");
		return sb.toString();
	}

	private static String reportOfDay(long visitedProfilesToday, long failProfilesToday, long discoveredPostsToday, long discoveredCommentsToday) {
		long currentTime = System.currentTimeMillis();
		long oneDayAgoTime = currentTime - 24 * 60 * 60 * 1000L;
		StringBuilder sb = new StringBuilder();
		sb.append("Deep pages/groups stats from ").append(SDF_TIME_DATE.format(new Date(oneDayAgoTime))).append(" to ").append(SDF_TIME_DATE.format(new Date(currentTime))).append("\n");
		sb.append("Total visited profiles: ").append(visitedProfilesToday).append("\n");
		sb.append("Visited profiles fail: ").append(failProfilesToday).append("\n");
		sb.append("Visited posts: ").append(discoveredPostsToday).append("\n");
		sb.append("Visited comments: ").append(discoveredCommentsToday);
		return sb.toString();
	}

	private void sendSms(String[] phones, String content) {
		if (phones != null && phones.length > 0) {
			for (String phone : phones) {
				SMSAlertService.SMSRequest message = new SMSAlertService.SMSRequest();
				message.mobile = phone;
				message.sms = content;
				SMSAlertService.offer(SMS_GATEWAY, USERNAME, PASSWORD, message);
			}
		} else {
			LOG.warn("Phones is null or empty!!!");
		}
	}

	private String formatSMS(String content) {
		StringBuilder sb = new StringBuilder();
		sb.append("TYP: REP").append("\n");
		sb.append("MSG:").append("\n").append(content).append("\n");
		sb.append("HOS: crawler29.nfw.vn").append("\n");
		sb.append("SRV: CRAWLER_GRAPH").append("\n");
		sb.append("OWN: MINHHT01").append("\n");
		sb.append("DATE: ").append(SDF_TIME_DATE.format(new Date()));
		return sb.toString();
	}
}
