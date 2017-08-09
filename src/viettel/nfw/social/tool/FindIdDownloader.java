package viettel.nfw.social.tool;

import java.net.Proxy;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static viettel.nfw.social.tool.SocialService.needToDownload;
import viettel.nfw.social.utils.Pair;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class FindIdDownloader implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(FindIdDownloader.class);
	private final Proxy proxy;
	private final DatabaseHandler db;

	private long lastTimeRequestToSiteLookUpId;

	public FindIdDownloader(DatabaseHandler db, Proxy proxy) {
		this.db = db;
		this.proxy = proxy;
		this.lastTimeRequestToSiteLookUpId = -1;
	}

	@Override
	public void run() {
		while (true) {
			Pair<String, Boolean> item = needToDownload.poll();
			if (item == null) {
				Funcs.sleep(Funcs.randInt(600, 1000));
			} else {
				String username = item.first;
				boolean isGroup = item.second;
				// recheck in inmem map
				if (db.containsUsername(username)) {
					continue;
				}
				// do download
				String id = tryhardToFindId(username, isGroup);
				if (StringUtils.isNotEmpty(id)) {
					LOG.info("Found id {} for username {}", id, username);
					db.writeUnId(username, id);
				} else {
					LOG.info("Cannot find id for username {}", username);
					db.writeErrorUsername(username);
				}
				SocialService.inProgressSet.remove(username);
			}
		}
	}

	private String tryhardToFindId(String username, boolean isGroup) {
		String fbProfileUrl = buildFacebookUrl(username, isGroup);

		// try find in site lookup-id.com
		if (lastTimeRequestToSiteLookUpId != -1) {
			long diff = System.currentTimeMillis() - lastTimeRequestToSiteLookUpId;
			long TIMEOUT_MINUTES = 1 * 60 * 1000;
			if (diff > 0 && diff < TIMEOUT_MINUTES) {
				LOG.info("Sleep {}ms before request", diff);
				Funcs.sleep(diff);
			}
		}
		IFinder getter = new SiteLookupIdDotComFinder(proxy);
		String id = getter.getId(fbProfileUrl);
		lastTimeRequestToSiteLookUpId = System.currentTimeMillis();
		if (StringUtils.isEmpty(id)) {
			// try find in site findmyfbid.com
			getter = new SiteFindmyfbidDotComFinder(proxy);
			id = getter.getId(fbProfileUrl);
			if (StringUtils.isEmpty(id)) {
				// try find in site facebook.com using phantomjs
				getter = new SiteFacebookUsingPhantomjsFinder(proxy);
				id = getter.getId(fbProfileUrl);
			}
		}
		return id;
	}

	private String buildFacebookUrl(String username, boolean isGroup) {
		if (isGroup) {
			return "https://www.facebook.com/groups/" + username;
		} else {
			return "https://www.facebook.com/" + username;
		}
	}

}
