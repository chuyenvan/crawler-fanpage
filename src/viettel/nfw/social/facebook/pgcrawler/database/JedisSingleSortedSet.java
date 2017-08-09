package viettel.nfw.social.facebook.pgcrawler.database;

import com.viettel.fix.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.Tuple;

/**
 *
 * @author duongth5
 */
public class JedisSingleSortedSet implements InMemSortedSet {

	public final String name;
	protected final RedisClusterConnectionPool clusterPool;
	private static final int MAX_ACCEPTED_SCORE_DEFAULT = 1_000_000_000;

	private static final Logger LOG = LoggerFactory.getLogger(JedisSingleSortedSet.class);

	public JedisSingleSortedSet(String name, RedisClusterConnectionPool clusterPool) {
		this.name = name;
		this.clusterPool = clusterPool;
	}

	@Override
	public Collection<String> poll(int numUrl, double deltaScore) {
		JedisCluster jedis;
		List<String> res = new ArrayList<>();
		try {
			jedis = clusterPool.poll();
		} catch (InterruptedException ex) {
			return null;
		}
		try {
			Set<Tuple> zrange = jedis.zrangeWithScores(name, 0, numUrl - 1);
			for (Tuple key : zrange) {

				double score = key.getScore();
				if (score < MAX_ACCEPTED_SCORE_DEFAULT) {
					res.add(key.getElement());
					if (deltaScore != 0) {
						Double zincrby = jedis.zincrby(name, deltaScore * -1.0, key.getElement());
						LOG.debug("Update score for {} from {} --> {}", new Object[]{key.getElement(), score, zincrby});
					}
				} else {
					LOG.debug("Skip {} cause the score is too large", key.getElement());
				}
			}
			return res;
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return res;
	}

	@Override
	public Collection<String> poll(int numUrl, boolean remove) {
		JedisCluster jedis;
		Set<String> zrange = new HashSet<>();
		try {
			jedis = clusterPool.poll();
		} catch (InterruptedException ex) {
			return null;
		}
		try {
			zrange = jedis.zrange(name, 0, numUrl);
			if (remove && zrange != null && !zrange.isEmpty()) {
				jedis.zrem(name, zrange.toArray(new String[]{}));
			}
			return zrange;
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return zrange;
	}

	@Override
	public Collection<String> poll(int numUrl) {
		return poll(numUrl, true);
	}

	/**
	 * Note that we convert score from positive number to negative number
	 *
	 * @param keyAndScores
	 */
	@Override
	public void addToQueue(List<Pair<String, Double>> keyAndScores) {
		JedisCluster jedis;
		try {
			jedis = clusterPool.poll();
		} catch (InterruptedException ex) {
			return;
		}
		try {
			Map<String, Double> member2Score = new HashMap<>();
			for (Pair<String, Double> pair : keyAndScores) {
				member2Score.put(pair.first, pair.second * -1.0);
			}
			jedis.zadd(name, member2Score);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
	}

	@Override
	public void addToQueue(String member, Double score) {
		JedisCluster jedis;
		try {
			jedis = clusterPool.poll();
		} catch (InterruptedException ex) {
			return;
		}
		try {
			jedis.zadd(name, score * -1, member);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
	}

	@Override
	public boolean contains(String key) {
		JedisCluster jedis;
		Double zscore = null;
		try {
			jedis = clusterPool.poll();
		} catch (InterruptedException ex) {
			return false;
		}
		try {
			zscore = jedis.zscore(name, key);
			return zscore != null;
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return zscore != null;
	}

	@Override
	public long remove(String... keys) {
		if (keys == null || keys.length == 0) {
			return 0;
		}

		JedisCluster jedis;
		try {
			jedis = clusterPool.poll();
		} catch (InterruptedException ex) {
			return 0;
		}
		try {
			Long zrem = jedis.zrem(name, keys);
			return zrem;
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return -1L;
	}

	/**
	 * return first element score
	 *
	 * @return
	 */
	public Double getTopScore() {
		JedisCluster jedis;
		try {
			jedis = clusterPool.poll();
		} catch (InterruptedException ex) {
			return null;
		}
		try {
			Set<String> zrange = jedis.zrange(name, 0, 1);
			if (zrange.isEmpty()) {
				return null;
			}
			return jedis.zscore(name, zrange.toArray(new String[]{})[0]);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return null;
	}

	/**
	 * return score of the key
	 *
	 * @param key
	 * @return
	 */
	public Double getScore(String key) {
		JedisCluster jedis;
		try {
			jedis = clusterPool.poll();
		} catch (InterruptedException ex) {
			return null;
		}
		try {
			return jedis.zscore(name, key);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return null;
	}

	/**
	 * Update score for a key in mem
	 *
	 * @param member
	 * @param deltaScore
	 * @return
	 */
	public Double updateScore(String member, Double deltaScore) {
		JedisCluster jedis;
		try {
			jedis = clusterPool.poll();
		} catch (InterruptedException ex) {
			return null;
		}
		try {
			return jedis.zincrby(name, deltaScore * -1, member);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return null;
	}

	/**
	 * return size of single sorted set
	 *
	 * @return
	 */
	public long getSize() {
		JedisCluster jedis;
		try {
			jedis = clusterPool.poll();
		} catch (InterruptedException ex) {
			return 0;
		}
		try {
			return jedis.zcard(name);
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		} finally {
			try {
				clusterPool.add(jedis);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
		return -1L;
	}

}
