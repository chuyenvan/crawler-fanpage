package viettel.nfw.social.facebook.pgcrawler.web;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.pgcrawler.entities.StoredProfileInfo;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.facebook.updatenews.graph.entities.ProfileType;
import vn.itim.detector.Language;

/**
 *
 * @author duongth5
 */
public class AdminHandler extends AbstractHandler {

	private final ProfileDatabaseHandler db;

	public AdminHandler(ProfileDatabaseHandler db) {
		this.db = db;
	}

	@Override
	public void handle(String string, Request rqst, HttpServletRequest hsr, HttpServletResponse response) throws IOException, ServletException {
		String query = rqst.getParameter("query");
		String result = "";
		boolean isContentTypeJson = false;
		if (query != null) {
			switch (query) {
				case "profiles": {
					Map<String, StoredProfileInfo> profileId2InfoFull = db.getAllStoredProfileInfo();
					Set<StoredProfileInfoMini> miniInfos = new HashSet<>();
					StoredProfileInfoMini miniInfo;
					for (Map.Entry<String, StoredProfileInfo> entrySet : profileId2InfoFull.entrySet()) {
						StoredProfileInfo storedProfileInfoFull = entrySet.getValue();
						miniInfo = new StoredProfileInfoMini();
						miniInfo.id = storedProfileInfoFull.getId();
						miniInfo.username = storedProfileInfoFull.getUsername();
						miniInfo.fullname = storedProfileInfoFull.getFullname();
						miniInfo.url = storedProfileInfoFull.getUrl();
						miniInfo.likesOrMembers = storedProfileInfoFull.getLikesOrMembers();
						miniInfo.language = storedProfileInfoFull.getLanguage();
						miniInfo.profileType = storedProfileInfoFull.getProfileType();
						miniInfo.postFrequency = storedProfileInfoFull.getPostFrequency();
						miniInfo.firstCrawlingTime = storedProfileInfoFull.getFirstCrawlingTime();
						miniInfo.lastCrawlingInfoTime = storedProfileInfoFull.getLastCrawlingInfoTime();
						miniInfo.lastCrawlingTimelineTime = storedProfileInfoFull.getLastCrawlingTimelineTime();
						miniInfo.lastSuccessCrawlingTimelineTime = storedProfileInfoFull.getLastSuccessCrawlingTimelineTime();
						miniInfo.crawledPostsSize = storedProfileInfoFull.getCrawledPostsSize();
						miniInfos.add(miniInfo);
					}
					ResultProfileData resultData = new ResultProfileData(miniInfos);
					result = JSON.encode(resultData);
					isContentTypeJson = true;
					break;
				}
				case "graphapps": {
					Map<String, FacebookApp> data = db.getAllFacebookApp();
					Set<FacebookApp> values = new HashSet<>();
					values.addAll(data.values());
					ResultGraphAppData resultData = new ResultGraphAppData(values);
					result = JSON.encode(resultData);
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

	private static class StoredProfileInfoMini {

		public String id;
		public String username;
		public String fullname;
		public String url;
		public long likesOrMembers;
		public Language language;
		public ProfileType profileType;
		public long postFrequency;
		public long firstCrawlingTime;
		public long lastCrawlingInfoTime;
		public long lastCrawlingTimelineTime;
		public long lastSuccessCrawlingTimelineTime;
		public int crawledPostsSize;
	}

	private static class ResultProfileData {

		public Set<StoredProfileInfoMini> data;

		public ResultProfileData() {
			data = new HashSet<>();
		}

		public ResultProfileData(Set<StoredProfileInfoMini> data) {
			this.data = data;
		}

	}

	private static class ResultGraphAppData {

		public Set<FacebookApp> data;

		public ResultGraphAppData() {
			data = new HashSet<>();
		}

		public ResultGraphAppData(Set<FacebookApp> data) {
			this.data = data;
		}

	}

}
