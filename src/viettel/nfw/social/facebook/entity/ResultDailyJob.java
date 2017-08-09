package viettel.nfw.social.facebook.entity;

/**
 *
 * @author duongth5
 */
public class ResultDailyJob {

    public enum StopType {

        NORMAL, SUDDEN_DEATH
    }

    public boolean isStop;
    public StopType stopType;

}
