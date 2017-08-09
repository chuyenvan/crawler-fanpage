package viettel.nfw.social.facebook.updatenews.repo;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import org.apache.commons.lang3.StringUtils;
import static org.fusesource.leveldbjni.JniDBFactory.asString;
import org.nigma.engine.util.Funcs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.RunUpdateNews;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectType;
import viettel.nfw.social.facebook.updatenews.graph.entities.SocialType;
import viettel.nfw.social.model.facebook.FacebookObject;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.TParser;
import vn.itim.engine.nspider.url.Url;
import vn.viettel.social.utils.HttpResponseInfo;
import vn.viettel.utils.CustomizedFixedThreadPool;
import vn.viettel.utils.SerializeObjectUtils;

/**
 *
 * @author duongth5
 */
public class DbTest {

	private static final Logger LOG = LoggerFactory.getLogger(DbTest.class);

	public static final String FORMAT_COMPOSITE_KEY = "%s#%s#%s";

	public static void main(String[] args) throws IOException {
//        String dbPath = "/data01/duongth5/updatenews/result/updatenews/database/crawled_fbobjs.db";
//        crawledData(dbPath);
//        migrateDataMapId();
//        showdb();
		// migrateDataObjectRequest();
		// Funcs.sleep(2000);
//        migrateDataLastCrawled();

//        extractId();
//        extractLastCrawled();
//         checkApp();
		readObjectRequest();
	}

	private static void readObjectRequest() throws IOException {
		String dbPath = "D:\\git\\object_request.db";
		ObjectRequestRepository objectRequestRepository = new ObjectRequestRepository(dbPath);
		Map<String, ObjectRequest> data = objectRequestRepository.getAllData();
		System.out.println(data.size());
		for (Map.Entry<String, ObjectRequest> entrySet : data.entrySet()) {
			String key = entrySet.getKey();
			ObjectRequest value = entrySet.getValue();
			System.out.println(key);
		}
	}

	private static void checkApp() throws IOException {
//        FacebookAppRepository facebookAppRepository = FacebookAppRepository.getInstance();
//        Map<String, FacebookApp> data = facebookAppRepository.getAllData();
//        System.out.println(data.size());
		String dbPath = "D:\\git\\object_request.db";
		ObjectRequestRepository objectRequestRepository = new ObjectRequestRepository(dbPath);
		Map<String, ObjectRequest> data = objectRequestRepository.getAllData();
		System.out.println(data.size());

		MappingUsername2IdRepository mUnIdRepository = MappingUsername2IdRepository.getInstance();
		Map<String, String> un2Id = mUnIdRepository.getAllData();

		try {
			List<String> rows = FileUtils.readList(new File("socials_2.txt"));

			Set<String> usernames = new HashSet<>();
			Set<ObjectRequest> objectRequests = new HashSet<>();

			for (String row : rows) {
				if (StringUtils.isEmpty(row)) {
					continue;
				}
				ObjectRequest objRequest = new ObjectRequest();
				Url url = new Url(row);
				String path = url.getPath();
				String id = "";

				objRequest.socialType = SocialType.FACEBOOK;
				objRequest.objectType = ObjectType.PAGE;
				objRequest.loopTimeTimeMillis = 4 * 60 * 60 * 1000;

				if (path.startsWith("/pages/")) {
					id = TParser.getOneInGroup(path, "[0-9]{10,}");
				} else if (path.startsWith("/groups/")) {
					objRequest.objectType = ObjectType.GROUP;
					String usernameOrId = StringUtils.replace(path, "/groups/", "");
					usernameOrId = StringUtils.replace(usernameOrId, "/", "");
					if (StringUtils.isNotEmpty(usernameOrId)) {
						if (usernameOrId.matches("^[0-9]+$")) {
							// this group url has ID
							id = usernameOrId;
						} else {
							// this group url has username
							if (un2Id.containsKey(usernameOrId)) {
								id = un2Id.get(usernameOrId);
							}
						}
					}
				} else if (path.startsWith("/profile.php")) {
					id = TParser.getOneInGroup(row, "[0-9]{10,}");
				} else {
					String usernameOrId = StringUtils.replace(path, "/", "");
					if (usernameOrId.matches("^[0-9]+$")) {
						// this group url has ID
						id = usernameOrId;
					} else {
						// this group url has username
						if (un2Id.containsKey(usernameOrId)) {
							id = un2Id.get(usernameOrId);
						}
					}
				}
				if (StringUtils.isEmpty(id)) {
					String username = StringUtils.replace(path, "/groups/", "");
					username = StringUtils.replace(username, "/", "");
//                    System.out.println(username);
					usernames.add(username);
				} else {
					objRequest.objectID = id;
					objectRequests.add(objRequest);
//                    System.out.println(objRequest.toString());
				}
			}

			for (String username : usernames) {
				ObjectRequest objRequest = new ObjectRequest();

				objRequest.socialType = SocialType.FACEBOOK;
				objRequest.objectType = ObjectType.PAGE;
				objRequest.loopTimeTimeMillis = 4 * 60 * 60 * 1000;

				if (username.equalsIgnoreCase("Admin5giay")) {
					objRequest.objectType = ObjectType.GROUP;
				}
				String id = downloadId(username);
				if (StringUtils.isEmpty(id)) {
					System.out.println(username);
				} else {
//                    System.out.println(username + "\t" + id);
					objRequest.objectID = id;
					objectRequests.add(objRequest);
				}
				Funcs.sleep(500);
			}

			System.out.println(objectRequests.size());

			for (ObjectRequest objectRequest : objectRequests) {
				// write to db
				try {
					String key = String.format(RunUpdateNews.FORMAT_COMPOSITE_KEY,
						objectRequest.socialType, objectRequest.objectID, objectRequest.objectType);
					byte[] keyByteArr = key.getBytes();
					byte[] valueByteArr = SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(objectRequest);
					objectRequestRepository.write(keyByteArr, valueByteArr);
					LOG.info("Saved {} to db with key {}", objectRequest.toString(), key);
				} catch (IOException ex) {
					LOG.error(ex.getMessage(), ex);
				}
			}

		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}
	}

