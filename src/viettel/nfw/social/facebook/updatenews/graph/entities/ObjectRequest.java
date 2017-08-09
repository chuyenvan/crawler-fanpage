package viettel.nfw.social.facebook.updatenews.graph.entities;

import java.io.Serializable;

/**
 *
 * @author duongth5
 */
public class ObjectRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    public SocialType socialType;
    public String objectID;
    public ObjectType objectType;
    public long loopTimeTimeMillis;

    public ObjectRequest() {
    }

    public ObjectRequest(SocialType socialType, String objectID, ObjectType objectType, long loopTimeTimeMillis) {
        this.socialType = socialType;
        this.objectID = objectID;
        this.objectType = objectType;
        this.loopTimeTimeMillis = loopTimeTimeMillis;
    }

    @Override
    public String toString() {
        return "{" + socialType + " " + objectID + " " + objectType + " " + loopTimeTimeMillis + "}";
    }

}
