package viettel.nfw.social.facebook.pgcrawler.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.planner.Planner;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileSortedSet;
import viettel.nfw.social.facebook.pgcrawler.entities.StoredPostInfo;
import viettel.nfw.social.facebook.pgcrawler.entities.StoredProfileInfo;
import viettel.nfw.social.facebook.updatenews.graph.entities.ProfileType;
import viettel.nfw.social.utils.Funcs;
import vn.itim.detector.Language;

/**
 *
 * @author duongth5
 */
public class ProfileHandler extends AbstractHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ProfileHandler.class);

	private final ProfileDatabaseHandler db;
	private final ProfileSortedSet pageGroupSortedSet;

	public ProfileHandler(ProfileDatabaseHandler db, ProfileSortedSet pageGroupSortedSet) {
		this.db = db;
		this.pageGroupSortedSet = pageGroupSortedSet;
	}

	@Override
	public void handle(String string, Request rqst, HttpServletRequest hsr, HttpServletResponse response) throws IOException, ServletException {
		String action = rqst.getParameter("action");
		String result = "false";
		boolean isContentTypeJson = false;
		if (action != null) {
			switch (action) {
				case "add": {
					String fbid = rqst.getParameter("fbid");
					String fbtype = rqst.getParameter("fbtype");
					boolean isSpecial = false;
					String isSpecialStr = rqst.getParameter("special");
					if (StringUtils.isNotEmpty(isSpecialStr)) {
						isSpecial = Boolean.parseBoolean(isSpecialStr);
					}
					result = String.valueOf(save(fbid, fbtype, isSpecial));
					break;
				}
				case "delete": {
					String fbid = rqst.getParameter("fbid");
					result = String.valueOf(delete(fbid));
					break;
				}
				case "getprofileinfo": {
					String fbid = rqst.getParameter("fbid");
					boolean getMoreDynamicStats = false;
					String getMoreDynamicStatsStr = rqst.getParameter("viewmore");
					if (StringUtils.isNotEmpty(getMoreDynamicStatsStr)) {
						getMoreDynamicStats = Boolean.parseBoolean(getMoreDynamicStatsStr);
					}
					result = getProfileInfo(fbid, getMoreDynamicStats);
					isContentTypeJson = true;
					break;
				}
				case "getpostinfo": {
					String fbid = rqst.getParameter("postid");
					result = getPostInfo(fbid);
					isContentTypeJson = true;
					break;
				}
				case "getallspecial": {
					result = getAllSpecial();
					isContentTypeJson = true;
					break;
				}
				default:
					break;
			}
		}
		response.setStatus(HttpServletResponse.SC_OK);
		if (isContentTypeJson) {
			// set content type is json
			response.setContentType("application/json");

			// check support jsonp
			String callback = rqst.getParameter("callback");
			if (StringUtils.isNotEmpty(callback)) {
				StringBuilder sb = new StringBuilder();
				sb.append(callback).append("(").append(result).append(")");
				result = sb.toString();
			}
		} else {
			// set content type is plain text
			response.setContentType("text/plain");
		}
		response.setCharacterEncoding("UTF-8");
		response.getOutputStream().write(result.getBytes());
		response.getOutputStream().close();
	}

	private boolean save(String fbid, String fbtype, boolean isSpecial) {
		boolean isOK = false;
		if (StringUtils.isNotEmpty(fbid) && StringUtils.isNotEmpty(fbtype) && fbid.matches("^[0-9]+$")) {
			if (fbtype.equals("page_real") || fbtype.equals("group_public")) {
				// save to disk
				saveToDisk(fbid, fbtype);
				// save to memory
				saveToMem(fbid, isSpecial);
				isOK = true;
			}
		}
		return isOK;
	}

	private boolean saveToDisk(String fbid, String fbtype) {
		boolean added = true;
		try {
			StoredProfileInfo storedProfileInfoInDb = db.getStoredProfileInfo(fbid);
			if (storedProfileInfoInDb == null) {
				// not existed
				storedProfileInfoInDb = new StoredProfileInfo(fbid);
				storedProfileInfoInDb.setLanguage(Language.UNKNOWN);
				storedProfileInfoInDb.setProfileType(ProfileType.getByShortName(fbtype));
			} else {
				// existed -> update
				storedProfileInfoInDb.setProfileType(ProfileType.getByShortName(fbtype));
			}
			db.saveStoredProfileInfo(storedProfileInfoInDb);
		} catch (IOException ex) {
			LOG.error("Error while add profile to disk", ex);
			added = false;
		}
		return added;
	}

	private void saveToMem(String fbid, boolean isSpecial) {
		long buff = Funcs.randInt(2 * 60 * 1000, 5 * 60 * 1000);
		double currentTime = System.currentTimeMillis() * 1.0 + buff * 1.0;
		pageGroupSortedSet.addToQueue(fbid, currentTime);
		if (isSpecial) {
			db.saveToSpecialList(fbid);
		}
	}

	private boolean delete(String profileId) {
		boolean isOK = false;
		if (StringUtils.isNotEmpty(profileId) && profileId.matches("^[0-9]+$")) {
			// delete from disk
			deleteFromDisk(profileId);
			// delete from memory
			deleteFromMem(profileId);
			// delete from special list
			deleteFromSpecialList(profileId);
			isOK = true;
		}
		return isOK;
	}

	private void deleteFromDisk(String fbid) {
		try {
			StoredProfileInfo storedProfileInfoInDb = db.getStoredProfileInfo(fbid);
			if (storedProfileInfoInDb != null) {
				db.deleteStoredProfileInfo(fbid);
			}
		} catch (IOException ex) {
			LOG.error("Error while delete profile from disk", ex);
		}
	}

	private void deleteFromMem(String fbid) {
		pageGroupSortedSet.remove(fbid);
	}
	
	private void deleteFromSpecialList(String fbid){
		db.removeFormSpecialList(fbid);
	}

	private String getFullnameOfProfileId(String profileId) {
		Map<String, Object> resultMap = new HashMap<>();
		if (StringUtils.isNotEmpty(profileId) && profileId.matches("^[0-9]+$")) {
			try {
				String fullname = db.getFullNameOfProfileId(profileId);
				if (StringUtils.isNotEmpty(fullname)) {
					resultMap.put("id", profileId);
					resultMap.put("fullname", fullname);
				} else {
					resultMap.put("code", 404);
					resultMap.put("message", "Fullname of profile " + profileId + " not found");
				}
			} catch (Exception ex) {
				LOG.error("Error while getting fullname of profileId " + profileId, ex);
				resultMap.put("code", 500);
				resultMap.put("message", "Something is wrong!");
			}
		} else {
			resultMap.put("code", 100);
			resultMap.put("message", "Please input profile ID!");
		}
		return JSON.encode(resultMap);
	}

	private String getProfileInfo(String fbid, boolean getMoreDynamicStats) {
		StoredProfileInfo storedProfile = new StoredProfileInfo(fbid);
		try {
			storedProfile = db.getStoredProfileInfo(fbid);
		} catch (IOException ex) {
			LOG.error("Error while getting profile info of " + fbid, ex);
		}
		String storedProfileJson = JSON.encode(storedProfile);
		if (!getMoreDynamicStats) {
			return storedProfileJson;
		}
		if (storedProfile == null) {
			return storedProfileJson;
		}
		JsonParser jsonParser = new JsonParser();
		JsonElement jsonElement = jsonParser.parse(storedProfileJson);
		if (jsonElement == null || jsonElement.isJsonNull()) {
			return storedProfileJson;
		}
		// add more properties
		// next crawlTime
		long nextCrawlTime = -1;
		Double scoreDouble = pageGroupSortedSet.getScore(fbid);
		if (scoreDouble != null) {
			nextCrawlTime = scoreDouble.longValue();
		}
		jsonElement.getAsJsonObject().addProperty("nextCrawlTime", nextCrawlTime);

		// in queue to wait crawl
		boolean inQueue = Planner.toCrawlQueue.contains(fbid);
		jsonElement.getAsJsonObject().addProperty("inQueue", inQueue);

		// is crawling
		boolean isCrawling = false;
		long timeCrawling = -1;
		if (Planner.profileInCrawlingMap.containsKey(fbid)) {
			isCrawling = true;
			long startCrawlingTime = Planner.profileInCrawlingMap.get(fbid);
			timeCrawling = System.currentTimeMillis() - startCrawlingTime;
		}
		JsonElement inCrawlingEl = new JsonObject();
		inCrawlingEl.getAsJsonObject().addProperty("isCrawling", isCrawling);
		inCrawlingEl.getAsJsonObject().addProperty("timeCrawling", timeCrawling);
		jsonElement.getAsJsonObject().add("inCrawling", inCrawlingEl);

		// in special list
		boolean isInSpecialList = db.containsInSpecialList(fbid);
		jsonElement.getAsJsonObject().addProperty("isInSpecialList", isInSpecialList);

		return jsonElement.toString();
	}

	private String getPostInfo(String postid) {
		StoredPostInfo storedPost = new StoredPostInfo();
		storedPost.setPostId(postid);
		try {
			storedPost = db.getStoredPostInfo(postid);
		} catch (IOException ex) {
			LOG.error("Error while getting profile info of " + postid, ex);
		}
		return JSON.encode(storedPost);
	}

	private String getAllSpecial() {
		Set<String> profiles = db.getAllProfileInSpecialList();
		return JSON.encode(profiles);
	}

	public static void main(String[] args) {
		StoredProfileInfo storedProfile = new StoredProfileInfo("11111111111111");
		String storedProfileJson = JSON.encode(storedProfile);
		System.out.println(storedProfileJson);
		long nextCrawlTime = 1234566;
		JsonParser jsonParser = new JsonParser();
		JsonElement jsonElement = jsonParser.parse(storedProfileJson);
		if (jsonElement == null || jsonElement.isJsonNull()) {
			System.out.println("null ----");
			return;
		}
		jsonElement.getAsJsonObject().addProperty("nextCrawlTime", nextCrawlTime);
		boolean inQueue = true;
		jsonElement.getAsJsonObject().addProperty("inQueue", inQueue);
		JsonElement el = new JsonObject();
		el.getAsJsonObject().addProperty("isCrawling", true);
		el.getAsJsonObject().addProperty("howlong", 12345);
		jsonElement.getAsJsonObject().add("inCrawling", el);
		System.out.println(jsonElement.toString());
	}

}
