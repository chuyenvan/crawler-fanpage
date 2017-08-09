package viettel.nfw.social.facebook.core;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.net.ssl.HttpsURLConnection;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.HttpResponseInfo;

/**
 *
 * @author duongth5
 */
public class HttpRequest {

    public static final String USER_AGENT_FIREFOX = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:32.0) Gecko/20100101 Firefox/32.0";
    public static final String GET = "GET";
    public static final String POST = "POST";
    public static final String HOST = "Host";
    public static final String USER_AGENT = "User-Agent";
    public static final String ACCEPT = "Accept";
    public static final String ACCEPT_ENCODING = "Accept-Encoding";
    public static final String ACCEPT_LANGUAGE = "Accept-Language";
    public static final String REFERER = "Referer";
    public static final String COOKIE = "Cookie";
    public static final String CONNECTION = "Connection";
    public static final String CONNECTION_KEEP_ALIVE = "keep-alive";
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";
    public static final String CONTENT_LENGTH = "Content-Length";
    public static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"utf-8\"?>";
    public static final String ENCODING_GZIP_DEFLATE = "gzip, deflate";
    public static final String LOCATION = "Location";

    private static final int BUFFER_SIZE = 4096;

    /**
     * Logger for HttpRequest class
     */
    private static final Logger LOG = LoggerFactory.getLogger(HttpRequest.class);
    /**
     * Cookie manager for requests
     */
    private final CookieManager cookieManager;
    /**
     * HTTP Connection
     */
    private HttpsURLConnection conn;
    /**
     * User Agent for account
     */
    private String userAgent;

    public HttpRequest() {
        this(null, USER_AGENT_FIREFOX);
    }

    public HttpRequest(CookieManager cookieManager, String userAgent) {
        this.cookieManager = cookieManager;
        if (StringUtils.isEmpty(userAgent)) {
            this.userAgent = USER_AGENT_FIREFOX;
        } else {
            this.userAgent = userAgent;
        }
        this.conn = null;
    }

    /**
     * Get cookies string of a URL from cookie manager.
     *
     * @param url URL that want to get cookies
     * @param cookieManager Cookie Manager
     * @return cookies string
     */
    public static String getCookieString(String url, CookieManager cookieManager) {
        try {
            URI uri = new URI(url);
            StringBuilder builder = new StringBuilder();
            Map<String, List<String>> headers = cookieManager.get(uri, new HashMap<String, List<String>>());
            List<String> cookies = headers.get("Cookie");
            for (String cookie : cookies) {
                builder.append(cookie).append("; ");
            }
            return builder.toString();
        } catch (URISyntaxException | IOException ex) {
            LOG.warn(ex.getMessage(), ex);
            return "";
        }
    }

    /**
     * Send HTTP POST request to server
     *
     * @param url URL
     * @param postParams parameters
     * @param referer
     * @param proxy the Proxy through which this connection will be made. If direct connection is desired, NULL should
     * be specified.
     * @return HTTP response information: header, status code, response body
     * @throws IOException
     */
    public HttpResponseInfo post(String url, String postParams, String referer, Proxy proxy) throws IOException {

        // Init connection to URL
        URL obj = new URL(url);
        if (proxy == null) {
            conn = (HttpsURLConnection) obj.openConnection();
        } else {
            conn = (HttpsURLConnection) obj.openConnection(proxy);
        }

        // Acts like a browser
        conn.setUseCaches(false);
        conn.setRequestMethod(POST);

        conn.setRequestProperty(HOST, "m.facebook.com");
        conn.setRequestProperty(USER_AGENT, this.userAgent);
        conn.setRequestProperty(ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty(ACCEPT_LANGUAGE, "en-US,en;q=0.5");
        if (StringUtils.isEmpty(referer)) {
            conn.setRequestProperty(REFERER, "https://m.facebook.com/");
        } else {
            conn.setRequestProperty(REFERER, referer);
        }
        String cookieStr = getCookieString(url, cookieManager);
        conn.setRequestProperty(COOKIE, cookieStr);
        conn.setRequestProperty(CONNECTION, CONNECTION_KEEP_ALIVE);
        conn.setRequestProperty(CONTENT_TYPE, CONTENT_TYPE_FORM);
        conn.setRequestProperty(CONTENT_LENGTH, Integer.toString(postParams.length()));

        conn.setDoOutput(true);
        conn.setDoInput(true);
        // stop set follow redirect
        conn.setInstanceFollowRedirects(false);

        // conn.setAllowUserInteraction(true);
        // HttpURLConnection.setFollowRedirects(false);
        // HttpsURLConnection.setFollowRedirects(false);
        // conn.setUseCaches(false);
        // Send post request
        try (DataOutputStream wr = new DataOutputStream(conn.getOutputStream())) {
            wr.writeBytes(postParams);
            wr.flush();
        }

        // get HTTP response information
        HttpResponseInfo response = new HttpResponseInfo();

        int status = conn.getResponseCode();
        LOG.debug("Sending 'POST' request to URL: " + url);
        LOG.debug("Post parameters: " + postParams);
        LOG.debug("Response Code: " + status);

        response.setStatus(status);
        response.setHeaders(conn.getHeaderFields());

        String contentType = conn.getHeaderField(CONTENT_TYPE);
        String charset = null;

        for (String param : contentType.replace(" ", "").split(";")) {
            if (param.startsWith("charset=")) {
                charset = param.split("=", 2)[1];
                break;
            }
        }

        if (charset != null) {
            LOG.debug("Charset: " + charset);
            StringBuilder sb;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), charset))) {
                String inputLine;
                sb = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    sb.append(inputLine);
                }
            }

            String body = sb.toString();
            if (indexOfString(body, XML_DECLARATION) == 0) {
                body = body.substring(XML_DECLARATION.length());
            }
            response.setBody(body);
        } else {
            // TODO It's likely binary content, use InputStream/OutputStream.
        }

        try {
            cookieManager.put(new URI(url), conn.getHeaderFields());
        } catch (URISyntaxException e) {
            LOG.warn("Error in set cookie", e);
        }

        return response;
    }

    /**
     * Send HTTP GET request to server
     *
     * @param url URL
     * @param proxy the Proxy through which this connection will be made. If direct connection is desired, NULL should
     * be specified.
     * @return HTTP response information: header, status code, response body
     * @throws IOException
     */
    public HttpResponseInfo get(String url, Proxy proxy) throws IOException {

        // Init connection to URL
        URL obj = new URL(url);
        if (proxy == null) {
            conn = (HttpsURLConnection) obj.openConnection();
        } else {
            conn = (HttpsURLConnection) obj.openConnection(proxy);
        }

        // default is GET
        conn.setRequestMethod(GET);
        conn.setUseCaches(false);

        // act like a browser
        conn.setRequestProperty(USER_AGENT, this.userAgent);
        conn.setRequestProperty(ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty(ACCEPT_LANGUAGE, "en-US,en;q=0.5");
        conn.setRequestProperty(ACCEPT_ENCODING, ENCODING_GZIP_DEFLATE);
        conn.setRequestProperty(CONNECTION, CONNECTION_KEEP_ALIVE);
        conn.setRequestProperty("Host", "m.facebook.com");
        String cookie = getCookieString(url, cookieManager);
        if (StringUtils.isNotEmpty(cookie)) {
            LOG.info("cookie: {}", cookie);
            conn.setRequestProperty(COOKIE, cookie);
        }

        int status = conn.getResponseCode();
        LOG.debug("Sending 'GET' request to URL: " + url);

        boolean redirect = false;

        // normally, 3xx is redirect
        if (status != HttpURLConnection.HTTP_OK) {
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER) {
                redirect = true;
            }
        }

        LOG.debug("Response Code: " + status);

        if (redirect) {

            // get redirect url from "location" header field
            String newUrl = conn.getHeaderField(LOCATION);

            // open the new connnection again
            if (proxy == null) {
                conn = (HttpsURLConnection) new URL(newUrl).openConnection();
            } else {
                conn = (HttpsURLConnection) new URL(newUrl).openConnection(proxy);
            }

            // default is GET
            conn.setRequestMethod(GET);
            conn.setUseCaches(false);

            // act like a browser
            conn.setRequestProperty(USER_AGENT, this.userAgent);
            conn.setRequestProperty(ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty(ACCEPT_LANGUAGE, "en-US,en;q=0.5");
            conn.setRequestProperty(ACCEPT_ENCODING, ENCODING_GZIP_DEFLATE);
            conn.setRequestProperty(CONNECTION, CONNECTION_KEEP_ALIVE);
            conn.setRequestProperty("Host", "m.facebook.com");
            String redirectCookie = getCookieString(url, cookieManager);
            if (StringUtils.isNotEmpty(redirectCookie)) {
                LOG.info("cookie: {}", redirectCookie);
                conn.setRequestProperty(COOKIE, cookie);
            }

            LOG.debug("Redirect to URL : " + newUrl);
        }

        // get HTTP response information
        HttpResponseInfo response = new HttpResponseInfo();

        response.setStatus(status);
        response.setHeaders(conn.getHeaderFields());

        String statusStr = String.valueOf(status);
        if (StringUtils.startsWith(statusStr, "4") || StringUtils.startsWith(statusStr, "5")) {
            response.setBody("");
            LOG.info("HTTP response status: {}", statusStr);
        } else {
            String contentType = conn.getHeaderField(CONTENT_TYPE);
            String charset = null;

            for (String param : contentType.replace(" ", "").split(";")) {
                if (param.startsWith("charset=")) {
                    charset = param.split("=", 2)[1];
                    break;
                }
            }

            if (charset != null) {
                LOG.debug("Charset: " + charset);
                StringBuilder sb;
                try (BufferedReader in = new BufferedReader(new InputStreamReader(decompressStream(conn.getInputStream()), charset))) {
                    String inputLine;
                    sb = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        sb.append(inputLine);
                    }
                }
                String body = sb.toString();
                if (indexOfString(body, XML_DECLARATION) == 0) {
                    body = body.substring(XML_DECLARATION.length());
                }
                response.setBody(body);
            } else {
                // TODO It's likely binary content, use InputStream/OutputStream.
            }
        }

        // Get the response cookies
        try {
            cookieManager.put(new URI(url), conn.getHeaderFields());
        } catch (URISyntaxException e) {
            LOG.warn("Error in set cookie", e);
        }

        return response;
    }

    /**
     * Indexing String in a Document
     *
     * @param rawHtml input document
     * @param searchStr searched string
     * @return position of string
     */
    private static int indexOfString(String rawHtml, String searchStr) {
        return rawHtml.indexOf(searchStr);
    }

    /**
     * Decompress GZIP or Non-GZIP stream
     *
     * @param input Input stream
     * @return decompressed stream
     * @throws IOException
     */
    private static InputStream decompressStream(InputStream input) throws IOException {
        PushbackInputStream pb = new PushbackInputStream(input, 2);
        byte[] signature = new byte[2];
        pb.read(signature);
        pb.unread(signature);
        if (signature[0] == (byte) 0x1f && signature[1] == (byte) 0x8b) {
            LOG.debug("gzip encoding");
            return new GZIPInputStream(pb);
        } else {
            LOG.debug("NOT gzip encoding");
            return pb;
        }
    }

    public void getImage(String url, String referer, boolean isSave, String saveDir, Proxy proxy) throws IOException {
        // Init connection to URL
        URL obj = new URL(url);
        String host = obj.getHost();
        if (proxy == null) {
            conn = (HttpsURLConnection) obj.openConnection();
        } else {
            conn = (HttpsURLConnection) obj.openConnection(proxy);
        }

        // default is GET
        conn.setRequestMethod(GET);
        conn.setUseCaches(false);

        // act like a browser
        conn.setRequestProperty(USER_AGENT, this.userAgent);
        conn.setRequestProperty(ACCEPT, "image/png,image/*;q=0.8,*/*;q=0.5");
        conn.setRequestProperty(ACCEPT_LANGUAGE, "en-US,en;q=0.5");
        conn.setRequestProperty(ACCEPT_ENCODING, ENCODING_GZIP_DEFLATE);
        conn.setRequestProperty(CONNECTION, CONNECTION_KEEP_ALIVE);
        conn.setRequestProperty("Host", host);
        conn.setRequestProperty(REFERER, referer);

        int status = conn.getResponseCode();
        LOG.debug("Sending 'GET' request to URL: " + url);
        if (status == HttpsURLConnection.HTTP_OK) {
            String fileName = "";
            String disposition = conn.getHeaderField("Content-Disposition");
            String contentType = conn.getContentType();
            int contentLength = conn.getContentLength();

            if (StringUtils.isNotEmpty(disposition)) {
                // extracts file name from header field
                int index = disposition.indexOf("filename=");
                if (index > 0) {
                    fileName = disposition.substring(index + 10, disposition.length() - 1);
                }
            } else {
                // extracts file name from URL
                String fileURL = obj.getPath();
                fileName = fileURL.substring(fileURL.lastIndexOf("/") + 1, fileURL.length());
            }

            LOG.info("Content-Type = " + contentType);
            LOG.info("Content-Disposition = " + disposition);
            LOG.info("Content-Length = " + contentLength);
            LOG.info("fileName = " + fileName);

            // opens input stream from the HTTP connection
            InputStream inputStream = conn.getInputStream();
            if (isSave) {
                String saveFilePath = saveDir + File.separator + fileName;

                // opens an output stream to save into file
                FileOutputStream outputStream = new FileOutputStream(saveFilePath);

                int bytesRead = -1;
                byte[] buffer = new byte[BUFFER_SIZE];
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }

                outputStream.close();
                inputStream.close();

                LOG.info("File downloaded");
            } else {
                int bytesRead = -1;
                byte[] buffer = new byte[BUFFER_SIZE];
                while ((bytesRead = inputStream.read(buffer)) != -1) {

                }
                inputStream.close();
            }

        } else {
            LOG.warn("No file to download. Server replied HTTP code: {}", status);
        }
    }
}
