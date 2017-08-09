/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viettel.nfw.group.crawler;

import viettel.nfw.page.crawler.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import javax.management.timer.Timer;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.RootLogger;

/**
 *
 * @author hoangvv
 */
public class HttpRequestComment {

	private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2883.87 Safari/537.36";
	private static final Logger logger = RootLogger.getLogger(HttpRequest.class);

	public static String sendGet(String url) throws MalformedURLException, IOException, InterruptedException {
//		System.setProperty("https.proxySet", "true");
//		System.getProperties().put("https.proxyHost", "192.168.5.10");
//		System.getProperties().put("https.proxyPort", "3128");
		try {
			long timeout = 10 * Timer.ONE_SECOND;
			long timewait = 20 * Timer.ONE_SECOND;
			HttpURLConnection con = null;
			int response_code = 0;
			try {
				URL obj = new URL(url);
				con = (HttpURLConnection) obj.openConnection();
				con.setConnectTimeout((int) timeout);
				con.setRequestMethod("GET");
				con.setRequestProperty("User-Agent", USER_AGENT);
				con.setRequestProperty("accept-language", "en-US,en;q=0.5");
				con.setRequestProperty("content-type", "application/x-www-form-urlencoded");
				con.setRequestProperty("cookie", "datr=MajjV22L31tpAsCK1rm3_9_9; sb=PqjjVxThD6NWW-6FjXbilHAW; locale=en_GB; fr=0vcOpGNqACTnKXSPC..BX_0YR.pV.AAA.0.0.BX_0YR.AWXv6yHq");
				response_code = con.getResponseCode();
			} catch (IOException e) {
				logger.error("Error in receive response code. Try again in 20s: " + e.getMessage() + "\t" + url);
				Thread.sleep(timewait);
				return null;
			}
			if (response_code == HttpURLConnection.HTTP_OK) {
				StringBuilder response;
				InputStreamReader input = null;
				BufferedReader buffer = null;
				try {
					input = new InputStreamReader(con.getInputStream());
					buffer = new BufferedReader(input);
					String inputLine;
					response = new StringBuilder();
					while ((inputLine = buffer.readLine()) != null) {
						response.append(inputLine);
					}
				} finally {
					if (buffer != null || input != null) {
						buffer.close();
						input.close();
						buffer = null;
						input = null;
					}
				}
				String s = response.toString();
				// print result
				return s;
			} else {
//			logger.error("Error in send url: " + url);
				return null;
			}
		} catch (IOException | InterruptedException e) {
			logger.error(e.getMessage() + "\t" + url);
		}
		return null;
	}
}