	private static String downloadId(String username) {
		String id = "";
		String accessToken = "CAACEdEose0cBAOOHarSVKZCzbcxQ9TsNzsYmCB4obvsgGHy5sZAtf7ol1K9zaaYVrbZCLRr8PP9uGckZAVF1ryF0tVoXPH11pWIX0Nhy4Nx2yJcMkPBihF3euNdVvnFEUbEaJK5Skhy9jyNZAS0OFn4R2w8zcgQkPZBDXZBXE7P7ZBwZCIs5q06uVIgOHnYx2IaReldnxFhQJ5AZDZD";
		String url = "https://graph.facebook.com/v2.4/" + username + "?access_token=" + accessToken;
		Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("192.168.4.13", 3128));

		try {
			HttpResponseInfo response = vn.viettel.social.utils.Utils.singleGet(url, proxy);
			if (response.getStatus() == 200) {
				String responseBody = response.getBody();
				if (StringUtils.isNotEmpty(responseBody)) {
					Gson gson = new Gson();
					JsonParser jsonParser = new JsonParser();
					JsonElement jsonElement = jsonParser.parse(responseBody);
					JsonObject jsonObject = jsonElement.getAsJsonObject();
					JsonElement dataElement = jsonObject.get("id");
					if (dataElement != null) {
						id = dataElement.getAsString();
					}
				}
			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}

		return id;
	}

	private static void extractId() {
		try {
			ObjectRequestRepository newObjRequestRepo = new ObjectRequestRepository("D:/object_request.db");
			Map<String, ObjectRequest> data = newObjRequestRepo.getAllData();
			Set<String> profileIds = new HashSet<>();
			for (Map.Entry<String, ObjectRequest> entrySet : data.entrySet()) {
				String key = entrySet.getKey();
				ObjectRequest value = entrySet.getValue();
				profileIds.add(value.objectID);
			}
			FileUtils.write(new File("out.txt"), profileIds);
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}
	}

	private static void migrateDataMapId() {
		try {
			MappingUsername2IdRepository oldMapping = new MappingUsername2IdRepository("/data01/duongth5/updatenews/result/updatenews/database/mapping_id_username.db");
			MappingUsername2IdRepository newMapping = new MappingUsername2IdRepository("/data01/duongth5/updatenews/database/mapping_username2id.db");
			Map<String, String> oldData = oldMapping.getAllData();
			for (Map.Entry<String, String> entrySet : oldData.entrySet()) {
				String oldId = entrySet.getKey();
				String oldUsername = entrySet.getValue();
				LOG.info("{} - {}", oldUsername, oldId);
				if (StringUtils.equalsIgnoreCase(oldId, oldUsername)) {
					// by pass
				} else {
					String newKey = oldUsername.toLowerCase();
					String newValue = oldId.toLowerCase();
					newMapping.write(newKey.getBytes(), newValue.getBytes());
				}
			}

			Funcs.sleep(2000);

			oldMapping.close();
			newMapping.close();
			LOG.info("DONE!!!");

		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}
	}

	private static void showdb() {
		try {
			LastCrawledRepository newLastCrawledRepository = new LastCrawledRepository("/data01/duongth5/updatenews/database/last_crawled.db");
			Map<String, String> oldData = newLastCrawledRepository.getAllData();
			LOG.info("size {}", oldData.size());
			for (Map.Entry<String, String> entrySet : oldData.entrySet()) {
				String username = entrySet.getKey();
				String id = entrySet.getValue();
				LOG.info("{} - {}", username, id);
			}
			LOG.info("DONE!!!");

		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}
	}

