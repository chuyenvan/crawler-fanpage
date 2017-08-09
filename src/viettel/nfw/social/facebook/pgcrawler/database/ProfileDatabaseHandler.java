package viettel.nfw.social.facebook.pgcrawler.database;

import viettel.nfw.social.utils.SerializeObjectUtils;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.fusesource.leveldbjni.JniDBFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisCluster;
import viettel.nfw.social.facebook.pgcrawler.entities.ProfileStat;
import viettel.nfw.social.facebook.pgcrawler.entities.StoredPostInfo;
import viettel.nfw.social.facebook.pgcrawler.entities.StoredProfileInfo;
import viettel.nfw.social.facebook.pgcrawler.scoring.ProfileScoringCalulator;
import viettel.nfw.social.utils.DateUtils;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.model.facebook.Profile;

/**
 *
 * @author duongth5
 */
public class ProfileDatabaseHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ProfileDatabaseHandler.class);

	private static ProfileDatabaseHandler instance;

	public static ProfileDatabaseHandler getInstance() {
		try {
			return instance == null ? instance = new ProfileDatabaseHandler() : instance;
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
			return null;
		}
	}

	private static final String DB_DIR = "newdb/";

	private static final String DB_STORED_PROFILE_INFO = "db-stored-profile-info.db";
	private static final String DB_STORED_POST_INFO = "db-stored-post-info.db";
	private static final String DB_FACEBOOK_APP = "db-facebook-app.db";
	private static final String DB_PROFILE_FREQ = "db-profile-freq.db";
	private static final String DB_PROFILE_INFO2 = "db-profile-info2.db";
	private static final String DB_MAP_PROFILE_ID_FULLNAME = "db-profile-id-fullname.db";
	private static final String DB_PROFILE_STATS = "db-profile-stats.db";
	private static final String DB_ORM_LIST = "orm_list";
	private static final int REDIS_POOL_SIZE = 10;

	private final LeveldbDriver storedProfileInfoRepo;
	private final LeveldbDriver storedPostInfoRepo;
	private final LeveldbDriver facebookAppRepo;
	private final LeveldbDriver profileFreqRepo;
	private final LeveldbDriver profileInfo2Repo;
	private final LeveldbDriver profileIdFullnameRepo;
	private final LeveldbDriver profileStatsRepo;
	private final RedisClusterConnectionPool redisConnPool;

	ProfileDatabaseHandler() throws IOException {
		this.storedProfileInfoRepo = new LeveldbDriver(DB_DIR + DB_STORED_PROFILE_INFO);
		this.storedPostInfoRepo = new LeveldbDriver(DB_DIR + DB_STORED_POST_INFO);
		this.facebookAppRepo = new LeveldbDriver(DB_DIR + DB_FACEBOOK_APP);
		this.profileFreqRepo = new LeveldbDriver(DB_DIR + DB_PROFILE_FREQ);
		this.profileInfo2Repo = new LeveldbDriver(DB_DIR + DB_PROFILE_INFO2);
		this.profileIdFullnameRepo = new LeveldbDriver(DB_DIR + DB_MAP_PROFILE_ID_FULLNAME);
		this.profileStatsRepo = new LeveldbDriver(DB_DIR + DB_PROFILE_STATS);
		this.redisConnPool = new RedisClusterConnectionPool(REDIS_POOL_SIZE);
	}

	public RedisClusterConnectionPool getRedisConnPool() {
		return redisConnPool;
	}

	public void shutdown() throws IOException {
		storedProfileInfoRepo.close();
		storedPostInfoRepo.close();
		facebookAppRepo.close();
		profileFreqRepo.close();
		profileInfo2Repo.close();
		profileIdFullnameRepo.close();
		profileStatsRepo.close();
	}

	public synchronized boolean containsInOrmList(String profileId) {
		// TODO
		return false;
	}

	public synchronized void updateProfileInfo(String profileId, Profile profile) throws IOException {
		// update new info data to profileInfo2Repo
		if (profile != null) {
			saveProfileInfo2(profileId, profile);
		}
		// update lastCrawlingInfoTime
		StoredProfileInfo storedProfileInfo = getStoredProfileInfo(profileId);
		storedProfileInfo.setLastCrawlingInfoTime(System.currentTimeMillis());
		saveStoredProfileInfo(storedProfileInfo);
	}

	public synchronized Profile getProfileInfo2(String profileId) {
		byte[] key = profileId.getBytes();
		byte[] value = profileInfo2Repo.get(key);
		if (value == null) {
			return null;
		}
		Profile profile = (Profile) SerializeObjectUtils.readingObjectFromByteArrayUsingGzip(value);
		return profile;
	}

	public synchronized void saveProfileInfo2(String profileId, Profile profile) throws IOException {
		byte[] key = profileId.getBytes();
		byte[] value = SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(profile);
		profileInfo2Repo.write(key, value);
	}

	public synchronized Map<String, Profile> getAllProfileInfo2() {
		Map<String, Profile> result = new HashMap<>();
		Map<String, byte[]> profileId2ByteData = profileInfo2Repo.getAllData();
		for (Map.Entry<String, byte[]> entrySet : profileId2ByteData.entrySet()) {
			String profileId = entrySet.getKey();
			byte[] value = entrySet.getValue();
			Profile profile = (Profile) SerializeObjectUtils.readingObjectFromByteArrayUsingGzip(value);
			result.put(profileId, profile);
		}
		return result;
	}

	public synchronized double getProfileFreq(String profileId) {
		byte[] key = profileId.getBytes();
		byte[] value = profileFreqRepo.get(key);
		String freqStr = JniDBFactory.asString(value);
		if (StringUtils.isEmpty(freqStr)) {
			return ProfileScoringCalulator.AVERAGE_TIME;
		}
		double freq = Double.parseDouble(freqStr);
		if (freq >= 0.0 && freq < ProfileScoringCalulator.MIN_TIME) {
			return ProfileScoringCalulator.AVERAGE_TIME;
		}
		return freq;
	}

	public synchronized void saveProfileFreq(String profileId, double freq) throws IOException {
		byte[] key = profileId.getBytes();
		String freqStr = String.valueOf(freq);
		byte[] value = freqStr.getBytes();
		profileFreqRepo.write(key, value);
	}

	public synchronized Map<String, Double> getAllProfileFreq() {
		Map<String, Double> result = new HashMap<>();
		Map<String, byte[]> profileId2ByteData = profileFreqRepo.getAllData();
		for (Map.Entry<String, byte[]> entrySet : profileId2ByteData.entrySet()) {
			String profileId = entrySet.getKey();
			byte[] value = entrySet.getValue();
			Double freq = Double.parseDouble(JniDBFactory.asString(value));
			result.put(profileId, freq);
		}
		return result;
	}

	public synchronized FacebookApp getFacebookApp(String appId) throws IOException {
		byte[] key = appId.getBytes();
		byte[] value = facebookAppRepo.get(key);
		if (value == null) {
			return null;
		}
		FacebookApp facebookApp = new FacebookApp();
		facebookApp.readFields(new DataInputStream(new ByteArrayInputStream(value)));
		return facebookApp;
	}

	public synchronized void saveFacebookApp(FacebookApp facebookApp) throws IOException {
		byte[] key = facebookApp.getAppID().getBytes();
		byte[] value = SerializeObjectUtils.fromWriteableObjecToByteArray(facebookApp);
		facebookAppRepo.write(key, value);
	}

	public synchronized Map<String, FacebookApp> getAllFacebookApp() {
		Map<String, FacebookApp> result = new HashMap<>();
		Map<String, byte[]> appId2ByteData = facebookAppRepo.getAllData();
		for (Map.Entry<String, byte[]> entrySet : appId2ByteData.entrySet()) {
			String appId = entrySet.getKey();
			byte[] value = entrySet.getValue();
			FacebookApp facebookApp = new FacebookApp();
			try {
				facebookApp.readFields(new DataInputStream(new ByteArrayInputStream(value)));
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
				continue;
			}
			result.put(appId, facebookApp);
		}
		return result;
	}

	public synchronized StoredProfileInfo getStoredProfileInfo(String profileId) throws IOException {
		byte[] key = profileId.getBytes();
		byte[] value = storedProfileInfoRepo.get(key);
		if (value == null) {
			return null;
		}
		StoredProfileInfo storedProfileInfo = new StoredProfileInfo();
		storedProfileInfo.readFields(new DataInputStream(new ByteArrayInputStream(value)));
		return storedProfileInfo;
	}

	public synchronized void saveStoredProfileInfo(StoredProfileInfo storedProfileInfo) throws IOException {
		String keyStr = storedProfileInfo.getId();
		if (StringUtils.isEmpty(keyStr)) {
			LOG.error("ID_NULL");
			return;
		}
		byte[] key = keyStr.getBytes();
		if (key == null) {
			LOG.error("ID_NULL_2");
			return;
		}
		byte[] value = SerializeObjectUtils.fromWriteableObjecToByteArray(storedProfileInfo);
		if (value == null) {
			LOG.info("PROFILE_INFO_NULL {}", keyStr);
			return;
		}
		storedProfileInfoRepo.write(key, value);
	}

	public synchronized void deleteStoredProfileInfo(String profileId) throws IOException {
		byte[] key = profileId.getBytes();
		if (key == null) {
			return;
		}
		storedProfileInfoRepo.delete(key);
	}

	public synchronized Map<String, StoredProfileInfo> getAllStoredProfileInfo() {
		Map<String, StoredProfileInfo> result = new HashMap<>();
		Map<String, byte[]> profileId2ByteData = storedProfileInfoRepo.getAllData();
		for (Map.Entry<String, byte[]> entrySet : profileId2ByteData.entrySet()) {
			String appId = entrySet.getKey();
			byte[] value = entrySet.getValue();
			StoredProfileInfo storedProfileInfo = new StoredProfileInfo();
			try {
				storedProfileInfo.readFields(new DataInputStream(new ByteArrayInputStream(value)));
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
				continue;
			}
			result.put(appId, storedProfileInfo);
		}
		return result;
	}

	public synchronized StoredPostInfo getStoredPostInfo(String postId) throws IOException {
		byte[] key = postId.getBytes();
		byte[] value = storedPostInfoRepo.get(key);
		if (value == null) {
			return null;
		}
		StoredPostInfo storedPostInfo = new StoredPostInfo();
		storedPostInfo.readFields(new DataInputStream(new ByteArrayInputStream(value)));
		return storedPostInfo;
	}

	public synchronized void saveStoredPostInfo(StoredPostInfo storedPostInfo) throws IOException {
		byte[] key = storedPostInfo.getPostId().getBytes();
		byte[] value = SerializeObjectUtils.fromWriteableObjecToByteArray(storedPostInfo);
		storedPostInfoRepo.write(key, value);
	}

	public synchronized Map<String, StoredPostInfo> getAllStoredPostInfo() {
		Map<String, StoredPostInfo> result = new HashMap<>();
		Map<String, byte[]> postId2ByteData = storedPostInfoRepo.getAllData();
		for (Map.Entry<String, byte[]> entrySet : postId2ByteData.entrySet()) {
			String appId = entrySet.getKey();
			byte[] value = entrySet.getValue();
			StoredPostInfo storedPostInfo = new StoredPostInfo();
			try {
				storedPostInfo.readFields(new DataInputStream(new ByteArrayInputStream(value)));
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
				continue;
			}
			result.put(appId, storedPostInfo);
		}
		return result;
	}

	public synchronized void saveProfileIdFullName(String profileId, String profileUsername) throws IOException {
		byte[] key = profileId.getBytes();
		byte[] value = profileUsername.getBytes();
		profileIdFullnameRepo.write(key, value);
	}

	public synchronized String getFullNameOfProfileId(String profileId) {
		byte[] key = profileId.getBytes();
		byte[] value = profileIdFullnameRepo.get(key);
		if (value == null) {
			return null;
		}
		String fullname = JniDBFactory.asString(value);
		return fullname;
	}

	public synchronized void saveProfileStat(String key, ProfileStat stat) {
		if (StringUtils.isEmpty(key)) {
			return;
		}
		if (stat == null) {
			return;
		}
		byte[] rowKey = key.getBytes();
		byte[] value = SerializeObjectUtils.fromWriteableObjecToByteArray(stat);
		try {
			profileStatsRepo.write(rowKey, value);
		} catch (Exception ex) {
			LOG.error("Error while saving profile stat", ex);
		}
	}

	public synchronized ProfileStat getProfileStat(String key, String profileId) {
		byte[] rowKey = key.getBytes();
		byte[] value = profileStatsRepo.get(rowKey);
		ProfileStat stat = new ProfileStat(profileId);
		if (value == null) {
			return stat;
		}
		try {
			stat.readFields(new DataInputStream(new ByteArrayInputStream(value)));
		} catch (IOException ex) {
			LOG.error("Error while getting profile stat", ex);
		}
		return stat;
	}

	// for redis
	private static final String KEY_FAIL_PROFILE_ID = "dpg:failprofileid";
	private static final String KEY_FAIL_POST_ID = "dpg:failpostid";
	private static final String KEY_SPECIAL_LIST = "dpg:specialprofileid";
	private static final String KEY_APPS_LIST = "dpg:apps";

	public static final String KEY_STAT_VISITED_PROFILE_TODAY = "dpg:ttprtd";
	public static final String KEY_STAT_PREVIOUS_VISITED_PROFILE_TODAY = "dpg:pttprtd";
	public static final String KEY_STAT_VISITED_PROFILE_FAIL_TODAY = "dpg:fprtd";
	public static final String KEY_STAT_DISCOVERED_POST_TODAY = "dpg:dcptd";
	public static final String KEY_STAT_DISCOVERED_COMMENT_TODAY = "dpg:dcctd";

	public synchronized boolean sendFailProfileId(String profileId) {
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		boolean ret = false;
		try {
			if (StringUtils.isNotEmpty(profileId)) {
				jedis.sadd(KEY_FAIL_PROFILE_ID, profileId);
			}
			ret = true;
		} catch (Exception ex) {
			LOG.error("Error while send fail profile id to redis " + profileId + " - " + ex.getMessage(), ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return ret;
	}

	public synchronized boolean sendFailPostId(String postId) {
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		boolean ret = false;
		try {
			if (StringUtils.isNotEmpty(postId)) {
				jedis.sadd(KEY_FAIL_POST_ID, postId);
			}
			ret = true;
		} catch (Exception ex) {
			LOG.error("Error while send fail post id to redis " + postId + " - " + ex.getMessage(), ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return ret;
	}

	public boolean isFailPostId(String postId) {
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		boolean ret = false;
		try {
			if (StringUtils.isNotEmpty(postId)) {
				ret = jedis.sismember(KEY_FAIL_POST_ID, postId);
			}
		} catch (Exception ex) {
			LOG.error("Error while check fail post id to redis " + postId + " - " + ex.getMessage(), ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return ret;
	}

	// Stats
	public synchronized Map<String, Long> getDownloaderStat() {
		Map<String, Long> downloaderStats = new HashMap<>();
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return downloaderStats;
		}
		try {
			String visitedProfilesTodayStr = jedis.get(KEY_STAT_VISITED_PROFILE_TODAY);
			if (StringUtils.isNotEmpty(visitedProfilesTodayStr)) {
				try {
					long value = Long.parseLong(visitedProfilesTodayStr);
					downloaderStats.put(KEY_STAT_VISITED_PROFILE_TODAY, value);
				} catch (NumberFormatException e) {
					LOG.error("Error while parseLong " + KEY_STAT_VISITED_PROFILE_TODAY, e);
				}
			}
			String previousVistedProfilesTodayStr = jedis.get(KEY_STAT_PREVIOUS_VISITED_PROFILE_TODAY);
			if (StringUtils.isNotEmpty(previousVistedProfilesTodayStr)) {
				try {
					long value = Long.parseLong(previousVistedProfilesTodayStr);
					downloaderStats.put(KEY_STAT_PREVIOUS_VISITED_PROFILE_TODAY, value);
				} catch (NumberFormatException e) {
					LOG.error("Error while parseLong " + KEY_STAT_PREVIOUS_VISITED_PROFILE_TODAY, e);
				}
			}
			String failProfilesTodayStr = jedis.get(KEY_STAT_VISITED_PROFILE_FAIL_TODAY);
			if (StringUtils.isNotEmpty(failProfilesTodayStr)) {
				try {
					long value = Long.parseLong(failProfilesTodayStr);
					downloaderStats.put(KEY_STAT_VISITED_PROFILE_FAIL_TODAY, value);
				} catch (NumberFormatException e) {
					LOG.error("Error while parseLong " + KEY_STAT_VISITED_PROFILE_FAIL_TODAY, e);
				}
			}
			String discoveredPostsTodayStr = jedis.get(KEY_STAT_DISCOVERED_POST_TODAY);
			if (StringUtils.isNotEmpty(discoveredPostsTodayStr)) {
				try {
					long value = Long.parseLong(discoveredPostsTodayStr);
					downloaderStats.put(KEY_STAT_DISCOVERED_POST_TODAY, value);
				} catch (NumberFormatException e) {
					LOG.error("Error while parseLong " + KEY_STAT_DISCOVERED_POST_TODAY, e);
				}
			}
			String discoveredCommentsTodayStr = jedis.get(KEY_STAT_DISCOVERED_COMMENT_TODAY);
			if (StringUtils.isNotEmpty(discoveredCommentsTodayStr)) {
				try {
					long value = Long.parseLong(discoveredCommentsTodayStr);
					downloaderStats.put(KEY_STAT_DISCOVERED_COMMENT_TODAY, value);
				} catch (NumberFormatException e) {
					LOG.error("Error while parseLong " + KEY_STAT_DISCOVERED_COMMENT_TODAY, e);
				}
			}
		} catch (Exception ex) {
			LOG.error("Error while getDownloaderStat", ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return downloaderStats;
	}

	public synchronized boolean saveDownloaderStat(Map<String, String> stats) {
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		boolean ret = false;
		try {
			if (stats != null) {
				if (stats.containsKey(KEY_STAT_VISITED_PROFILE_TODAY)) {
					jedis.set(KEY_STAT_VISITED_PROFILE_TODAY, String.valueOf(stats.get(KEY_STAT_VISITED_PROFILE_TODAY)));
				}
				if (stats.containsKey(KEY_STAT_PREVIOUS_VISITED_PROFILE_TODAY)) {
					jedis.set(KEY_STAT_PREVIOUS_VISITED_PROFILE_TODAY, String.valueOf(stats.get(KEY_STAT_PREVIOUS_VISITED_PROFILE_TODAY)));
				}
				if (stats.containsKey(KEY_STAT_VISITED_PROFILE_FAIL_TODAY)) {
					jedis.set(KEY_STAT_VISITED_PROFILE_FAIL_TODAY, String.valueOf(stats.get(KEY_STAT_VISITED_PROFILE_FAIL_TODAY)));
				}
				if (stats.containsKey(KEY_STAT_DISCOVERED_POST_TODAY)) {
					jedis.set(KEY_STAT_DISCOVERED_POST_TODAY, String.valueOf(stats.get(KEY_STAT_DISCOVERED_POST_TODAY)));
				}
				if (stats.containsKey(KEY_STAT_DISCOVERED_COMMENT_TODAY)) {
					jedis.set(KEY_STAT_DISCOVERED_COMMENT_TODAY, String.valueOf(stats.get(KEY_STAT_DISCOVERED_COMMENT_TODAY)));
				}
			}
			ret = true;
		} catch (Exception ex) {
			LOG.error("Error while save downloader stats", ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return ret;
	}

	public synchronized void saveProfileStatInMem(String key, ProfileStat stat) {
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return;
		}
		try {
			Map<String, String> field2Value = new HashMap<>();
			if (StringUtils.isNotEmpty(stat.id)) {
				field2Value.put("profileId", stat.id);
			}
			field2Value.put("visitedToday", String.valueOf(stat.visitedToday));
			field2Value.put("visitedFailToday", String.valueOf(stat.visitedFailToday));
			field2Value.put("discoveredPostsToday", String.valueOf(stat.discoveredPostsToday));
			field2Value.put("discoveredCommentsToday", String.valueOf(stat.discoveredCommentsToday));
			field2Value.put("lastVisitedTimeToday", String.valueOf(stat.lastVisitedTimeToday));
			field2Value.put("lastDiffVisitedTimeToday", String.valueOf(stat.lastDiffVisitedTimeToday));
			if (stat.listDiffVisitedTimeToday.size() > 0) {
				StringBuilder sb = new StringBuilder();
				for (Long diffTime : stat.listDiffVisitedTimeToday) {
					sb.append(String.valueOf(diffTime)).append(" ");
				}
				field2Value.put("listDiffVisitedTimeToday", sb.toString().trim());
			}
			jedis.hmset(key, field2Value);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
	}

	public synchronized ProfileStat getProfileStatFromMem(String key, String profileId) {
		ProfileStat stat = new ProfileStat(profileId);
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return null;
		}
		try {
			Map<String, String> field2value = jedis.hgetAll(key);
			stat.visitedToday = field2value.containsKey("visitedToday") ? Integer.parseInt(field2value.get("visitedToday")) : 0;
			stat.visitedFailToday = field2value.containsKey("visitedFailToday") ? Integer.parseInt(field2value.get("visitedFailToday")) : 0;
			stat.discoveredPostsToday = field2value.containsKey("discoveredPostsToday") ? Integer.parseInt(field2value.get("discoveredPostsToday")) : 0;
			stat.discoveredCommentsToday = field2value.containsKey("discoveredCommentsToday") ? Integer.parseInt(field2value.get("discoveredCommentsToday")) : 0;
			stat.lastVisitedTimeToday = field2value.containsKey("lastVisitedTimeToday") ? Long.parseLong(field2value.get("lastVisitedTimeToday")) : DateUtils.getStart(new Date(System.currentTimeMillis())).getTime();
			stat.lastDiffVisitedTimeToday = field2value.containsKey("lastDiffVisitedTimeToday") ? Long.parseLong(field2value.get("lastDiffVisitedTimeToday")) : 0;
			String listDiffTimes = field2value.get("listDiffVisitedTimeToday");
			if (StringUtils.isNotEmpty(listDiffTimes)) {
				String[] diffTimes = listDiffTimes.split(" ");
				List<Long> l = new ArrayList<>();
				for (String diffTime : diffTimes) {
					try {
						l.add(Long.parseLong(diffTime));
					} catch (NumberFormatException e) {
						LOG.error(e.getMessage(), e);
					}
				}
				stat.listDiffVisitedTimeToday.addAll(l);
			}
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return stat;
	}

	// Special List
	public synchronized boolean saveToSpecialList(String profileId) {
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		boolean ret = false;
		try {
			if (StringUtils.isNotEmpty(profileId)) {
				jedis.sadd(KEY_SPECIAL_LIST, profileId);
			}
			ret = true;
		} catch (Exception ex) {
			LOG.error("Error while save profile id to special list " + profileId + " - " + ex.getMessage(), ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return ret;
	}

	public synchronized boolean removeFormSpecialList(String profileId) {
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		boolean ret = false;
		try {
			if (StringUtils.isNotEmpty(profileId)) {
				jedis.srem(KEY_SPECIAL_LIST, profileId);
			}
			ret = true;
		} catch (Exception ex) {
			LOG.error("Error while delete profile id from special list " + profileId + " - " + ex.getMessage(), ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return ret;
	}

	public synchronized boolean containsInSpecialList(String profileId) {
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		boolean ret = false;
		try {
			if (StringUtils.isNotEmpty(profileId)) {
				ret = jedis.sismember(KEY_SPECIAL_LIST, profileId);
			}
		} catch (Exception ex) {
			LOG.error("Error while check profile in special " + profileId + " - " + ex.getMessage(), ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return ret;
	}

	public synchronized Set<String> getAllProfileInSpecialList() {
		Set<String> profiles = new HashSet<>();
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return profiles;
		}
		try {
			profiles = jedis.smembers(KEY_SPECIAL_LIST);
		} catch (Exception ex) {
			LOG.error("Error while get all profiles in special list - " + ex.getMessage(), ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return profiles;
	}

	// Apps List
	public synchronized boolean saveToAppsList(String appId) {
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		boolean ret = false;
		try {
			if (StringUtils.isNotEmpty(appId)) {
				jedis.sadd(KEY_APPS_LIST, appId);
			}
			ret = true;
		} catch (Exception ex) {
			LOG.error("Error while save app id to list " + appId + " - " + ex.getMessage(), ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return ret;
	}

	public synchronized boolean removeFromAppsList(String appId) {
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		boolean ret = false;
		try {
			if (StringUtils.isNotEmpty(appId)) {
				jedis.srem(KEY_APPS_LIST, appId);
			}
			ret = true;
		} catch (Exception ex) {
			LOG.error("Error while delete app id from list " + appId + " - " + ex.getMessage(), ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return ret;
	}

	public synchronized boolean containsInAppsList(String appId) {
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		boolean ret = false;
		try {
			if (StringUtils.isNotEmpty(appId)) {
				ret = jedis.sismember(KEY_APPS_LIST, appId);
			}
		} catch (Exception ex) {
			LOG.error("Error while check app in list " + appId + " - " + ex.getMessage(), ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return ret;
	}

	public synchronized Set<String> getAllInAppsList() {
		Set<String> profiles = new HashSet<>();
		JedisCluster jedis;
		try {
			jedis = redisConnPool.poll();
		} catch (InterruptedException ex) {
			return profiles;
		}
		try {
			profiles = jedis.smembers(KEY_APPS_LIST);
		} catch (Exception ex) {
			LOG.error("Error while get all apps in list - " + ex.getMessage(), ex);
		} finally {
			try {
				redisConnPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return profiles;
	}

}
