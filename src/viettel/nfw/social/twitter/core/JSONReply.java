package viettel.nfw.social.twitter.core;

import java.util.Date;

import com.google.gson.annotations.SerializedName;

public class JSONReply {

    @SerializedName("id_str")
    private String id;

    @SerializedName("text")
    private String text;

    @SerializedName("source")
    private String source;

    @SerializedName("in_reply_to_status_id_str")
    private String inReplyToStatusId;

    @SerializedName("in_reply_to_user_id_str")
    private String inReplyToUserId;

    @SerializedName("in_reply_to_screen_name")
    private String inReplyToScreenName;

    @SerializedName("user")
    private JSONProfile user;

    @SerializedName("created_at")
    private Date createdAt;

    @SerializedName("lang")
    private String lang;

    @SerializedName("userId")
    private String userId;

    /**
     * @return the id
     */
    public String getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * @param text the text to set
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * @return the source
     */
    public String getSource() {
        return source;
    }

    /**
     * @param source the source to set
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * @return the inReplyToStatusId
     */
    public String getInReplyToStatusId() {
        return inReplyToStatusId;
    }

    /**
     * @param inReplyToStatusId the inReplyToStatusId to set
     */
    public void setInReplyToStatusId(String inReplyToStatusId) {
        this.inReplyToStatusId = inReplyToStatusId;
    }

    /**
     * @return the inReplyToUserId
     */
    public String getInReplyToUserId() {
        return inReplyToUserId;
    }

    /**
     * @param inReplyToUserId the inReplyToUserId to set
     */
    public void setInReplyToUserId(String inReplyToUserId) {
        this.inReplyToUserId = inReplyToUserId;
    }

    /**
     * @return the inReplyToScreenName
     */
    public String getInReplyToScreenName() {
        return inReplyToScreenName;
    }

    /**
     * @param inReplyToScreenName the inReplyToScreenName to set
     */
    public void setInReplyToScreenName(String inReplyToScreenName) {
        this.inReplyToScreenName = inReplyToScreenName;
    }

    /**
     * @return the user
     */
    public JSONProfile getUser() {
        return user;
    }

    /**
     * @param user the user to set
     */
    public void setUser(JSONProfile user) {
        this.user = user;
    }

    /**
     * @return the createdAt
     */
    public Date getCreatedAt() {
        return createdAt;
    }

    /**
     * @param createdAt the createdAt to set
     */
    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * @return the lang
     */
    public String getLang() {
        return lang;
    }

    /**
     * @param lang the lang to set
     */
    public void setLang(String lang) {
        this.lang = lang;
    }

    /**
     * @return the userId
     */
    public String getUserId() {
        return userId;
    }

    /**
     * @param userId the userId to set
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Reply [id=" + id + ", text=" + text + ", source=" + source + ", inReplyToStatusId=" + inReplyToStatusId
                + ", inReplyToUserId=" + inReplyToUserId + ", inReplyToScreenName=" + inReplyToScreenName + ", user="
                + user + ", createdAt=" + createdAt + ", lang=" + lang + ", userId=" + userId + "]";
    }
}
