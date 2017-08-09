package viettel.nfw.social.facebook.entity;

/**
 *
 * @author duongth5
 */
public enum Activity {

    POST_MY_STATUS(201, 50, 3, "Update my own status"),
    POST_TO_GROUP(202, 20, 2, "Post status to a groups"),
    VISIT_MY_NEWS_FEED(203, 60, 4, "Surf my news feed to read some posts"),
    VISIT_MY_TIMELINE(204, 30, 3, "Surf my timeline"),
    VISIT_OTHER_PROFILE(205, 5, 4, "Visit other profile"),
    COMMENT_POST(206, 5, 2, "Comment to a post"),
    LIKE_POST(207, 10, 6, "Like a post"),
    SHARE_POST(208, 10, 3, "Share a post"),
    READ_MY_NOTIFICATION(210, 20, 4, "Read my notification"),
    MAKE_FRIENDS(211, 20, 6, "Add friends, follow friends"),
    READ_MY_FRIENDS_TAB(212, 30, 3, "Read my friend"),
    JOIN_GROUP_PAGE(213, 30, 2, "Join a group or Like a Page");

    private final int id;
    private final String description;
    private final int rate;
    private final int limitNumberPerDay;

    private Activity(int id, int rate, int limitNumberPerDay, String description) {
        this.id = id;
        this.description = description;
        this.rate = rate;
        this.limitNumberPerDay = limitNumberPerDay;
    }

    public int getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public int getRate() {
        return rate;
    }

    public int getLimitNumberPerDay() {
        return limitNumberPerDay;
    }

    @Override
    public String toString() {
        return "Activity{" + "id=" + id + ", rate=" + rate + ", limitNumberPerDay=" + limitNumberPerDay + ", description=" + description + '}';
    }

}
