package viettel.nfw.social.utils;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author DoNgocThien
 */
public class TCrawler {

	private static final Logger LOG = LoggerFactory.getLogger(TCrawler.class);

	private static final String J_CONNECTION = "close";
	// UserAgent
	private static final String J_USER_AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/27.0.1453.121 CoRom/27.0.1453.121 Safari/537.36 AlexaToolbar/alxg-3.1";
	// private static final String J_ACCEPT = "text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5";
	private static final String J_ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
	private static final String J_ACCEPT_CHARSET = "UTF-8,iso-8859-1;q=0.7,*;q=0.7";
	private static final String J_ACCEPT_LANGUAGE = "vi,en-US;q=0.8,en;q=0.6";
	private static final int J_N_RETRY = 5;
	// Thoi gian connect
	private static final int connect_time_out = 30 * 1000; // 30s
	// Thoi gian doc
	private static final int read_time_out = 30 * 1000; // 30s
	// Do dai max content
	private static final int max_content_length = 8 * 1024 * 1024; // 8M
	// Neu do dai cua content ma duoi gia tri nay thi day co the la trang redirect sang trag khac hoac la trang loi
	private static final int min_content_length = 2000; // 2k
	// Cho phep redirect
	private static final boolean follow_redirect = true;
	// Do sau cua lay redirect
	private static final int max_depth_redirect = 6;

	/**
	 * Tao ket noi den URL
	 *
	 * @param url_
	 * @return
	 */
	private static HttpURLConnection connect(URL url_, String method, Map<String, String> extent) {
		try {
			URLConnection ucon = url_.openConnection();
			HttpURLConnection conn = (HttpURLConnection) ucon;

			//conn.setRequestProperty("Cookie", "");
			conn.setUseCaches(false);
			conn.setAllowUserInteraction(false);
			conn.setRequestMethod(method);
			conn.setConnectTimeout(connect_time_out);
			conn.setReadTimeout(read_time_out);

			HttpURLConnection.setFollowRedirects(true);
			conn.setInstanceFollowRedirects(true);

			conn.addRequestProperty("Connection", J_CONNECTION);
			conn.addRequestProperty("User-Agent", J_USER_AGENT);
			conn.addRequestProperty("Accept", J_ACCEPT);
			conn.addRequestProperty("Accept-Charset", J_ACCEPT_CHARSET);
			conn.addRequestProperty("Accept-Language", J_ACCEPT_LANGUAGE);
			conn.setDoOutput(true);
			if (method.equals("POST")) {
				conn.setDoInput(true);
				StringBuilder data = new StringBuilder();
				for (String param : extent.keySet()) {
					String value = extent.get(param);
					data.append(URLEncoder.encode(param, "UTF-8")).append('=')
						.append(URLEncoder.encode(value, "UTF-8")).append("&");
				}
				if (data.length() > 0) {
					data.setLength(data.length() - 1);
					try (OutputStream os = conn.getOutputStream(); BufferedWriter writer = new BufferedWriter(
						new OutputStreamWriter(os, "UTF-8"))) {
						writer.write(data.toString());
						writer.flush();
					}
				}
			} else {
				if (extent != null) {
					for (String field : extent.keySet()) {
						conn.addRequestProperty(field, extent.get(field));
					}
				}
			}

			conn.connect();
			return conn;
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
			return null;
		}
	}

	public static void getBinaryContentFromUrl(String url, OutputStream out) {
		for (int i = 0; i < 1; i++) {
			try {
				HttpURLConnection hc = connect(new URL(url), "GET", null);
				int contentLength = hc.getContentLength();
				String contentType = hc.getContentType();
				if (contentType.startsWith("text/") || contentLength == -1) {
					throw new IOException("This is not a binary file: " + url);
				}

				int c = 0;
				InputStream raw = hc.getInputStream();
				byte[] data;
				int offset;
				try (InputStream in = new BufferedInputStream(raw)) {
					data = new byte[contentLength];
					int bytesRead = 0;
					offset = 0;
					while (offset < contentLength) {
						bytesRead = in.read(data, offset, data.length - offset);
						if (bytesRead == -1) {
							break;
						}
						offset += bytesRead;
					}
				}
				if (offset != contentLength) {
					throw new IOException("Only read " + offset + " bytes; Expected " + contentLength + " bytes: " + url);
				}
				out.write(data);
				out.flush();
				return;
			} catch (Exception ex) {
				LOG.error(ex.getMessage(), ex);
			}
			Funcs.sleep(10000L);
		}
	}

	public static String postContentFromUrl(String url, Map<String, String> extendedHeader) {
		return getContentFromUrl(url, "POST", extendedHeader);
	}

	public static String getContentFromUrl(String url) {
		return getContentFromUrl(url, null);
	}

	public static String getContentFromUrl(String url, Map<String, String> extendedHeader) {
		return getContentFromUrl(url, "GET", extendedHeader);
	}

	private static String getContentFromUrl(String url, String method, Map<String, String> extendedHeader) {
		String content = "";
		boolean useGZip = false;
		for (int i = 0; i < J_N_RETRY; i++) {
			try {
				HttpURLConnection hc = connect(new URL(url), method, extendedHeader);
				int content_length = hc.getContentLength();
				if ((content_length > max_content_length) || (content_length == -1)) {
					content_length = max_content_length;
				}

				StringBuilder sb = new StringBuilder();
				int c = 0;
				InputStream is = null;
				if (useGZip) {
					is = new GZIPInputStream(hc.getInputStream());
					content_length = 1 * 1024 * 1024;
				} else {
					is = hc.getInputStream();
				}
				try (BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"))) {
					char ch[] = new char[content_length];

					while (c < content_length) {
						int t = br.read(ch, 0, content_length);

						if (t > 0) {
							sb.append(ch, 0, t);
						} else {
							break;
						}

						c = c + t;
					}
				}
				content = sb.toString();
				if (hc.getURL() != null) {
					url = hc.getURL().toString();
				}
				//System.out.println(hc.getHeaderField("Content-Encoding"));
				try {
					if (!content.contains("<body>") && hc.getHeaderField("Content-Encoding").equals("gzip")) {
						useGZip = true;
						continue;
					}
				} catch (Exception ex) {
				}
				break;
			} catch (Exception ex) {
				LOG.error(ex.getMessage(), ex);
				if (i == J_N_RETRY - 1) {
					return null;
				}

			}
		}
		return content;
	}

	public static List<Proxy> getProxiesFromFreeProxyNet() {
		return getProxiesFromFreeProxyNet(-1);
	}

	public static List<Proxy> getProxiesFromFreeProxyNet(int limit) {
		String content = getContentFromUrl("http://www.us-proxy.org/");
		List<Proxy> res = new ArrayList<>();
		if (content == null) {
			return res;
		}
		String tbody = TParser.getContent(content, "<tbody>", "</tbody>");
		List<String> trs = TParser.getContentList(tbody, "<tr[^>]*+>", "</tr>");

		for (String tr : trs) {
			try {
				List<String> tds = TParser.getContentList(tr, "<td[^>]*+>", "</td>");
				res.add(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(tds.get(0), Integer.parseInt(tds.get(1)))));
			} catch (Exception ex) {
			}
			if (res.size() == limit) {
				break;
			}
		}
		return res;
	}
}
