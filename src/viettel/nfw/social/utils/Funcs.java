package viettel.nfw.social.utils;

import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.ocpsoft.prettytime.nlp.PrettyTimeParser;

/**
 *
 * @author duongth5
 */
public class Funcs {

	public static boolean randBoolean() {
		Random rand = new Random();
		boolean randBoolean = rand.nextBoolean();
		return randBoolean;
	}

	public static int randInt(int min, int max) {
		Random rand = new Random();
		int randomNum = rand.nextInt((max - min) + 1) + min;
		return randomNum;
	}

	/**
	 * Convert milliseconds to readable string (..h..m..s)
	 *
	 * @param milliseconds input milliseconds
	 * @return readable string
	 */
	public static String toReadableString(long milliseconds) {
		long hours = TimeUnit.MILLISECONDS.toHours(milliseconds);
		long minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) - TimeUnit.HOURS.toMinutes(hours);
		long seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) - TimeUnit.MINUTES.toSeconds(minutes) - TimeUnit.HOURS.toSeconds(hours);
		String ret = String.format("%dh%dm%ds", hours, minutes, seconds);
		return ret;
	}

	/**
	 * Disable log
	 *
	 * @param className
	 */
	public static void disableLog(String className) {
		org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger("className");
		if (logger != null) {
			logger.setLevel(org.apache.log4j.Level.OFF);
		}
	}

	public static boolean sleep(long millis) {
		try {
			Thread.sleep(millis);
			return false;
		} catch (InterruptedException ignored) {
			return true;
		}
	}

	/**
	 * Parse human time to date time. Only support English
	 *
	 * @param humanTime string contains human time
	 * @return parsed date. return null if cannot parse
	 */
	public static Date humanTimeParser(String humanTime) {
		String customHumanTime;
		if (humanTime.matches("^(Sun|Sat|Fri|Thurs|Wed|Tues|Mon)$")) {
			customHumanTime = "this past " + humanTime;
		} else if (StringUtils.startsWithAny(humanTime, new String[]{"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"})) {
			customHumanTime = "this past " + humanTime;
		} else if (humanTime.matches("^(\\d++)(\\s+)(min|mins|hr|hrs)$")) {
			customHumanTime = humanTime + " ago";
		} else {
			customHumanTime = humanTime;
		}
		List<Date> date = new PrettyTimeParser().parse(customHumanTime);
		if (date.isEmpty()) {
			return null;
		}
		return date.get(0);
	}

	public static String userNamePasswordBase64(String username, String password) {
		return "Basic " + base64Encode(username + ":" + password);
	}

	private final static char base64Array[] = {
		'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H',
		'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P',
		'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X',
		'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f',
		'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
		'o', 'p', 'q', 'r', 's', 't', 'u', 'v',
		'w', 'x', 'y', 'z', '0', '1', '2', '3',
		'4', '5', '6', '7', '8', '9', '+', '/'
	};

	private static String base64Encode(String string) {
		String encodedString = "";
		byte bytes[] = string.getBytes();
		int i = 0;
		int pad = 0;
		while (i < bytes.length) {
			byte b1 = bytes[i++];
			byte b2;
			byte b3;
			if (i >= bytes.length) {
				b2 = 0;
				b3 = 0;
				pad = 2;
			} else {
				b2 = bytes[i++];
				if (i >= bytes.length) {
					b3 = 0;
					pad = 1;
				} else {
					b3 = bytes[i++];
				}
			}
			byte c1 = (byte) (b1 >> 2);
			byte c2 = (byte) (((b1 & 0x3) << 4) | (b2 >> 4));
			byte c3 = (byte) (((b2 & 0xf) << 2) | (b3 >> 6));
			byte c4 = (byte) (b3 & 0x3f);
			encodedString += base64Array[c1];
			encodedString += base64Array[c2];
			switch (pad) {
				case 0:
					encodedString += base64Array[c3];
					encodedString += base64Array[c4];
					break;
				case 1:
					encodedString += base64Array[c3];
					encodedString += "=";
					break;
				case 2:
					encodedString += "==";
					break;
			}
		}
		return encodedString;
	}
}
