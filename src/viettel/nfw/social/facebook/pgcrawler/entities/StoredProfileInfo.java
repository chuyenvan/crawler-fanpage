package viettel.nfw.social.facebook.pgcrawler.entities;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.apache.hadoop.io.Writable;
import viettel.nfw.social.facebook.updatenews.graph.entities.ProfileType;
import vn.itim.detector.Language;

/**
 *
 * @author duongth5
 */
public class StoredProfileInfo implements Writable {

	public static final long UNSET_TIME = -1;

	private String id;
	private String username;
	private String fullname;
	private String url;
	private String description;
	private long likesOrMembers;
	private Language language;
	private ProfileType profileType;
	private long postFrequency;
	private long firstCrawlingTime;
	private long lastCrawlingInfoTime;
	private long lastCrawlingTimelineTime;
	private long lastSuccessCrawlingTimelineTime;

	private int crawledPostsSize;
	private String[] crawledPostIds;

	public static StoredProfileInfo buildStoredProfileInfo(String id, String username, String fullname, String url,
			long likesOrMembers, Language language, ProfileType profileType, long postFrequency) {
		return new StoredProfileInfo(id, username, fullname, url, "", likesOrMembers, language, profileType,
				postFrequency, UNSET_TIME, UNSET_TIME, UNSET_TIME, UNSET_TIME, null);
	}

	public StoredProfileInfo() {
	}

	public StoredProfileInfo(String id) {
		this.id = id;
		this.username = "";
		this.fullname = "";
		this.url = "";
		this.description = "";
		this.likesOrMembers = -1;
		this.language = Language.UNKNOWN;
		this.profileType = ProfileType.UNKNOWN;
		this.postFrequency = -1;
		this.firstCrawlingTime = UNSET_TIME;
		this.lastCrawlingInfoTime = UNSET_TIME;
		this.lastCrawlingTimelineTime = UNSET_TIME;
		this.lastSuccessCrawlingTimelineTime = UNSET_TIME;
		this.crawledPostsSize = 0;
		this.crawledPostIds = null;
	}

	public StoredProfileInfo(String id, String username, String fullname, String url, String description,
			long likesOrMembers, Language language, ProfileType profileType, long postFrequency, long firstCrawlingTime,
			long lastCrawlingInfoTime, long lastCrawlingTimelineTime, long lastSuccessCrawlingTimelineTime, String[] crawledPostIds) {
		this.id = id;
		this.username = username;
		this.fullname = fullname;
		this.url = url;
		this.description = description;
		this.likesOrMembers = likesOrMembers;
		this.language = language;
		this.profileType = profileType;
		this.postFrequency = postFrequency;
		this.firstCrawlingTime = firstCrawlingTime;
		this.lastCrawlingInfoTime = lastCrawlingInfoTime;
		this.lastCrawlingTimelineTime = lastCrawlingTimelineTime;
		this.lastSuccessCrawlingTimelineTime = lastSuccessCrawlingTimelineTime;
		this.crawledPostIds = crawledPostIds;
	}

	public String getId() {
		return id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getFullname() {
		return fullname;
	}

	public void setFullname(String fullname) {
		this.fullname = fullname;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public long getLikesOrMembers() {
		return likesOrMembers;
	}

	public void setLikesOrMembers(long likesOrMembers) {
		this.likesOrMembers = likesOrMembers;
	}

	public Language getLanguage() {
		return language;
	}

	public void setLanguage(Language language) {
		this.language = language;
	}

	public ProfileType getProfileType() {
		return profileType;
	}

	public void setProfileType(ProfileType profileType) {
		this.profileType = profileType;
	}

	public long getPostFrequency() {
		return postFrequency;
	}

	public void setPostFrequency(long postFrequency) {
		this.postFrequency = postFrequency;
	}

	public long getFirstCrawlingTime() {
		return firstCrawlingTime;
	}

	public void setFirstCrawlingTime(long firstCrawlingTime) {
		this.firstCrawlingTime = firstCrawlingTime;
	}

	public long getLastCrawlingInfoTime() {
		return lastCrawlingInfoTime;
	}

	public void setLastCrawlingInfoTime(long lastCrawlingInfoTime) {
		this.lastCrawlingInfoTime = lastCrawlingInfoTime;
	}

	public long getLastCrawlingTimelineTime() {
		return lastCrawlingTimelineTime;
	}

	public void setLastCrawlingTimelineTime(long lastCrawlingTimelineTime) {
		this.lastCrawlingTimelineTime = lastCrawlingTimelineTime;
	}

	public long getLastSuccessCrawlingTimelineTime() {
		return lastSuccessCrawlingTimelineTime;
	}

	public void setLastSuccessCrawlingTimelineTime(long lastSuccessCrawlingTimelineTime) {
		this.lastSuccessCrawlingTimelineTime = lastSuccessCrawlingTimelineTime;
	}

	public int getCrawledPostsSize() {
		return crawledPostsSize;
	}

	public void setCrawledPostsSize(int crawledPostsSize) {
		this.crawledPostsSize = crawledPostsSize;
	}

	public String[] getCrawledPostIds() {
		return crawledPostIds;
	}

	public void setCrawledPostIds(String[] crawledPostIds) {
		this.crawledPostIds = crawledPostIds;
	}

	@Override
	public void write(DataOutput d) throws IOException {
		d.writeUTF(id);
		d.writeUTF(username);
		d.writeUTF(fullname);
		d.writeUTF(url);
		d.writeUTF(description);
		d.writeLong(likesOrMembers);
		if (language == null) {
			d.writeInt(-1);
		} else {
			d.writeInt(language.getId());
		}
		if (profileType == null) {
			d.writeInt(-1);
		} else {
			d.writeInt(profileType.getId());
		}

		d.writeLong(postFrequency);
		d.writeLong(firstCrawlingTime);
		d.writeLong(lastCrawlingInfoTime);
		d.writeLong(lastCrawlingTimelineTime);
		d.writeLong(lastSuccessCrawlingTimelineTime);

		crawledPostsSize = crawledPostIds == null ? 0 : crawledPostIds.length;
		d.writeInt(crawledPostsSize);
		for (int i = 0; i < crawledPostsSize; i++) {
			d.writeUTF(crawledPostIds[i]);
		}
	}

	@Override
	public void readFields(DataInput di) throws IOException {
		this.id = di.readUTF();
		this.username = di.readUTF();
		this.fullname = di.readUTF();
		this.url = di.readUTF();
		this.description = di.readUTF();
		this.likesOrMembers = di.readLong();

		int lid = di.readInt();
		if (lid < 0) {
			this.language = null;
		} else {
			this.language = Language.getById(lid);
		}

		int ptid = di.readInt();
		if (ptid < 0) {
			this.profileType = null;
		} else {
			this.profileType = ProfileType.getById(ptid);
		}

		this.postFrequency = di.readLong();
		this.firstCrawlingTime = di.readLong();
		this.lastCrawlingInfoTime = di.readLong();
		this.lastCrawlingTimelineTime = di.readLong();
		this.lastSuccessCrawlingTimelineTime = di.readLong();

		this.crawledPostsSize = di.readInt();
		crawledPostIds = new String[this.crawledPostsSize];
		for (int i = 0; i < crawledPostIds.length; i++) {
			crawledPostIds[i] = di.readUTF();
		}
	}

}
