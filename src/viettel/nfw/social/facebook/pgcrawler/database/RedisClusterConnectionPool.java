package viettel.nfw.social.facebook.pgcrawler.database;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import viettel.nfw.social.utils.EngineConfiguration;
import vn.viettel.utils.SimpleTimer;

/**
 *
 * Redis Cluster Connection pool
 *
 * @author thiendn2
 *
 * Created on Aug 13, 2015, 6:45:01 PM
 */
public class RedisClusterConnectionPool {

	private final ArrayBlockingQueue<JedisCluster> clusterPool;
	private static final Set<HostAndPort> haps;
	private static final Logger LOG = LoggerFactory.getLogger(RedisClusterConnectionPool.class);

	static {
		haps = new HashSet<>();
		String hostAndPorts = EngineConfiguration.get().get("jedis.connection.cluster");
		// Currently set default is null
		LOG.info("hostAndPorts: {}", hostAndPorts);
		if (hostAndPorts == null) {
			hostAndPorts = "203.113.152.1:7000;203.113.152.2:7000;203.113.152.3:7000;203.113.152.4:7000;"
				+ "203.113.152.5:7000;203.113.152.6:7000;203.113.152.7:7000;203.113.152.8:7000;203.113.152.9:7000;"
				+ "203.113.152.10:7000;203.113.152.29:7000;203.113.152.30:7000";
		}

		String[] parts = hostAndPorts.split(";");
		for (String part : parts) {
			String[] segments = part.split(":");
			haps.add(new HostAndPort(segments[0], Integer.parseInt(segments[1])));
		}
	}

	public static JedisCluster getJedisCluster() {
		JedisCluster jedis = new JedisCluster(haps);
		return jedis;
	}

	public RedisClusterConnectionPool(int poolSize) {
		this.clusterPool = new ArrayBlockingQueue<>(poolSize);
		SimpleTimer simpleTimer = new SimpleTimer();
		for (int i = 0; i < poolSize; i++) {
			clusterPool.add(getJedisCluster());
			LOG.info("getJedisCluster {} in {}s", i, simpleTimer.getTimeAndReset() / 1000);
		}
	}

	public JedisCluster poll() throws InterruptedException {
		return clusterPool.take();
	}

	public void add(JedisCluster cluster) throws InterruptedException {
		if (cluster != null) {
			clusterPool.put(cluster);
		}
	}
}
