package vn.viettel.social.inmemdb.jedis;

import java.util.Collection;
import redis.clients.jedis.JedisCluster;
import vn.viettel.social.inmemdb.InMemSet;


/**
 *
 * @author Duong
 */
public class JedisSet implements InMemSet {

    private final String name;
    private final RedisClusterConnectionPool clusterPool;

    public JedisSet(String name, RedisClusterConnectionPool conPool) {
        this.name = name;
        this.clusterPool = conPool;
    }

    @Override
    public void add(String element) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            cluster.sadd(name, element);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void addAll(String[] elements) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            cluster.sadd(name, elements);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public boolean contains(String element) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return false;
        }
        try {
            return cluster.sismember(name, element);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void remove(String element) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            cluster.srem(name, element);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void remove(String[] elements) {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            cluster.srem(name, elements);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public long getSize() {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return -1;
        }
        try {
            return cluster.scard(name);
        } finally {
            try {
                clusterPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public Collection<String> getAll() {
        JedisCluster cluster;
        try {
            cluster = clusterPool.poll();
        } catch (InterruptedException ex) {
            return null;
        }
        try {
            return cluster.smembers(name);
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
