package vn.viettel.social.inmemdb.jedis;

import redis.clients.jedis.JedisCluster;

/**
 *
 * @author duongth5
 */
public class JedisKV {

    private final RedisClusterConnectionPool conPool;

    public JedisKV(RedisClusterConnectionPool conPool) {
        this.conPool = conPool;
    }

    public void remove(String key) {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            cluster.del(key);
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public String put(String key, String value) {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return null;
        }
        try {
            return cluster.set(key, value);
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public boolean containsKey(String key) {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return false;
        }
        try {
            return cluster.exists(key);
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    public String get(String key) {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return null;
        }
        try {
            return cluster.get(key);
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

}
