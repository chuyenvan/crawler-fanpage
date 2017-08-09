package viettel.nfw.social.tool;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.eclipse.jetty.util.ConcurrentHashSet;
import static org.fusesource.leveldbjni.JniDBFactory.asString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.repo.MappingUsername2IdRepository;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.TParser;
import viettel.nfw.social.utils.Funcs;
import vn.viettel.utils.CustomizedFixedThreadPool;

/**
 *
 * @author duongth5
 */
public class SocialServiceTest {

	private static final Logger LOG = LoggerFactory.getLogger(SocialServiceTest.class);

	public static void main(String[] args) throws IOException {
//		buildData();
		checkSize();
	}
	
	private static void checkSize() throws IOException{
		String DB_MAPING_UN_ID = "username2id.db";
		StringToStringRepository username2IdRepository = new StringToStringRepository(DB_MAPING_UN_ID);
		LOG.info("new map size {}", username2IdRepository.getAllData().size());
		LOG.info("query {}", asString(username2IdRepository.get("duong.tanghai".getBytes())));
		LOG.info("query {}", asString(username2IdRepository.get("cskh.viettel".getBytes())));
		LOG.info("query {}", asString(username2IdRepository.get("viettan".getBytes())));
		username2IdRepository.close();
		
		LOG.info("file map size {}", FileUtils.readList(new File("allKeysRead")).size());
		LOG.info("file ign size {}", FileUtils.readList(new File("ignorelist.txt")).size());
		
		MappingUsername2IdRepository map1 = new MappingUsername2IdRepository("database/map1/mapping_username2id.db");
		LOG.info("map1 size {}", map1.getAllData().size());
		map1.close();
		MappingUsername2IdRepository map2 = new MappingUsername2IdRepository("database/map2/mapping_username2id.db");
		LOG.info("map2 size {}", map2.getAllData().size());
		map2.close();
		MappingUsername2IdRepository map3 = new MappingUsername2IdRepository("database/map3/mapping_username2id.db");
		LOG.info("map3 size {}", map3.getAllData().size());
		map3.close();
	}

	private static void buildData() throws IOException {
		String DB_MAPING_UN_ID = "username2id.db";
		StringToStringRepository username2IdRepository = new StringToStringRepository(DB_MAPING_UN_ID);

		Set<String> rows = new HashSet<>();
		rows.addAll(FileUtils.readList(new File("allKeysRead")));
		LOG.info("DONE Read file");

		final ConcurrentHashSet<String> ignoreList = new ConcurrentHashSet<>();

		final ConcurrentHashMap<String, String> total = new ConcurrentHashMap<>();
		MappingUsername2IdRepository map1 = new MappingUsername2IdRepository("database/map1/mapping_username2id.db");
		MappingUsername2IdRepository map2 = new MappingUsername2IdRepository("database/map2/mapping_username2id.db");
		MappingUsername2IdRepository map3 = new MappingUsername2IdRepository("database/map3/mapping_username2id.db");

		total.putAll(map1.getAllData());
		LOG.info("DONE Read map1");
		total.putAll(map2.getAllData());
		LOG.info("DONE Read map2");
		total.putAll(map3.getAllData());
		LOG.info("DONE Read map3");

		CustomizedFixedThreadPool pool = new CustomizedFixedThreadPool(10, 1000, "Parse");

		for (final String row : rows) {
			pool.execute(new Runnable() {

				@Override
				public void run() {
					String[] part = row.split("\t");
					if (part.length == 2) {
						total.put(part[0].trim(), part[1].trim());
					} else {
						ignoreList.add(row);
					}
				}
			});
		}

		while (pool.getQueueSize() > 0) {
			LOG.info("Waiting ...");
		}
		pool.shutdown(5000);

		for (Map.Entry<String, String> entrySet : total.entrySet()) {
			String username = entrySet.getKey().trim().toLowerCase();
			String id = entrySet.getValue().trim().toLowerCase();
			if (username.matches("^[0-9]{9,}$") && id.matches("^[0-9]{9,}$") && username.equals(id)) {
				// ignore
				ignoreList.add(username + "\t" + id);
			} else {
				username2IdRepository.write(username.getBytes(), id.getBytes());
				System.out.println(username + " - " + id);
			}
		}
		LOG.info("Done write to new");

		FileUtils.write(new File("ignorelist.txt"), ignoreList);
		LOG.info("Done write to list");

		Funcs.sleep(1000L);
		map1.close();
		map2.close();
		map3.close();
		username2IdRepository.close();

	}

	private static void testFinder() {
		Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("203.113.152.12", 3333));
		IFinder getter = new SiteFacebookUsingPhantomjsFinder(proxy);
		System.out.println(getter.getId("https://www.facebook.com/minhanh.01"));

		System.out.println(proxy.address().toString().replace("/", ""));
		System.out.println(TParser.getOneInGroup("fb://profile/100001378595846", "[0-9]{9,}"));
		System.exit(-2);
		getter = new SiteFindmyfbidDotComFinder(proxy);
		System.out.println(getter.getId("https://www.facebook.com/chuyennd"));
	}
}
