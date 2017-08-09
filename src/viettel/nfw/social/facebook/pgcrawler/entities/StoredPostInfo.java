package viettel.nfw.social.facebook.pgcrawler.entities;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.hadoop.io.Writable;
import vn.itim.detector.Language;

/**
 *
 * @author Duong
 */
public class StoredPostInfo implements Writable {

	public static final long UNSET_TIME = -1;

	private String postId;
	private String actorProfileId;
	private String profileTimelineId;
	private String url;
	private Language contentLanguage;

	private long publishedTime;
	private long crawledTime;
	private long reCrawledTime;

	private long comments;
	private long likes;
	private long shares;
	private int insideLinksSize;
	private int outsideLinksSize;

	public StoredPostInfo() {
	}

	public StoredPostInfo(String postId, String actorProfileId, String profileTimelineId, String url, Language contentLanguage,
		long publishedTime, long crawledTime, long reCrawledTime,
		long comments, long likes, long shares, int insideLinksSize, int outsideLinksSize) {
		this.postId = postId;
		this.actorProfileId = actorProfileId;
		this.profileTimelineId = profileTimelineId;
		this.url = url;
		this.contentLanguage = contentLanguage;
		this.publishedTime = publishedTime;
		this.crawledTime = crawledTime;
		this.reCrawledTime = reCrawledTime;
		this.comments = comments;
		this.likes = likes;
		this.shares = shares;
		this.insideLinksSize = insideLinksSize;
		this.outsideLinksSize = outsideLinksSize;
	}

	public String getPostId() {
		return postId;
	}

	public void setPostId(String postId) {
		this.postId = postId;
	}

	public String getActorProfileId() {
		return actorProfileId;
	}

	public void setActorProfileId(String actorProfileId) {
		this.actorProfileId = actorProfileId;
	}

	public String getProfileTimelineId() {
		return profileTimelineId;
	}

	public void setProfileTimelineId(String profileTimelineId) {
		this.profileTimelineId = profileTimelineId;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public Language getContentLanguage() {
		return contentLanguage;
	}

	public void setContentLanguage(Language contentLanguage) {
		this.contentLanguage = contentLanguage;
	}

	public long getPublishedTime() {
		return publishedTime;
	}

	public void setPublishedTime(long publishedTime) {
		this.publishedTime = publishedTime;
	}

	public long getCrawledTime() {
		return crawledTime;
	}

	public void setCrawledTime(long crawledTime) {
		this.crawledTime = crawledTime;
	}

	public long getReCrawledTime() {
		return reCrawledTime;
	}

	public void setReCrawledTime(long reCrawledTime) {
		this.reCrawledTime = reCrawledTime;
	}

	public long getComments() {
		return comments;
	}

	public void setComments(long comments) {
		this.comments = comments;
	}

	public long getLikes() {
		return likes;
	}

	public void setLikes(long likes) {
		this.likes = likes;
	}

	public long getShares() {
		return shares;
	}

	public void setShares(long shares) {
		this.shares = shares;
	}

	public int getInsideLinksSize() {
		return insideLinksSize;
	}

	public void setInsideLinksSize(int insideLinksSize) {
		this.insideLinksSize = insideLinksSize;
	}

	public int getOutsideLinksSize() {
		return outsideLinksSize;
	}

	public void setOutsideLinksSize(int outsideLinksSize) {
		this.outsideLinksSize = outsideLinksSize;
	}

	@Override
	public void write(DataOutput d) throws IOException {
		d.writeUTF(postId);
		d.writeUTF(actorProfileId);
		d.writeUTF(profileTimelineId);
		d.writeUTF(url);
		if (contentLanguage == null) {
			d.writeInt(-1);
		} else {
			d.writeInt(contentLanguage.getId());
		}

		d.writeLong(publishedTime);
		d.writeLong(crawledTime);
		d.writeLong(reCrawledTime);

		d.writeLong(comments);
		d.writeLong(likes);
		d.writeLong(shares);
		d.writeInt(insideLinksSize);
		d.writeInt(outsideLinksSize);

	}

	@Override
	public void readFields(DataInput di) throws IOException {
		this.postId = di.readUTF();
		this.actorProfileId = di.readUTF();
		this.profileTimelineId = di.readUTF();
		this.url = di.readUTF();
		int lid = di.readInt();
		if (lid < 0) {
			this.contentLanguage = null;
		} else {
			this.contentLanguage = Language.getById(lid);
		}

		this.publishedTime = di.readLong();
		this.crawledTime = di.readLong();
		this.reCrawledTime = di.readLong();

		this.comments = di.readLong();
		this.likes = di.readLong();
		this.shares = di.readLong();
		this.insideLinksSize = di.readInt();
		this.outsideLinksSize = di.readInt();

	}

}
