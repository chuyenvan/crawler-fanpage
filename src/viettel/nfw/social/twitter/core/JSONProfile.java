package viettel.nfw.social.twitter.core;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.google.gson.annotations.SerializedName;

public class JSONProfile {

    @SerializedName("id_str")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("screen_name")
    private String screenName;

    @SerializedName("location")
    private String location;

    @SerializedName("profile_location")
    private String profileLocation;

    @SerializedName("description")
    private String description;

    @SerializedName("followers_count")
    private long followersCount;

    @SerializedName("friends_count")
    private long friendsCount;

    @SerializedName("listed_count")
    private long listedCount;

    @SerializedName("favourites_count")
    private long favouritesCount;

    @SerializedName("statuses_count")
    private long statusesCount;

    @SerializedName("media_count")
    private long mediaCount;

    @SerializedName("protected")
    private boolean profileProtected;

    @SerializedName("lang")
    private String lang;

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
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the screenName
     */
    public String getScreenName() {
        return screenName;
    }

    /**
     * @param screenName the screenName to set
     */
    public void setScreenName(String screenName) {
        this.screenName = screenName;
    }

    /**
     * @return the location
     */
    public String getLocation() {
        return location;
    }

    /**
     * @param location the location to set
     */
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * @return the profileLocation
     */
    public String getProfileLocation() {
        return profileLocation;
    }

    /**
     * @param profileLocation the profileLocation to set
     */
    public void setProfileLocation(String profileLocation) {
        this.profileLocation = profileLocation;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        Document descDoc = Jsoup.parse(description);
        this.description = descDoc.text();
    }

    /**
     * @return the followersCount
     */
    public long getFollowersCount() {
        return followersCount;
    }

    /**
     * @param followersCount the followersCount to set
     */
    public void setFollowersCount(long followersCount) {
        this.followersCount = followersCount;
    }

    /**
     * @return the friendsCount
     */
    public long getFriendsCount() {
        return friendsCount;
    }

    /**
     * @param friendsCount the friendsCount to set
     */
    public void setFriendsCount(long friendsCount) {
        this.friendsCount = friendsCount;
    }

    /**
     * @return the listedCount
     */
    public long getListedCount() {
        return listedCount;
    }

    /**
     * @param listedCount the listedCount to set
     */
    public void setListedCount(long listedCount) {
        this.listedCount = listedCount;
    }

    /**
     * @return the favouritesCount
     */
    public long getFavouritesCount() {
        return favouritesCount;
    }

    /**
     * @param favouritesCount the favouritesCount to set
     */
    public void setFavouritesCount(long favouritesCount) {
        this.favouritesCount = favouritesCount;
    }

    /**
     * @return the statusesCount
     */
    public long getStatusesCount() {
        return statusesCount;
    }

    /**
     * @param statusesCount the statusesCount to set
     */
    public void setStatusesCount(long statusesCount) {
        this.statusesCount = statusesCount;
    }

    /**
     * @return the mediaCount
     */
    public long getMediaCount() {
        return mediaCount;
    }

    /**
     * @param mediaCount the mediaCount to set
     */
    public void setMediaCount(long mediaCount) {
        this.mediaCount = mediaCount;
    }

    /**
     * @return the profileProtected
     */
    public boolean isProfileProtected() {
        return profileProtected;
    }

    /**
     * @param profileProtected the profileProtected to set
     */
    public void setProfileProtected(boolean profileProtected) {
        this.profileProtected = profileProtected;
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

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return "Profile [id=" + id + ", name=" + name + ", screenName=" + screenName + ", location=" + location
                + ", profileLocation=" + profileLocation + ", description=" + description + ", followersCount="
                + followersCount + ", friendsCount=" + friendsCount + ", listedCount=" + listedCount
                + ", favouritesCount=" + favouritesCount + ", statusesCount=" + statusesCount + ", mediaCount="
                + mediaCount + ", profileProtected=" + profileProtected + ", lang=" + lang + "]";
    }

}
