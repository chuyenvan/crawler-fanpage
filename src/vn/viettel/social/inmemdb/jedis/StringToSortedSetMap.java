package vn.viettel.social.inmemdb.jedis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import redis.clients.jedis.JedisCluster;

/**
 *
 * @author duongth5
 */
public class StringToSortedSetMap extends ConcurrentHashMap<String, JedisSingleSortedSet> {

    private final RedisClusterConnectionPool conPool;

    private static final String KEY_SET_PREFIX = "ks:";

    // name for this map 
    private final String name;

    // need a set to keep all keys
    private final String keySetName;
    private final JedisSet keySet; // stored user defined key

    // list contains all single sorted sets
    private final List<JedisSingleSortedSet> singleSortedSets;

    public StringToSortedSetMap(String name, RedisClusterConnectionPool conPool) {
        this.conPool = conPool;
        this.name = name;
        this.keySetName = KEY_SET_PREFIX + name;
        this.keySet = new JedisSet(this.keySetName, this.conPool);
        this.singleSortedSets = new ArrayList<>();
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
            // ScanResult<String> sscan = cluster.sscan(keySetName, ScanParams.SCAN_POINTER_START);
            // List<String> result = sscan.getResult();			
            List<String> result = new ArrayList<>();
            result.addAll(this.keySet.getAll());
            for (String sortedSetKey : result) {
                System.out.println("Load SortedSet key " + sortedSetKey);
                JedisSingleSortedSet jedisSingleSortedSet = new JedisSingleSortedSet(sortedSetKey, conPool);
                this.put(sortedSetKey, jedisSingleSortedSet);
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
        for (JedisSingleSortedSet singleSortedSet : singleSortedSets) {
            singleSortedSet.destroy();
        }
        keySet.destroy();
        super.clear();
    }

    @Override
    public JedisSingleSortedSet remove(Object userDefineKey) {
        JedisSingleSortedSet singleSortedSet = this.get((String) userDefineKey);
        singleSortedSet.destroy();
        singleSortedSets.remove(singleSortedSet);
        keySet.remove((String) userDefineKey);
        return super.remove(userDefineKey);
    }

    public JedisSingleSortedSet put(String userDefineKey) {
        String keyInRedis = getSortedSetKeyInRedis(userDefineKey);
        JedisSingleSortedSet singleSortedSet = new JedisSingleSortedSet(keyInRedis, conPool);
        singleSortedSets.add(singleSortedSet);
        keySet.add(userDefineKey);
        return super.put(userDefineKey, singleSortedSet);
    }

    /**
     *
     * @return the best score element from Composite List
     */
    public Collection<String> topPoll(int numUrl, boolean isRemove) {
        JedisSingleSortedSet topSet = null;
        Double minScore = null;
        for (JedisSingleSortedSet sortedSet : singleSortedSets) {
            Double topScore = sortedSet.getTopScore();
            if (topScore != null && (topScore < minScore || minScore == null)) {
                minScore = topScore;
                topSet = sortedSet;
            }
        }
        if (topSet != null) {
            return topSet.getTop(numUrl, isRemove);
        } else {
            return null;
        }
    }

    public static void main(String[] args) {
        StringToSortedSetMap map = new StringToSortedSetMap("social", new RedisClusterConnectionPool(5));
        map.put("fb:crawled:988181941");
        map.get("fb:crawled:988181941").addToQueue("https://www.facebook.com/duong.tanghai", 13.5);
        map.put("fb:crawled:981243242");
        map.get("fb:crawled:981243242").addToQueue("https://www.facebook.com/abc.zyx", 11.5);
        System.out.println("aaa");
    }

}
