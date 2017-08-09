package viettel.nfw.forums;

import java.io.Serializable;

/**
 *
 * @author duongth5
 */
public class ThreadInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    public String threadId;
    public String threadUrl;
    public int lastPage;
    public long lastUpdate;
    public int numberReplies;
    public int numberViews;

    public ThreadInfo() {
    }

    @Override
    public String toString() {
        return "{" + threadId + " " + threadUrl + " " + lastPage + " " + lastUpdate + " " + numberReplies + " " + numberViews + '}';
    }

}
