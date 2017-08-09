package viettel.nfw.social.google.entity;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CrawledResult implements Serializable {

    private static final long serialVersionUID = 4240867670694836839L;
    private int errorCode;
    private String errorDescription;
    private long startTime;
    private long endTime;
    private long crawledTime;
    private String accountCrawl;
    // data return
    private Set<String> foundProfileUrls;
    private Set<String> foundOutsideUrls;
    private Object crawledProfile;

    public int getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(int errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorDescription() {
        return errorDescription;
    }

    public void setErrorDescription(String errorDescription) {
        this.errorDescription = errorDescription;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public long getCrawledTime() {
        return crawledTime;
    }

    public void setCrawledTime(long crawledTime) {
        this.crawledTime = crawledTime;
    }

    public String getAccountCrawl() {
        return accountCrawl;
    }

    public void setAccountCrawl(String accountCrawl) {
        this.accountCrawl = accountCrawl;
    }

    public Set<String> getFoundProfileUrls() {
        return foundProfileUrls == null ? new HashSet<String>() : Collections.unmodifiableSet(foundProfileUrls);
    }

    public void setFoundProfileUrls(Set<String> foundProfileUrls) {
        this.foundProfileUrls = foundProfileUrls;
    }

    public Set<String> getFoundOutsideUrls() {
        return foundOutsideUrls == null ? new HashSet<String>() : Collections.unmodifiableSet(foundOutsideUrls);
    }

    public void setFoundOutsideUrls(Set<String> foundOutsideUrls) {
        this.foundOutsideUrls = foundOutsideUrls;
    }

    public Object getCrawledProfile() {
        return crawledProfile;
    }

    public void setCrawledProfile(Object crawledProfile) {
        this.crawledProfile = crawledProfile;
    }
}
