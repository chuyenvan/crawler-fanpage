package viettel.nfw.social.utils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 *
 * @author duongth5
 */
public class HttpResponseInfo {

    /**
     * Response status code
     */
    private int status;
    /**
     * Response header
     */
    private Map<String, List<String>> headers;
    /**
     * Response body
     */
    private String body;

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public Map<String, List<String>> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    public void setHeaders(Map<String, List<String>> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }
}
