package viettel.nfw.social.facebook.pgcrawler.web;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.crawler.CrawlersPool;
import viettel.nfw.social.facebook.pgcrawler.database.ProfileDatabaseHandler;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;

/**
 *
 * @author duongth5
 */
public class AppHandler extends AbstractHandler {

	private static final Logger LOG = LoggerFactory.getLogger(ProfileHandler.class);

	private final ProfileDatabaseHandler db;

	public AppHandler(ProfileDatabaseHandler db) {
		this.db = db;
	}

	@Override
	public void handle(String string, Request rqst, HttpServletRequest hsr, HttpServletResponse response) throws IOException, ServletException {
		String action = rqst.getParameter("action");
		String result = "false";
		boolean isContentTypeJson = false;
		if (action != null) {
			switch (action) {
				case "add": {
					String accountName = rqst.getParameter("accountName");
					String accountPass = rqst.getParameter("accountPass");
					String appName = rqst.getParameter("appName");
					String appID = rqst.getParameter("appID");
					String apiVersion = rqst.getParameter("apiVersion");
					String appSecret = rqst.getParameter("appSecret");
					String appAccessToken = rqst.getParameter("appAccessToken");
					String userAccessToken = rqst.getParameter("userAccessToken");
					boolean isSave = save(appID, accountName, accountPass, appName, apiVersion, appSecret, appAccessToken, userAccessToken);
					result = String.valueOf(isSave);
					break;
				}
				case "delete": {
					String appID = rqst.getParameter("appID");
					boolean isDelete = delete(appID);
					result = String.valueOf(isDelete);
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

	private boolean save(String appID, String accountName, String accountPass, String appName, String apiVersion, String appSecret, String appAccessToken, String userAccessToken) {
		boolean isSave = true;
		if (StringUtils.isEmpty(appID)) {
			return false;
		}
		FacebookApp appInfo = null;

		// query from disk
		try {
			appInfo = db.getFacebookApp(appID);
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}
		// if not exist, init the new once
		if (appInfo == null) {
			appInfo = new FacebookApp();
			appInfo.setAppID(appID);
		}

		if (StringUtils.isNotEmpty(accountName)) {
			appInfo.setAccountName(accountName);
		}
		if (StringUtils.isNotEmpty(accountPass)) {
			appInfo.setAccountPass(accountPass);
		}
		if (StringUtils.isNotEmpty(appName)) {
			appInfo.setAppName(appName);
		}
		if (StringUtils.isNotEmpty(apiVersion)) {
			appInfo.setApiVersion(apiVersion);
		}
		if (StringUtils.isNotEmpty(appSecret)) {
			appInfo.setAppSecret(appSecret);
		}
		if (StringUtils.isNotEmpty(appAccessToken)) {
			appInfo.setAppAccessToken(appAccessToken);
		}
		if (StringUtils.isNotEmpty(userAccessToken)) {
			appInfo.setUserAccessToken(userAccessToken);
		}
		// add to disk
		try {
			db.saveFacebookApp(appInfo);
		} catch (IOException ex) {
			LOG.error(ex.getMessage(), ex);
		}
		// add to mem
		db.saveToAppsList(appID);
		// add to update map
		CrawlersPool.appId2UpdateAppInfo.put(appID, appInfo);
		try {
			if (CrawlersPool.problemAppIds.contains(appID)) {
				CrawlersPool.problemAppIds.remove(appID);
			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}

		return isSave;
	}

	private boolean delete(String appID) {
		boolean isDelete = true;
		db.removeFromAppsList(appID);
		CrawlersPool.activeAppIds.remove(appID);
		CrawlersPool.appId2UpdateAppInfo.remove(appID);
		return isDelete;
	}
}
