package viettel.nfw.social.facebook.entity;

/**
 *
 * @author duongth5
 */
public interface IActivity {

    public void setMethod();

    public void run();

    public AccountStatus getStatus();
}
