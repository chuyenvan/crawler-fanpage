package viettel.nfw.social.tool;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.TParser;

/**
 *
 * @author duongth5
 */
public class SiteFindmyfbidDotComFinder implements IFinder {

	private static final Logger LOG = LoggerFactory.getLogger(SiteLookupIdDotComFinder.class);
	private static final String SITE = "http://findmyfbid.com";
	private final Proxy proxy;

	public SiteFindmyfbidDotComFinder(Proxy proxy) {
		this.proxy = proxy;
	}

	@Override
	public String getId(String facebookProfileUrl) {
		try {
			return downloadAndPaser(facebookProfileUrl, proxy);
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
			return null;
		}
	}

	private static String downloadAndPaser(String facebookProfileUrl, Proxy proxy) throws IOException {

		List<String> paramList = new ArrayList<>();
		paramList.add(URLEncoder.encode("url", "UTF-8") + "=" + URLEncoder.encode(facebookProfileUrl, "UTF-8"));

		StringBuilder result = new StringBuilder();
		for (String param : paramList) {
			if (result.length() == 0) {
				result.append(param);
			} else {
				result.append("&").append(param);
			}
		}
		String postParams = result.toString();

		URL obj = new URL(SITE);
		HttpURLConnection conn;
		if (proxy == null) {
			conn = (HttpURLConnection) obj.openConnection();
		} else {
			conn = (HttpURLConnection) obj.openConnection(proxy);
		}

		// Acts like a browser
		conn.setUseCaches(false);
		conn.setRequestMethod("POST");

		conn.setRequestProperty("Host", "findmyfbid.com");
		conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.0");
		conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
		conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
		conn.setRequestProperty("Referer", "http://findmyfbid.com/");
		conn.setRequestProperty("Connection", "keep-alive");
		conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		conn.setRequestProperty("Content-Length", Integer.toString(postParams.length()));

		conn.setDoOutput(true);
		conn.setDoInput(true);
		conn.setInstanceFollowRedirects(false);

		try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
			wr.writeBytes(postParams);
			wr.flush();
		}
		int status = conn.getResponseCode();
		LOG.debug("Sending 'POST' request to URL: " + SITE);
		LOG.debug("Post parameters: " + postParams);
		LOG.debug("Response Code: " + status);

		boolean redirect = false;

		// normally, 3xx is redirect
		if (status != HttpURLConnection.HTTP_OK) {
			if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM
				|| status == HttpURLConnection.HTTP_SEE_OTHER) {
				redirect = true;
			}
		}

		String id = null;
		if (redirect) {
			// get redirect url from "location" header field
			String redirectUrl = conn.getHeaderField("Location");
            // http://findmyfbid.com/failure
			// http://findmyfbid.com/success/1152666961
			if (StringUtils.isNotEmpty(redirectUrl)) {
				id = TParser.getOneInGroup(redirectUrl, "[0-9]{9,}");
			}
		}

		StringBuilder sb;
		try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
			String inputLine;
			sb = new StringBuilder();
			while ((inputLine = in.readLine()) != null) {
				sb.append(inputLine);
			}
		}

		return id;
	}
}
