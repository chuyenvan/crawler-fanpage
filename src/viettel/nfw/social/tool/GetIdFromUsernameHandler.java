package viettel.nfw.social.tool;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.arnx.jsonic.JSON;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.Pair;

/**
 *
 * @author duongth5
 */
public class GetIdFromUsernameHandler extends AbstractHandler {

	private static final Logger LOG = LoggerFactory.getLogger(GetIdFromUsernameHandler.class);

	private static final ResponseMessage EMPTY_USERNAME = new ResponseMessage(101, "Please enter username!");
	private static final ResponseMessage INVALID_USERNAME = new ResponseMessage(102, "Invalid input. Username contains [0-9a-zA-Z.]; if profile is group, group_<usename>");
	private static final ResponseMessage CANNOT_FIND_ID = new ResponseMessage(103, "Cannot find id of this username!");
	private static final ResponseMessage WAIT_FOR_DOWNLOAD = new ResponseMessage(104, "We are trying to find id of this username. Please try again in some minutes!");
	private static final ResponseMessage UNKNOWN_ERROR = new ResponseMessage(105, "Unknown error.");
	private static final ResponseMessage REQUEST_TOO_MUCH = new ResponseMessage(106, "You are request to much! We are trying to find id of this username");

	private static final long TIMEOUT = 1000L;
	private static final String PREFIX_GROUP = "group_";

	private final DatabaseHandler db;

	public GetIdFromUsernameHandler(DatabaseHandler db) {
		this.db = db;
	}

	@Override
	public void handle(String target, Request req, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		String username = request.getParameter("username");
		response.setStatus(HttpServletResponse.SC_OK);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");

		StringBuilder messageSb = new StringBuilder();

		if (StringUtils.isNotEmpty(username)) {
			// validate username
			username = username.trim().toLowerCase();
			if (validate(username)) {
				boolean isGroup = false;
				if (StringUtils.startsWithIgnoreCase(username, PREFIX_GROUP)) {
					isGroup = true;
					username = StringUtils.substring(username, PREFIX_GROUP.length());
				}
				// check in mapping table (un => id)
				// get id from repository
				String id = db.getId(username);
				if (id != null) {
					// if exist
					ResponseData respData = new ResponseData(id, username);
					messageSb.append(JSON.encode(respData));
				} else {
					// check in error list
					if (db.containsErrorUsername(username)) {
						messageSb.append(JSON.encode(CANNOT_FIND_ID));
					} else {
						boolean isAddToQueue = false;
						if (SocialService.inProgressSet.contains(username)) {
							messageSb.append(JSON.encode(REQUEST_TOO_MUCH));
						} else {
							try {
								// tryhard to find this ID
								// add to progress set
								SocialService.inProgressSet.add(username);
								// add to queue to download
								isAddToQueue = SocialService.needToDownload.offer(new Pair<>(username, isGroup), TIMEOUT, TimeUnit.SECONDS);
							} catch (InterruptedException ex) {
								LOG.error(ex.getMessage(), ex);
							}
							if (isAddToQueue) {
								messageSb.append(JSON.encode(WAIT_FOR_DOWNLOAD));
							} else {
								messageSb.append(JSON.encode(UNKNOWN_ERROR));
							}
						}
					}
				}
			} else {
				messageSb.append(JSON.encode(INVALID_USERNAME));
			}
		} else {
			messageSb.append(JSON.encode(EMPTY_USERNAME));
		}

		response.getOutputStream().write(messageSb.toString().getBytes());
		response.getOutputStream().close();
	}

	private static boolean validate(String username) {
		boolean isValid = false;
		String temp = username;
		if (StringUtils.startsWithIgnoreCase(temp, PREFIX_GROUP)) {
			temp = StringUtils.substring(temp, PREFIX_GROUP.length());
		}
		if (StringUtils.isNotEmpty(temp)) {
			if (temp.matches("^[0-9a-zA-Z.]+$")) {
				isValid = true;
			}
		}
		return isValid;
	}

	public static void main(String[] args) {
		String str = "group_cskh.viettel";
		System.out.println(str);
		str = StringUtils.substring(str, "group_".length());
		System.out.println(str);
		System.exit(-3);

		System.out.println("DuongTH5  ".trim().toLowerCase());
		String[] testStrs = new String[]{"chuyennd", " thiendn2", "group_asd fdssfds", "group_CSKH.Viettel"};

		for (String testStr : testStrs) {
			System.out.println(testStr + " " + validate(testStr.trim()));
		}
	}

	private static class ResponseData {

		public String id;
		public String username;

		public ResponseData(String id, String username) {
			this.id = id;
			this.username = username;
		}

	}

	private static class ResponseMessage {

		public int code;
		public String message;

		public ResponseMessage(int code, String message) {
			this.code = code;
			this.message = message;
		}

	}

}
