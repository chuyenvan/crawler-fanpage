package vn.viettel.social.inmemdb.jedis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.nigma.engine.web.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.Tuple;
import vn.viettel.social.inmemdb.InMemSortedSet;

/**
 *
 * @author thiendn2
 *
 * Created on Aug 13, 2015, 6:40:53 PM
 */
public class JedisSingleSortedSet implements InMemSortedSet {

    public final String name;
    protected final RedisClusterConnectionPool clusterPool;
    private static final int MAX_ACCEPTED_SCORE_DEFAULT = 1_000_000;

    public JedisSingleSortedSet(String name, RedisClusterConnectionPool clusterPool) {
        this.name = name;
        this.clusterPool = clusterPool;
    }

    @Override
    public Collection<String> poll(int numUrl, double deltaScore) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return null;
        }
        try {
            Set<Tuple> zrange = cluster.zrangeWithScores(name, 0, numUrl - 1);
            List<String> res = new ArrayList<>();
            for (Tuple key : zrange) {

                double score = key.getScore();
                if (score > MAX_ACCEPTED_SCORE_DEFAULT) {
                    res.add(key.getElement());
                    Double zincrby = cluster.zincrby(name, deltaScore * -1.0, key.getElement());
                    LOG.debug("Update score for {} from {} --> {}", new Object[]{key.getElement(), score, zincrby});
                } else {
                    LOG.debug("Skip {} cause the score is too large", key.getElement());
                }
            }
            return res;
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }

    }

    /**
     * Stupid in word "Poll"
     *
     * @param numUrl
     * @param remove
     * @return
     */
    @Override
    public Collection<String> getTop(int numUrl, boolean remove) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return null;
        }
        try {
            Set<String> zrange = cluster.zrange(name, 0, numUrl - 1);
            if (zrange != null) {
                if (remove) {
                    if (zrange.size() > 0) {
                        cluster.zrem(name, zrange.toArray(new String[]{}));
                    }
                }
            }
            return zrange;
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }

    }

    /**
     * Poll and rempve
     *
     * @param numUrl
     * @return
     */
    @Override
    public Collection<String> getTop(int numUrl) {
        return JedisSingleSortedSet.this.getTop(numUrl, true);
    }

    /**
     * Note that we convert score from positive number to negative number
     *
     * @param keyAndScore
     */
    @Override
    public void addToQueue(List<Pair<String, Double>> keyAndScore) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            Map<String, Double> member2Score = new HashMap<>();
            for (Pair<String, Double> pair : keyAndScore) {
                member2Score.put(pair.first, pair.second * -1.0);
            }
            cluster.zadd(name, member2Score);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    /**
     * Note that we convert score from positive number to negative number
     *
     * @param keyAndScore
     */
    @Override
    public void addToQueue(String member, Double score) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            cluster.zadd(name, score * -1, member);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public boolean contains(String key) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return false;
        }
        try {
            Double zscore = cluster.zscore(name, key);
            return zscore != null;
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static final Logger LOG = LoggerFactory.getLogger(JedisSingleSortedSet.class);

    @Override
    public long remove(String... keys) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return 0;
        }
        try {
            Long zrem = cluster.zrem(name, keys);
            return zrem;
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public Double getTopScore() {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return null;
        }
        try {
            Set<String> zrange = cluster.zrange(name, 0, 1);
            if (zrange.isEmpty()) {
                return null;
            }
            return cluster.zscore(name, zrange.toArray(new String[]{})[0]);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public Double getScore(String key) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return null;
        }
        try {
            return cluster.zscore(name, key);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        RedisClusterConnectionPool pool = new RedisClusterConnectionPool(5);
        JedisSingleSortedSet sortedSet = new JedisSingleSortedSet("thien.test", pool);
        sortedSet.addToQueue("trungnt", 3.0);
        sortedSet.addToQueue("duongth", 5.0);
        sortedSet.addToQueue("thiendn", 2.0);
        sortedSet.addToQueue("minhht", 1.0);
        LOG.info("peek 1:{}", sortedSet.getTop(1, false));
        //LOG.info("getTop 2: {}", sortedSet.getTop(2, true));
        LOG.info("peek 1: {}", sortedSet.getTop(1, false));
        LOG.info("Contains: {}", sortedSet.contains("thiendn"));
        LOG.info("Contains: {}", sortedSet.contains("thanhtn"));
        LOG.info("Topscore : {}", sortedSet.getTopScore());
        sortedSet.addToQueue("minhht", 6.0);
        LOG.info("Topscore : {}", sortedSet.getTopScore());
    }

    public void fillValue(double value) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            double min = -1000000.0;
            double max = 100000.0;
            double step = 10000.0;
            double start = min;
            while (start < max) {
                if (value >= start && value <= start + step) {
                    start += step;
                    continue;
                }
                Set<String> zrange = cluster.zrangeByScore(name, start, start + step);

                LOG.info("Start = {}; keyfound={}", start, zrange.size());
                start += step;
                if (!zrange.isEmpty()) {
                    for (String key : zrange) {
                        cluster.zadd(name, value, key);
                    }
                }

            }

        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public long getSize() {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();			
        } catch (InterruptedException ex) {
            return 0;
        }
        try {
            return cluster.zcard(name);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void destroy() {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            cluster.del(name);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }
}
