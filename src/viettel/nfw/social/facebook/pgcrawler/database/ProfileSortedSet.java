package viettel.nfw.social.facebook.pgcrawler.database;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.Tuple;

/**
 *
 * @author duongth5
 */
public class ProfileSortedSet extends JedisSingleSortedSet {

	private static final Logger LOG = LoggerFactory.getLogger(ProfileSortedSet.class);
	private final ProfileDatabaseHandler dbHandler;
	private final Set<String> makeSureProfileInQueue = new HashSet<>();
	public static final String KEY_DEEP_FB_PAGE_GROUP_SORTED_SET = "dpg:fbid";

	public ProfileSortedSet(RedisClusterConnectionPool connPool, ProfileDatabaseHandler dbHandler) {
		super(KEY_DEEP_FB_PAGE_GROUP_SORTED_SET, connPool);
		this.dbHandler = dbHandler;
	}

	public static final double OVER_TIME = 60 * 60 * 1000L; // 60 minutes

	private String profileId = null;
	private double time = 0.0;

	@Override
	public synchronized Collection<String> poll(int numUrl, double deltaScore) {
		if (profileId != null) {
			if (time > System.currentTimeMillis()) {
				if ((time - System.currentTimeMillis()) > OVER_TIME) {
					try {
						return Arrays.asList(new String[]{profileId});
					} finally {
						profileId = null;
						time = 0.0;
					}
				} else {
					return new ArrayList<>();
				}
			} else {
				try {
					return Arrays.asList(new String[]{profileId});
				} finally {
					profileId = null;
					time = 0.0;
				}
			}
		}
		JedisCluster cluster;
		try {
			cluster = this.clusterPool.poll();
		} catch (InterruptedException ex) {
			return null;
		}
		try {
			Set<Tuple> zrange = cluster.zrangeWithScores(name, 0, 0);
			List<String> res = new ArrayList<>();
			for (Tuple key : zrange) {

				double score = key.getScore();
				String pid = key.getElement();
				//Double zincrby = cluster.zincrby(name, dbHandler.getProfileFreq(pid), pid);
				//LOG.info("Update score for {} from {} --> {}", new Object[]{pid, score, zincrby});
				double newScore = dbHandler.getProfileFreq(pid) + System.currentTimeMillis();
				Long zadd = cluster.zadd(name, newScore, pid);
				try {
					LOG.info("Update score for {} from {}({}) --> {}({}), status {}",
						new Object[]{pid, (long) score, new Date((long) score).toString(), (long) newScore, new Date((long) newScore).toString(), zadd});
				} catch (Exception e) {
				}
				if (score > System.currentTimeMillis()) {
					profileId = key.getElement();
					time = score;
					return new ArrayList<>();
				}
				res.add(key.getElement());
			}
			return res;
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
			return null;
		} finally {
			try {
				clusterPool.add(cluster);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}

	}

	@Override
	public Collection<String> poll(int numUrl) {
		return poll(numUrl, 0.0);
	}

	@Override
	public void addToQueue(String member, Double score) {
		JedisCluster cluster;
		try {
			cluster = clusterPool.poll();
		} catch (InterruptedException ex) {
			return;
		}
		try {
			cluster.zadd(name, score, member);
		} finally {
			try {
				clusterPool.add(cluster);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
	}

	/**
	 * add to set when we need
	 *
	 * @param profileId
	 */
	public void addToSetIfNeed(String profileId) {
		if (makeSureProfileInQueue.contains(profileId)) {
			return;
		}
		if (contains(profileId)) {
			makeSureProfileInQueue.add(profileId);
			return;
		}
		this.addToQueue(profileId, System.currentTimeMillis() + this.dbHandler.getProfileFreq(profileId));
		makeSureProfileInQueue.add(profileId);

	}
}
