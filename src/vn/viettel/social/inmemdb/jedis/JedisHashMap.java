package vn.viettel.social.inmemdb.jedis;

import com.google.gson.Gson;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisCluster;

/**
 *
 * @author chuyennd
 *
 * Created on Dec 31, 2015, 6:40:53 PM
 */
public class JedisHashMap {

	public final String name;
	protected final RedisClusterConnectionPool clusterPool;
	private static final Logger LOG = LoggerFactory.getLogger(JedisHashMap.class);

	public JedisHashMap(String name, RedisClusterConnectionPool clusterPool) {
		this.name = name;
		this.clusterPool = clusterPool;
	}

	public boolean set(String key, String value) {
		JedisCluster cluster;
		try {
			cluster = clusterPool.poll();
		} catch (InterruptedException ex) {
			LOG.error(ex.getMessage(), ex);
			return false;
		}
		try {
			cluster.hset(name, key, value);
			return true;
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(cluster);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return false;
	}

	public boolean mset(Map<String, String> keyAndValue) {
		JedisCluster cluster;
		try {
			cluster = clusterPool.poll();
		} catch (InterruptedException ex) {
			LOG.error(ex.getMessage(), ex);
			return false;
		}
		try {
			cluster.hmset(name, keyAndValue);
			return true;
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(cluster);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return false;
	}

	public String get(String key) {
		JedisCluster cluster;
		try {
			cluster = clusterPool.poll();
		} catch (InterruptedException ex) {
			LOG.error(ex.getMessage(), ex);
			return null;
		}
		try {
			return cluster.hget(name, key);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(cluster);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return null;
	}

	public boolean exists(String key) {
		JedisCluster cluster;
		String lastpage = null;
		try {
			cluster = clusterPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		try {
			return cluster.hexists(name, key);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(cluster);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return lastpage != null;
	}

	public long remove(String... keys) {
		JedisCluster cluster;
		try {
			cluster = clusterPool.poll();
		} catch (InterruptedException ex) {
			return 0;
		}
		try {
			Long hdel = cluster.hdel(name, keys);
			return hdel;
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(cluster);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return -1L;
	}

	/**
	 * return size of JedisSingleMap
	 *
	 * @return
	 */
	public long getSize() {
		JedisCluster cluster;
		try {
			cluster = clusterPool.poll();
		} catch (InterruptedException ex) {
			return 0;
		}
		try {
			return cluster.hlen(name);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(cluster);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return -1L;
	}

	public Set<String> getAllKey() {
		JedisCluster cluster;
		try {
			cluster = clusterPool.poll();
		} catch (InterruptedException ex) {
			return null;
		}
		try {
			return cluster.hkeys(name);			
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(cluster);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return null;
	}

	public static void main(String[] args) throws Exception {
		RedisClusterConnectionPool pool = new RedisClusterConnectionPool(1);
		JedisHashMap jedisHashMap = new JedisHashMap("allDataDeepId", pool);

	}
}
