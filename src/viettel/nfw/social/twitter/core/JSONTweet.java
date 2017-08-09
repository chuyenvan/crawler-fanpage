package viettel.nfw.social.twitter.core;

import java.util.Date;

import com.google.gson.annotations.SerializedName;

public class JSONTweet {

    @SerializedName("id_str")
    private String id;

    @SerializedName("text")
    private String text;

    @SerializedName("created_at")
    private Date createdAt;

    @SerializedName("source")
    private String source;

    @SerializedName("user")
    private JSONProfile user;

    @SerializedName("retweet_count")
    private long retweetCount;

    @SerializedName("favorite_count")
    private long favoriteCount;

    @SerializedName("conversation_id")
    private String conversationId;

    @SerializedName("possibly_sensitive")
    private boolean possiblySensitive;

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
     * @return the retweetCount
     */
    public long getRetweetCount() {
        return retweetCount;
    }

    /**
     * @param retweetCount the retweetCount to set
     */
    public void setRetweetCount(long retweetCount) {
        this.retweetCount = retweetCount;
    }

    /**
     * @return the favoriteCount
     */
    public long getFavoriteCount() {
        return favoriteCount;
    }

    /**
     * @param favoriteCount the favoriteCount to set
     */
    public void setFavoriteCount(long favoriteCount) {
        this.favoriteCount = favoriteCount;
    }

    /**
     * @return the conversationId
     */
    public String getConversationId() {
        return conversationId;
    }

    /**
     * @param conversationId the conversationId to set
     */
    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    /**
     * @return the possiblySensitive
     */
    public boolean isPossiblySensitive() {
        return possiblySensitive;
    }

    /**
     * @param possiblySensitive the possiblySensitive to set
     */
    public void setPossiblySensitive(boolean possiblySensitive) {
        this.possiblySensitive = possiblySensitive;
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

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Tweet [id=" + id + ", text=" + text + ", createdAt=" + createdAt + ", source=" + source + ", user="
                + user + ", retweetCount=" + retweetCount + ", favoriteCount=" + favoriteCount + ", conversationId="
                + conversationId + ", possiblySensitive=" + possiblySensitive + ", lang=" + lang + ", userId=" + userId
                + "]";
    }
}
