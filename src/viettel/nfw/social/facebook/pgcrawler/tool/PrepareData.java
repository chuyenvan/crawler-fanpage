package viettel.nfw.social.facebook.pgcrawler.tool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileSortedSet;
import viettel.nfw.social.facebook.pgcrawler.database.RedisClusterConnectionPool;
import viettel.nfw.social.facebook.pgcrawler.entities.StoredProfileInfo;
import viettel.nfw.social.facebook.updatenews.graph.FacebookAppManager;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.facebook.updatenews.graph.entities.ProfileType;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.Funcs;
import vn.itim.detector.Language;

/**
 *
 * @author Duong
 */
public class PrepareData {

	private static final Logger LOG = LoggerFactory.getLogger(PrepareData.class);

	public static void main(String[] args) throws IOException {
		String profileFilename = args[0];
		boolean isImportDataToLevelDb = Boolean.parseBoolean(args[1]);
		boolean isImportDataToRedis = Boolean.parseBoolean(args[2]);
		boolean isImportApp = Boolean.parseBoolean(args[3]);
		String appFilename = args[4];
		process(profileFilename, isImportDataToLevelDb, isImportDataToRedis, isImportApp, appFilename);
	}

	private static void process(String profileFilename, boolean isImportDataToLevelDb, boolean isImportDataToRedis, boolean isImportApp, String appFilename) throws IOException {

		LOG.info("CHECK-Is import profile: {}", (isImportDataToLevelDb || isImportDataToRedis));
		LOG.info("CHECK-Is import fb apps: {}", isImportApp);

		ProfileDatabaseHandler db = ProfileDatabaseHandler.getInstance();

		// check if import profile data
		if (isImportDataToLevelDb || isImportDataToRedis) {
			RedisClusterConnectionPool redisConnPool = new RedisClusterConnectionPool(3);
			ProfileSortedSet pageGroupSortedSet = new ProfileSortedSet(redisConnPool, db);

			LOG.info("Read profiles file: {}", profileFilename);
			// read data from file
			List<StoredProfileInfo> storedProfileInfos = new ArrayList<>();
			List<String> rows = FileUtils.readList(new File(profileFilename));
			for (String row : rows) {
				if (!StringUtils.startsWith(row, "#")) {
					String[] parts = row.split("\t");
					String profileId = parts[0];
					StoredProfileInfo storedProfileInfo = new StoredProfileInfo(profileId);
					storedProfileInfo.setUsername(StringUtils.equalsIgnoreCase(parts[1], "null") ? "" : parts[1]);
					storedProfileInfo.setFullname(StringUtils.equalsIgnoreCase(parts[2], "null") ? "" : parts[2]);
					storedProfileInfo.setUrl(StringUtils.equalsIgnoreCase(parts[3], "null") ? "" : parts[3]);
					storedProfileInfo.setLikesOrMembers(Long.parseLong(parts[4]));
					storedProfileInfo.setLanguage(Language.getByShortName(parts[5]));
					storedProfileInfo.setProfileType(ProfileType.getByShortName(parts[6]));
					storedProfileInfos.add(storedProfileInfo);
				}
			}

			for (StoredProfileInfo storedProfileInfo : storedProfileInfos) {
				// save to leveldb
				boolean isExisted = false;
				if (isImportDataToLevelDb) {
					StoredProfileInfo storedProfileInfoInDb = db.getStoredProfileInfo(storedProfileInfo.getId());
					if (storedProfileInfoInDb == null) {
						db.saveStoredProfileInfo(storedProfileInfo);
					} else {
						isExisted = true;
						LOG.info("Profile existed! {}", storedProfileInfo.getId());
					}
				}
				// save to redis
				if (isImportDataToRedis) {
					long buff = Funcs.randInt(2 * 60 * 1000, 45 * 60 * 1000);
					double currentTime = System.currentTimeMillis() * 1.0 + buff * 1.0;
					if (!isExisted) {
						pageGroupSortedSet.addToQueue(storedProfileInfo.getId(), currentTime);
					}
				}
			}
		}

		// check if import facebook app
		if (isImportApp) {
			LOG.info("Read facebok apps file: {}", appFilename);
			List<FacebookApp> facebookApps = FacebookAppManager.readAppInfoFromFile(appFilename);
			for (FacebookApp facebookApp : facebookApps) {
				String appId = facebookApp.getAppID();
				FacebookApp appInfoInDb = db.getFacebookApp(appId);
				if (appInfoInDb == null) {
					// not existed
					db.saveFacebookApp(facebookApp);
				} else {
					// existed
					LOG.info("App existed! {}", facebookApp.toString());
				}
			}
		}

		db.shutdown();
		LOG.info("DONE!");
	}
}
