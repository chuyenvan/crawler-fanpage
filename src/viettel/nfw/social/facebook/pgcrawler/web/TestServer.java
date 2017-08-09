package viettel.nfw.social.facebook.pgcrawler.web;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.Funcs;
import viettel.nfw.social.utils.TCrawler;
import vn.viettel.engine.utils.Crawler;

/**
 *
 * @author duongth5
 */
public class TestServer {

	private static final Logger LOG = LoggerFactory.getLogger(TestServer.class);

	public static void main(String[] args) throws IOException, Exception {
		// start web server
//		startWebServer();
		// add/delete profile
//		deleteProfiles();
//		addProfiles();
		// add/update app token
//		addAppIdsToRedis();
		updateApps();
	}

	private static void addAppIdsToRedis() throws IOException {
		ProfileDatabaseHandler db = ProfileDatabaseHandler.getInstance();
		Set<String> appIds = new HashSet<>();
		appIds.addAll(FileUtils.readList(new File("app-ids-list.txt")));
		for (String appId : appIds) {
			db.saveToAppsList(appId);
		}
	}

	private static void updateApps() throws IOException {
		List<String> rows = FileUtils.readList(new File("app-infos.text"));
		for (String row : rows) {
			String[] parts = row.split("\t");
			if (parts.length > 4) {
				String appId = parts[2];
				String userAccessToken = parts[3];
				if (StringUtils.isEmpty(appId) || StringUtils.isEmpty(userAccessToken)
					|| appId.equals("null") || userAccessToken.equals("null")) {
					System.out.println("NULL " + row);
					continue;
				}
				Map<String, String> param = new HashMap<>();
				param.put("action", "add");
				param.put("appID", appId);
				param.put("userAccessToken", userAccessToken);

				String url = "http://203.113.152.29:7018/app/";
				String response = TCrawler.postContentFromUrl(url, param);
				System.out.println(row);
				System.out.println("response: " + response);
//				break;
			} else {
				System.out.println("not enough " + row);
			}
		}
	}

	private static void deleteProfiles() throws UnsupportedEncodingException, IOException {
		Set<String> deleteIds = new HashSet<>();
		deleteIds.addAll(FileUtils.readList(new File("delete-ids.txt")));
		for (String deleteId : deleteIds) {
			String url = "http://203.113.152.29:7018/profile/?action=delete&fbid=" + URLEncoder.encode(deleteId, "UTF-8");
			String response = Crawler.getContentFromUrl(url);
			System.out.println(response);
			Funcs.sleep(100);
		}
	}

	private static void addProfiles() throws UnsupportedEncodingException, IOException {
		Set<String> rows = new HashSet<>();
		rows.addAll(FileUtils.readList(new File("added-ids.txt")));
		for (String row : rows) {
			String[] parts = row.split("\t");
			if (parts.length == 2) {
				String id = parts[0];
				String type = parts[1];
				String url = "http://203.113.152.29:7018/profile/?action=add&fbid=" + URLEncoder.encode(id, "UTF-8") + "&fbtype=" + URLEncoder.encode(type, "UTF-8");
				String response = Crawler.getContentFromUrl(url);
				System.out.println(response);
			} else {
				System.out.println(row);
			}
		}
	}

	private static void startWebServer() throws Exception {
		ProfileDatabaseHandler db = ProfileDatabaseHandler.getInstance();
		WebUIServer server = new WebUIServer(7019, db);
		server.run();
	}

}
