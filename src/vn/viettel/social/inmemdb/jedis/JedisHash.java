package vn.viettel.social.inmemdb.jedis;

import redis.clients.jedis.JedisCluster;
import vn.viettel.social.inmemdb.InMemHash;

/**
 *
 * @author duongth5
 */
public class JedisHash implements InMemHash {

    private final RedisClusterConnectionPool conPool;
    private final String name;

    public JedisHash(String name, RedisClusterConnectionPool conPool) {
        this.name = name;
        this.conPool = conPool;
    }

    @Override
    public void add(String field, String value) {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            cluster.hset(name, field, value);
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public boolean contains(String field) {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return false;
        }
        try {
            return cluster.hexists(name, field);
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void destroy() {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            cluster.del(name);
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public String get(String field) {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return null;
        }
        try {
            return cluster.hget(name, field);
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void remove(String field) {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            cluster.hdel(name, field);
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void remove(String[] fields) {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            cluster.hdel(name, fields);
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public long getSize() {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return -1;
        }
        try {
            return cluster.hlen(name);
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

}
