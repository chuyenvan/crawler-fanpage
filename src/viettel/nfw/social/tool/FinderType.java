package viettel.nfw.social.tool;

/**
 *
 * @author duongth5
 */
public enum FinderType {

    SITE_LOOKUPID("lookup-id.com"),
    SITE_FINDMYFBID("findmyfbid.com"),
    SITE_FACEBOOK_WEB("www.facebook.com");

    private final String desc;

    private FinderType(String desc) {
        this.desc = desc;
    }

    public String getDesc() {
        return desc;
    }

}
