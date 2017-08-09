package viettel.nfw.social.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import net.arnx.jsonic.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class SMSAlertService {

	private static final Logger LOG = LoggerFactory.getLogger(SMSAlertService.class);

	public static final String SMS_GATEWAY_INTERNAL = "http://10.30.154.108:1128/v1.0/sms";
	public static final String SMS_GATEWAY_PUBLIC = "http://203.113.152.84:1128/v1.0/sms";
	public static final String USERNAME = "smsAcount";
	public static final String PASSWORD = "Alert_Gateway_Center1232**";
	private static final String KEY_CONTENT_TYPE = "Content-Type";
	private static final String JSON_CONTENT_TYPE = "application/json";

	public static class SMSRequest {

		public String mobile;
		public String sms;

		public SMSRequest() {
		}

		public SMSRequest(String mobile, String sms) {
			this.mobile = mobile;
			this.sms = sms;
		}

		@Override
		public String toString() {
			return "mobile=" + mobile + ", sms=" + sms;
		}

	}

	public static boolean offer(String host, String username, String password, SMSAlertService.SMSRequest message) {
		boolean isSuccess = false;
		try {
			URL url = new URL(host);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setRequestProperty(KEY_CONTENT_TYPE, JSON_CONTENT_TYPE);
			conn.setRequestMethod("POST");

			conn.setRequestProperty("Authorization", Funcs.userNamePasswordBase64(username, password));
			conn.connect();

			String jsonStr = JSON.encode(message);
			System.out.println(jsonStr);
			byte[] outputBytes = jsonStr.getBytes("UTF-8");
			try (OutputStream os = conn.getOutputStream()) {
				os.write(outputBytes);
			}

			//display what returns the POST request
			StringBuilder sb = new StringBuilder();
			int HttpResult = conn.getResponseCode();
			if (HttpResult == HttpURLConnection.HTTP_OK) {
				try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
					String line;
					while ((line = br.readLine()) != null) {
						sb.append(line).append("\n");
					}
				}
				LOG.info("Response: {}", sb.toString());
				isSuccess = true;
			} else {
				LOG.warn("Response: {}", conn.getResponseMessage());
			}
		} catch (MalformedURLException ex) {
			LOG.error(ex.getMessage(), ex);
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}

		return isSuccess;
	}

	public static void main(String[] args) {
		SMSAlertService.SMSRequest message = new SMSAlertService.SMSRequest();
		message.mobile = "+841689041344";
		message.sms = "Crawler_Services_Graph test message from +84978011696";

		offer(SMS_GATEWAY_INTERNAL, USERNAME, PASSWORD, message);
	}
}
