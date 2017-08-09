package viettel.nfw.social.google.core;

import java.io.BufferedReader;
import java.io.DataOutputStream;
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
import vn.viettel.social.fb.utils.FacebookURL;
import vn.viettel.social.gp.utils.GooglePlusURL;
import vn.viettel.social.twitter.utils.TwitterURL;

import vn.viettel.social.utils.consts.Header;
import vn.viettel.social.utils.consts.Method;
import vn.viettel.social.utils.consts.SCommon;

/**
 * Create GET/POST HTTP request to targeted URL
 *
 * @author duongth5
 * @version 1.0
 * @since 1.0
 */
public class HttpRequest {

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

    public static final int SOCIAL_TYPE_FACEBOOK = 1;
    public static final int SOCIAL_TYPE_TWITTER = 2;
    public static final int SOCIAL_TYPE_GOOGLE_PLUS = 3;

    public HttpRequest() {
        this(null);
    }

    public HttpRequest(CookieManager cookieManager) {
        this.cookieManager = cookieManager;
        this.conn = null;
        this.userAgent = Header.Value.USER_AGENT_FIREFOX;
    }

    public HttpRequest(CookieManager cookieManager, String userAgent) {
        this.cookieManager = cookieManager;
        if (StringUtils.isEmpty(userAgent)) {
            this.userAgent = Header.Value.USER_AGENT_FIREFOX;
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
     * @param socialType social types: Facebook, Google Plus, Twitter
     * @param accept Accept Header value
     * @param referer Referer Header value
     * @param proxy the Proxy through which this connection will be made. If direct connection is desired, NULL should
     * be specified.
     * @return HTTP response information: header, status code, response body
     * @throws IOException
     */
    public HttpResponseInfo post(String url, String postParams, int socialType, String accept, String referer, Proxy proxy) throws IOException {

        // Init connection to URL
        URL obj = new URL(url);
        if (proxy == null) {
            conn = (HttpsURLConnection) obj.openConnection();
        } else {
            conn = (HttpsURLConnection) obj.openConnection(proxy);
        }

        // Acts like a browser
        conn.setUseCaches(false);
        conn.setRequestMethod(Method.POST);

        switch (socialType) {
            case SOCIAL_TYPE_FACEBOOK:
                conn.setRequestProperty(Header.HOST, FacebookURL.HOST_MOBILE);
                conn.setRequestProperty(Header.USER_AGENT, this.userAgent);
                conn.setRequestProperty(Header.ACCEPT, Header.Value.ACCEPT_DEFAULT_2);
                conn.setRequestProperty(Header.ACCEPT_LANGUAGE, Header.Value.LANGUAGE_EN_US_Q_0_8);
                conn.setRequestProperty(Header.REFERER, FacebookURL.MOBILE_BASE_URL);
                break;
            case SOCIAL_TYPE_GOOGLE_PLUS:
                conn.setRequestProperty(Header.HOST, GooglePlusURL.HOST_ACCOUNTS_GOOGLE);
                conn.setRequestProperty(Header.USER_AGENT, this.userAgent);
                conn.setRequestProperty(Header.ACCEPT, Header.Value.ACCEPT_DEFAULT_2);
                conn.setRequestProperty(Header.ACCEPT_LANGUAGE, Header.Value.LANGUAGE_EN_US_Q_0_5);
                conn.setRequestProperty(Header.REFERER, GooglePlusURL.LOGIN_AUTH_URL);
                break;
            case SOCIAL_TYPE_TWITTER:
                conn.setRequestProperty(Header.HOST, TwitterURL.HOST_MOBILE);
                conn.setRequestProperty(Header.USER_AGENT, Header.Value.USER_AGENT_FIREFOX);
                conn.setRequestProperty(Header.ACCEPT, accept);
                conn.setRequestProperty(Header.ACCEPT_LANGUAGE, Header.Value.LANGUAGE_EN_US_Q_0_5);
                conn.setRequestProperty(Header.REFERER, referer);
                break;
        }

        conn.setRequestProperty(Header.COOKIE, getCookieString(url, cookieManager));
        conn.setRequestProperty(Header.CONNECTION, Header.Value.CONNECTION_KEEP_ALIVE);
        conn.setRequestProperty(Header.CONTENT_TYPE, Header.Value.CONTENT_TYPE_FORM);
        conn.setRequestProperty(Header.CONTENT_LENGTH, Integer.toString(postParams.length()));

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

        String contentType = conn.getHeaderField(Header.CONTENT_TYPE);
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
            if (indexOfString(body, SCommon.XML_DECLARATION) == 0) {
                body = body.substring(SCommon.XML_DECLARATION.length());
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

    /**
     * Send HTTP GET request to server
     *
     * @param url URL
     * @param socialType social types: Facebook, Google Plus, Twitter
     * @param proxy the Proxy through which this connection will be made. If direct connection is desired, NULL should
     * be specified.
     * @return HTTP response information: header, status code, response body
     * @throws IOException
     */
    public HttpResponseInfo get(String url, int socialType, Proxy proxy) throws IOException {

        String defaultUserAgent = Header.Value.USER_AGENT_FIREFOX;
        switch (socialType) {
            case SOCIAL_TYPE_FACEBOOK:
                defaultUserAgent = this.userAgent;
                break;
            case SOCIAL_TYPE_GOOGLE_PLUS:
                defaultUserAgent = this.userAgent;
                break;
            case SOCIAL_TYPE_TWITTER:
                defaultUserAgent = Header.Value.USER_AGENT_FIREFOX;
                break;
        }

        // Init connection to URL
        URL obj = new URL(url);
        if (proxy == null) {
            conn = (HttpsURLConnection) obj.openConnection();
        } else {
            conn = (HttpsURLConnection) obj.openConnection(proxy);
        }

        // default is GET
        conn.setRequestMethod(Method.GET);
        conn.setUseCaches(false);

        // act like a browser
        conn.setRequestProperty(Header.USER_AGENT, defaultUserAgent);
        conn.setRequestProperty(Header.ACCEPT, Header.Value.ACCEPT_DEFAULT);
        conn.setRequestProperty(Header.ACCEPT_LANGUAGE, Header.Value.LANGUAGE_EN_US_Q_0_5);
        conn.setRequestProperty(Header.ACCEPT_ENCODING, Header.Value.ENCODING_GZIP_DEFLATE);
        conn.setRequestProperty(Header.CONNECTION, Header.Value.CONNECTION_KEEP_ALIVE);
        conn.setRequestProperty(Header.COOKIE, getCookieString(url, cookieManager));
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
            String newUrl = conn.getHeaderField(Header.LOCATION);

            // open the new connnection again
            if (proxy == null) {
                conn = (HttpsURLConnection) new URL(newUrl).openConnection();
            } else {
                conn = (HttpsURLConnection) new URL(newUrl).openConnection(proxy);
            }

            // default is GET
            conn.setRequestMethod(Method.GET);
            conn.setUseCaches(false);

            // act like a browser
            conn.setRequestProperty(Header.USER_AGENT, defaultUserAgent);
            conn.setRequestProperty(Header.ACCEPT, Header.Value.ACCEPT_DEFAULT);
            conn.setRequestProperty(Header.ACCEPT_LANGUAGE, Header.Value.LANGUAGE_EN_US_Q_0_5);
            conn.setRequestProperty(Header.ACCEPT_ENCODING, Header.Value.ENCODING_GZIP_DEFLATE);
            conn.setRequestProperty(Header.CONNECTION, Header.Value.CONNECTION_KEEP_ALIVE);
            conn.setRequestProperty(Header.COOKIE, getCookieString(url, cookieManager));

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
            String contentType = conn.getHeaderField(Header.CONTENT_TYPE);
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
                try (BufferedReader in = new BufferedReader(new InputStreamReader(decompressStream(conn.getInputStream()),
                        charset))) {
                    String inputLine;
                    sb = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        sb.append(inputLine);
                    }
                }
                String body = sb.toString();
                if (indexOfString(body, SCommon.XML_DECLARATION) == 0) {
                    body = body.substring(SCommon.XML_DECLARATION.length());
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

}