	private static void migrateDataObjectRequest() {
		try {
			ObjectRequestRepository oldObjRequestRepo = new ObjectRequestRepository("/data01/duongth5/updatenews/result/updatenews/database/object_request.db");
			ObjectRequestRepository newObjRequestRepo = new ObjectRequestRepository("/data01/duongth5/updatenews/database/object_request.db");

			MappingUsername2IdRepository newMapping = new MappingUsername2IdRepository("/data01/duongth5/updatenews/database/mapping_username2id.db");

			Map<String, ObjectRequest> oldData = oldObjRequestRepo.getAllData();

			List<String> notValid = new ArrayList<>();

			for (Map.Entry<String, ObjectRequest> entrySet : oldData.entrySet()) {
				ObjectRequest objectRequest = entrySet.getValue();
				if (objectRequest.objectID.matches("^[0-9]+$")) {
					try {
						String key = String.format(FORMAT_COMPOSITE_KEY,
							objectRequest.socialType, objectRequest.objectID, objectRequest.objectType);
						byte[] keyByteArr = key.getBytes();
						byte[] valueByteArr = SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(objectRequest);
						newObjRequestRepo.write(keyByteArr, valueByteArr);
						LOG.info("Saved {} to db with key {}", objectRequest.toString(), key);
					} catch (IOException ex) {
						LOG.error(ex.getMessage(), ex);
					}
				} else {
					String username = objectRequest.objectID.toLowerCase();
					// write to file then add later
					boolean isExistedInDB = false;
					byte[] idBytes = newMapping.get(username.getBytes());
					if (idBytes != null) {
						String idStr = asString(idBytes);
						if (StringUtils.isNotEmpty(idStr)) {
							objectRequest.objectID = idStr;
							String key = String.format(FORMAT_COMPOSITE_KEY,
								objectRequest.socialType, objectRequest.objectID, objectRequest.objectType);
							byte[] keyByteArr = key.getBytes();
							byte[] valueByteArr = SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(objectRequest);
							newObjRequestRepo.write(keyByteArr, valueByteArr);
							LOG.info("Saved {} to db with key {}", objectRequest.toString(), key);
							isExistedInDB = true;
						}
					}

					// if not found in db, go to crawl
					if (!isExistedInDB) {
						StringBuilder sb = new StringBuilder();
						sb.append(objectRequest.socialType);
						sb.append("\t");
						sb.append(objectRequest.objectID);
						sb.append("\t");
						sb.append(objectRequest.objectType);
						sb.append("\t");
						sb.append(objectRequest.loopTimeTimeMillis);
						notValid.add(sb.toString());
					}
				}
			}

			Funcs.sleep(2000);
			oldObjRequestRepo.close();
			newObjRequestRepo.close();
			newMapping.close();
			FileUtils.write(new File("notvalid.txt"), notValid);
			LOG.info("DONE!!!");

		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	private static void migrateDataLastCrawled() {
		try {
			LastCrawledRepository oldLastCrawledRepository = new LastCrawledRepository("/data01/duongth5/updatenews/result/updatenews/database/last_crawled.db");
			final LastCrawledRepository newLastCrawledRepository = new LastCrawledRepository("/data01/duongth5/updatenews/database/last_crawled.db");
			ObjectRequestRepository newObjRequestRepo = new ObjectRequestRepository("/data01/duongth5/updatenews/database/object_request.db");
			final MappingUsername2IdRepository newMapping = new MappingUsername2IdRepository("/data01/duongth5/updatenews/database/mapping_username2id.db");

			final Map<String, ObjectRequest> newObjReqData = newObjRequestRepo.getAllData();
			Map<String, String> oldData = oldLastCrawledRepository.getAllData();

			CustomizedFixedThreadPool pool = new CustomizedFixedThreadPool(10, 50000, "Push");

			for (final Map.Entry<String, String> entrySet : oldData.entrySet()) {

				pool.execute(new Runnable() {

					@Override
					public void run() {
						String oldKey = entrySet.getKey();
						String oldValue = entrySet.getValue();
						LOG.info("old: {} - {}", oldKey, oldValue);

						String newKey = StringUtils.replace(oldKey, "FACEBOOK_", "");

						if (newKey.matches("^[0-9]+$")) {
							try {
								for (Map.Entry<String, ObjectRequest> entrySet1 : newObjReqData.entrySet()) {
									String key = entrySet1.getKey();
									ObjectRequest objectRequest = entrySet1.getValue();
									if (StringUtils.contains(key, newKey)) {
										String wkey = String.format(FORMAT_COMPOSITE_KEY,
											objectRequest.socialType, objectRequest.objectID, objectRequest.objectType);
										LOG.info("new: {} - {}", wkey, oldValue);
										newLastCrawledRepository.write(wkey.getBytes(), oldValue.getBytes());
										break;
									}
								}
							} catch (IOException ex) {
								LOG.error(ex.getMessage(), ex);
							}
						} else {
							String username = newKey.toLowerCase();
							// write to file then add later
							byte[] idBytes = newMapping.get(username.getBytes());
							if (idBytes != null) {
								String idStr = asString(idBytes);
								if (StringUtils.isNotEmpty(idStr)) {
									for (Map.Entry<String, ObjectRequest> entrySet1 : newObjReqData.entrySet()) {
										String key = entrySet1.getKey();
										ObjectRequest objectRequest = entrySet1.getValue();
										if (StringUtils.contains(key, idStr)) {
											try {
												String wkey = String.format(FORMAT_COMPOSITE_KEY,
													objectRequest.socialType, objectRequest.objectID, objectRequest.objectType);
												LOG.info("new: {} - {}", wkey, oldValue);
												newLastCrawledRepository.write(wkey.getBytes(), oldValue.getBytes());
												break;
											} catch (IOException ex) {
												java.util.logging.Logger.getLogger(DbTest.class.getName()).log(Level.SEVERE, null, ex);
											}
										}
									}
								}
							}
						}
					}
				});

			}

			Funcs.sleep(2000);

//            oldLastCrawledRepository.close();
//            newLastCrawledRepository.close();
//            newObjRequestRepo.close();
//            newMapping.close();
			LOG.info("DONE!!!");

		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}
	}

	private static void crawledData(String dbPath) {
		try {
			CrawledFacebookObjectRepository crawledFacebookObjectRepo = new CrawledFacebookObjectRepository(dbPath);
			Set<String> keys = crawledFacebookObjectRepo.getAllKeys();
			LOG.info("keys size {}", keys.size());
			Set<FacebookObject> groups = new HashSet<>();
			int count = 0;
			for (String key : keys) {
				FacebookObject value = (FacebookObject) SerializeObjectUtils.readingObjectFromByteArrayUsingGzip(crawledFacebookObjectRepo.get(key.getBytes()));
				String type = value.getInfo().getType();
				if (count < 300) {
					if (StringUtils.equalsIgnoreCase(type, "group")) {
						groups.add(value);
						count++;
					}
				}
			}

			LOG.info("groups size {}", groups.size());
			List<String> rows = new ArrayList<>();
			for (FacebookObject group : groups) {
				try {
					String id = group.getInfo().getId();
					String username = group.getInfo().getUsername();
					String fullname = group.getInfo().getFullname();
					String type = group.getInfo().getType();

					String row = id + "\t" + username + "\t" + fullname + "\t" + type;
					rows.add(row);

				} catch (Exception e) {
					LOG.error(e.getMessage(), e);
				}
			}

			FileUtils.write(new File("groups.txt"), rows);

		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	private static void test() {
		try {
//            LastCrawledRepository lastCrawledRepository = new LastCrawledRepository("data2/update-news/20150629/last_crawled.db");
			ObjectRequestRepository objRequestRepository = new ObjectRequestRepository("data2/update-news/20150629/object_request.db");
//            ProfilePostsRepository profiePostsRepository = new ProfilePostsRepository("data2/update-news/20150629/profile_posts.db");

//            Map<String, String> lastCrawled = lastCrawledRepository.getAllData();
			Map<String, ObjectRequest> objRequest = objRequestRepository.getAllData();
//            List<String> profilePost = profiePostsRepository.getAllKeys();

			int count6h = 0;
			int countOther = 0;
			for (Map.Entry<String, ObjectRequest> entrySet : objRequest.entrySet()) {
				String key = entrySet.getKey();
				ObjectRequest value = entrySet.getValue();

				if (value.loopTimeTimeMillis < (7 * 60 * 60 * 1000)) {
					count6h++;
				} else {
					countOther++;
				}
			}

			LOG.info("size {}", count6h);
			LOG.info("size {}", countOther);
			LOG.info("DONE!");

		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
	}

	private static void extractLastCrawled() {
		try {
			LastCrawledRepository lastCrawledRepository = LastCrawledRepository.getInstance();
			Map<String, String> data = lastCrawledRepository.getAllData();
			List<String> profileIds = new ArrayList<>();
			for (Map.Entry<String, String> entrySet : data.entrySet()) {
				String key = entrySet.getKey();
				String value = entrySet.getValue();
				profileIds.add(key + "\t" + value);
			}
			System.out.println(data.size());
			FileUtils.write(new File("profileAndLastCrawledTime.txt"), profileIds);
		} catch (FileNotFoundException ex) {
			java.util.logging.Logger.getLogger(DbTest.class.getName()).log(Level.SEVERE, null, ex);
		}
	}
}
