package vn.viettel.social.fb.test;

import java.io.Serializable;

/**
 *
 * @author duongth5
 */
public class FbUrlToHtml implements Serializable {

    private String rawUrl;
    private String rawHtml;
    private long crawledTime;

    public FbUrlToHtml() {
    }

    public FbUrlToHtml(String rawUrl, String rawHtml, long crawledTime) {
        this.rawUrl = rawUrl;
        this.rawHtml = rawHtml;
        this.crawledTime = crawledTime;
    }

    public String getRawUrl() {
        return rawUrl;
    }

    public void setRawUrl(String rawUrl) {
        this.rawUrl = rawUrl;
    }

    public String getRawHtml() {
        return rawHtml;
    }

    public void setRawHtml(String rawHtml) {
        this.rawHtml = rawHtml;
    }

    public long getCrawledTime() {
        return crawledTime;
    }

    public void setCrawledTime(long crawledTime) {
        this.crawledTime = crawledTime;
    }

}
