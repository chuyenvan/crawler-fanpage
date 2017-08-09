package vn.viettel.social.inmemdb.jedis;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

/**
 *
 * @author duongth5
 */
public class StringToJedisHashMap extends ConcurrentHashMap<String, JedisHash> {

    private final RedisClusterConnectionPool conPool;

    private static final String KEY_SET_PREFIX = "ks:";

    // name for this map 
    private final String name;

    // need a set to keep all keys
    private final String keySetName;
    private final JedisSet keySet; // stored user defined key

    // list contains all single sorted sets
    private final List<JedisHash> jedisHashes;

    public StringToJedisHashMap(String name, RedisClusterConnectionPool conPool) {
        this.conPool = conPool;
        this.name = name;
        this.keySetName = KEY_SET_PREFIX + this.name;
        this.keySet = new JedisSet(this.keySetName, this.conPool);
        this.jedisHashes = new ArrayList<>();
        loadMap();
    }

    private void loadMap() {
        JedisCluster cluster;
        try {
            cluster = conPool.poll();
        } catch (InterruptedException ex) {
            return;
        }
        try {
            // I use sscan for get all member in keySet instead of using smembers of JedisSet
            // because of performance
            ScanResult<String> sscan = cluster.sscan(keySetName, ScanParams.SCAN_POINTER_START);
            List<String> result = sscan.getResult();
            for (String jedisHashKey : result) {
                System.out.println("Load SortedSet key " + jedisHashKey);
                JedisHash jedisHash = new JedisHash(jedisHashKey, conPool);
                this.put(jedisHashKey, jedisHash);
            }
        } finally {
            try {
                conPool.add(cluster);
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }
    }

    private String getSortedSetKeyInRedis(String userDefineKey) {
        String keyInRedis = userDefineKey;
        return keyInRedis;
    }

    @Override
    public void clear() {
        for (JedisHash singleSortedSet : jedisHashes) {
            singleSortedSet.destroy();
        }
        keySet.destroy();
        super.clear();
    }

    @Override
    public JedisHash remove(Object userDefineKey) {
        JedisHash singleSortedSet = this.get((String) userDefineKey);
        singleSortedSet.destroy();
        jedisHashes.remove(singleSortedSet);
        keySet.remove((String) userDefineKey);
        return super.remove(userDefineKey);
    }

    public JedisHash put(String userDefineKey) {
        String keyInRedis = getSortedSetKeyInRedis(userDefineKey);
        JedisHash singleSortedSet = new JedisHash(keyInRedis, conPool);
        jedisHashes.add(singleSortedSet);
        keySet.add(userDefineKey);
        return super.put(userDefineKey, singleSortedSet);
    }
}
