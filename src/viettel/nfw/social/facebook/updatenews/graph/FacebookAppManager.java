package viettel.nfw.social.facebook.updatenews.graph;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.facebook.updatenews.repo.FacebookAppRepository;
import vn.viettel.utils.SerializeObjectUtils;

/**
 *
 * @author duongth5
 */
public class FacebookAppManager {

	private static final Logger LOG = LoggerFactory.getLogger(FacebookAppManager.class);
	public static final String FACEBOOK_APP_INFO_FILENAME = "input/updatenews/login/facebook-app-2.txt";

	public static List<FacebookApp> readAppInfoFromFile(String filename) {
		List<FacebookApp> appInfos = new ArrayList<>();
		File file = new File(filename);
		try {
			try (BufferedReader br = new BufferedReader(new FileReader(file))) {
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (StringUtils.isNotEmpty(line)
							&& !StringUtils.startsWith(line, "#")) {
						String[] parts = StringUtils.split(line, "\t");
						int length = parts.length;
						if (length == 8) {
							String accountUsername = parts[0];
							String accountPassword = parts[1];
							String appName = parts[2];
							String appID = parts[3];
							String apiVersion = parts[4];
							String appSecret = parts[5];
							String appAccessToken = parts[6];
							String userAccessToken = parts[7];
							appInfos.add(new FacebookApp(accountUsername, accountPassword, appName, appID, apiVersion,
									appSecret, appAccessToken, userAccessToken));
						} else {
							LOG.warn("This line has error format: {} in file {}", line, filename);
						}
					}
				}
			}
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}
		return appInfos;
	}

	public static void writeAppInfosToDatabase(FacebookAppRepository fbAppRepo, List<FacebookApp> appInfos) {
		for (FacebookApp appInfo : appInfos) {
			byte[] key = appInfo.getAppID().getBytes();
			byte[] value = SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(appInfo);
			try {
				fbAppRepo.write(key, value);
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
	}

	private static void updateManualUserAccessToken(FacebookAppRepository fbAppRepo, Map<String, String> appID2UserAccessToken) {
		for (Map.Entry<String, String> entrySet : appID2UserAccessToken.entrySet()) {
			String appID = entrySet.getKey();
			String newUserAccessToken = entrySet.getValue();
			byte[] appInfoBytes = fbAppRepo.get(appID.getBytes());
			FacebookApp appInfo = (FacebookApp) SerializeObjectUtils.readingObjectFromByteArrayUsingGzip(appInfoBytes);
			appInfo.setUserAccessToken(newUserAccessToken);
			try {
				fbAppRepo.write(appID.getBytes(), SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(appInfo));
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
	}

	private static void deleteManualApp(FacebookAppRepository fbAppRepo, Map<String, String> appID2UserAccessToken) {
		for (Map.Entry<String, String> entrySet : appID2UserAccessToken.entrySet()) {
			String appID = entrySet.getKey();
			try {
				fbAppRepo.delete(appID.getBytes());
			} catch (IOException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
	}

	public static void main(String[] args) {
		FacebookAppRepository facebookAppRepo = FacebookAppRepository.getInstance();
		Map<String, FacebookApp> data = facebookAppRepo.getAllData();
		data.size();
//		List<FacebookApp> appInfos = readAppInfoFromFile(FACEBOOK_APP_INFO_FILENAME);
//		writeAppInfosToDatabase(facebookAppRepo, appInfos);
		try {
			facebookAppRepo.close();
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}

//        Map<String, FacebookApp> appInfos = facebookAppRepo.getAllData();
//        for (Map.Entry<String, FacebookApp> entrySet : appInfos.entrySet()) {
//            String key = entrySet.getKey();
//            FacebookApp appInfo = entrySet.getValue();
//
//            FacebookGraphActions facebookGraphActions = new FacebookGraphActions(appInfo);
//            // init the app
//            facebookGraphActions.initApp();
//            // debug current access token
//            try {
//                boolean isAccessTokenValid = facebookGraphActions.isUserAccessTokenValid(appInfo.userAccessToken);
//                LOG.info("isAccessTokenValid {}", isAccessTokenValid);
//                if (!isAccessTokenValid) {
//                    LOG.warn("User Access Token invalid");
//                } else {
//                    LOG.info("User Access Token still valid {}", appInfo.userAccessToken);
//                }
//            } catch (FacebookException ex) {
//                // debug token failed
//                LOG.info(ex.getMessage(), ex);
//                LOG.info("APP: {}", appInfo.toString());
//            }
//        }
//        Map<String, String> appID2UserAccessToken = new HashMap<>();
//        appID2UserAccessToken.put("466279283532083", "");
//        appID2UserAccessToken.put("398485887011865", "");
//        appID2UserAccessToken.put("841684722577589", "");
//        appID2UserAccessToken.put("813573925385020", "");
//        appID2UserAccessToken.put("105540826454846", "");
//        appID2UserAccessToken.put("459161630927957", "");
//        // updateManualUserAccessToken(facebookAppRepo, appID2UserAccessToken);
//        deleteManualApp(facebookAppRepo, appID2UserAccessToken);
	}
}
