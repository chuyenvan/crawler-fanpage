package viettel.nfw.social.facebook.updatenews.tool;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class FindFacebookId {

    private static final Logger LOG = LoggerFactory.getLogger(FindFacebookId.class);

    private static String getFromLookUpId(String facebookUrl) throws MalformedURLException, ProtocolException, IOException {
        // Init connection to URL
        String url = "https://lookup-id.com";

        List<String> paramList = new ArrayList<>();
        paramList.add(URLEncoder.encode("check", "UTF-8") + "=" + URLEncoder.encode("Lookup", "UTF-8"));
        paramList.add(URLEncoder.encode("fburl", "UTF-8") + "=" + URLEncoder.encode(facebookUrl, "UTF-8"));

        StringBuilder result = new StringBuilder();
        for (String param : paramList) {
            if (result.length() == 0) {
                result.append(param);
            } else {
                result.append("&").append(param);
            }
        }
        String postParams = result.toString();

        URL obj = new URL(url);
        HttpsURLConnection conn;
        Proxy proxy = null;
        if (proxy == null) {
            conn = (HttpsURLConnection) obj.openConnection();
        } else {
            conn = (HttpsURLConnection) obj.openConnection(proxy);
        }

        // Acts like a browser
        conn.setUseCaches(false);
        conn.setRequestMethod("POST");

        conn.setRequestProperty("Host", "lookup-id.com");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.0");
        conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        conn.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
        conn.setRequestProperty("Referer", "https://lookup-id.com/");
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
        LOG.debug("Sending 'POST' request to URL: " + url);
        LOG.debug("Post parameters: " + postParams);
        LOG.debug("Response Code: " + status);

        StringBuilder sb;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String inputLine;
            sb = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                sb.append(inputLine);
            }
        }
        String body = sb.toString();

        return body;
    }
    
    public static void main(String[] args){
        try {
            String facebookUrl = "https://www.facebook.com/cuhiep";
            String response = getFromLookUpId(facebookUrl);
            LOG.info(response);
        } catch (ProtocolException ex) {
            LOG.error(ex.getMessage(), ex);
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }
}
