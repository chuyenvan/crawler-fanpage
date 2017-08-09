package vn.viettel.social.inmemdb.jedis;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import org.nigma.engine.util.EngineConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

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
		LOG.info("hostAndPorts: " + hostAndPorts);
		if (hostAndPorts == null) {
			haps.add(new HostAndPort("192.168.101.9", 30001));
			haps.add(new HostAndPort("192.168.101.10", 30001));
			haps.add(new HostAndPort("192.168.101.11", 30001));
			haps.add(new HostAndPort("192.168.101.9", 30002));
			haps.add(new HostAndPort("192.168.101.10", 30002));
			haps.add(new HostAndPort("192.168.101.11", 30002));

		} else {
			String[] parts = hostAndPorts.split(";");
			for (String part : parts) {
				String[] segments = part.split(":");
				haps.add(new HostAndPort(segments[0], Integer.parseInt(segments[1])));
			}
		}
	}

	private static JedisCluster getJedisCluster() {
		JedisCluster jedis = new JedisCluster(haps);
		return jedis;
	}

	public RedisClusterConnectionPool(int poolSize) {
		this.clusterPool = new ArrayBlockingQueue<>(poolSize);
		for (int i = 0; i < poolSize; i++) {
			clusterPool.add(getJedisCluster());
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
