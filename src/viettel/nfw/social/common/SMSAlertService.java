package viettel.nfw.social.common;

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

            conn.setRequestProperty("Authorization", userNamePasswordBase64(username, password));
            conn.connect();

            String jsonStr = JSON.encode(message);
            System.out.println(jsonStr);
            byte[] outputBytes = jsonStr.getBytes("UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write(outputBytes);
            os.close();

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

    public static void main(String[] args) {
        SMSAlertService.SMSRequest message = new SMSAlertService.SMSRequest();
        message.mobile = "+84988181941";
        message.sms = "Crawler_Services_Graph test message from duongth5";

        offer(SMS_GATEWAY_INTERNAL, USERNAME, PASSWORD, message);
    }
}
