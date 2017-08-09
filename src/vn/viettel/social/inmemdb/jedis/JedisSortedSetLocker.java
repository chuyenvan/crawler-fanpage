package vn.viettel.social.inmemdb.jedis;

import com.github.jedis.lock.JedisLock;
import org.nigma.engine.util.EngineConfiguration;
import redis.clients.jedis.JedisPool;

/**
 *
 * @author thiendn2
 *
 * Created on Aug 14, 2015, 5:33:44 PM
 */
public class JedisSortedSetLocker {

    private static final String REDIS_SERVER_FOR_LOCKER = EngineConfiguration.get().get("crawler.locker.redis", "203.113.152.15:4010");

    public static final JedisPool jedisPool;

    static {
        int separator = REDIS_SERVER_FOR_LOCKER.indexOf(":");
        if (separator < 0) {
            throw new RuntimeException("Incorrect configure for redis server locker " + REDIS_SERVER_FOR_LOCKER);
        }
        String host = REDIS_SERVER_FOR_LOCKER.substring(0, separator);
        int port = Integer.parseInt(REDIS_SERVER_FOR_LOCKER.substring(separator + 1));
        jedisPool = new JedisPool(host, port);
    }

    private static final String LOCKER_KEY = "lkr";
    private static final int LOCKER_TIME_OUT = 5000;//5 seconds
    public static final JedisLock locker = new JedisLock(LOCKER_KEY, LOCKER_TIME_OUT);

    public static final JedisLock getLockerByName(String keyName) {
        return new JedisLock(keyName, LOCKER_TIME_OUT);
    }

//	private static class FixedJedisPool{
//		private final ArrayBlockingQueue<Jedis> queue = new ArrayList<>();		
//	}
}
