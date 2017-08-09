package viettel.nfw.social.facebook.pgcrawler.tool;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.RunUpdateNews;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectType;
import viettel.nfw.social.facebook.updatenews.graph.entities.SocialType;
import viettel.nfw.social.utils.TParser;

/**
 *
 * @author duongth5
 */
public class Parser {

	private static final Logger LOG = LoggerFactory.getLogger(Parser.class);

	private static final long DEFAULT_LOOP_TIME_PROFILE = 6L * 60L * 60L * 1000L;
	private static final long DEFAULT_LOOP_TIME_POST = 12L * 60L * 60L * 1000L;
	private static final String FORMAT_COMPOSITE_POST_ID = "%s_%s"; // profileId_postID

	private static List<ObjectRequest> extractUsernameOrId(URI uri) {
		List<ObjectRequest> result = new ArrayList<>();

		String host = uri.getHost();
		// check hostname contains facebook.com
		if (StringUtils.containsIgnoreCase(host, "facebook.com")) {
			String path = uri.getPath();
			String query = uri.getQuery();

			if (StringUtils.startsWithIgnoreCase(path, "/groups/")) {
				// case groups
				String usernameOrId = StringUtils.replace(path, "/groups/", "");
				usernameOrId = StringUtils.replace(usernameOrId, "/", "");
				if (StringUtils.isNotEmpty(usernameOrId)) {
					if (usernameOrId.matches("^[0-9]+$")) {
						// this group url has ID
						result.add(new ObjectRequest(SocialType.FACEBOOK, usernameOrId, ObjectType.GROUP, DEFAULT_LOOP_TIME_PROFILE));
					} else {
						// this group url has username
					}
				}
			} else if (StringUtils.startsWithIgnoreCase(path, "/pages/")) {
				// case pages with URL https://www.facebook.com/pages/
				String id = TParser.getOneInGroup(path, "[0-9]{10,}");
				if (StringUtils.isNotEmpty(id)) {
					result.add(new ObjectRequest(SocialType.FACEBOOK, id, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
				}
			} else if (StringUtils.equalsIgnoreCase(path, "/profile.php")) {
				// case pages with URL contains ID
				String id = TParser.getOneInGroup(query, "[0-9]{10,}");
				if (StringUtils.isNotEmpty(id)) {
					result.add(new ObjectRequest(SocialType.FACEBOOK, id, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
				}
			} else if (path.matches("^/[0-9a-zA-Z.]+$")) {
				// case pages with URL contains Username
				String username = StringUtils.replace(path, "/", "");
				if (StringUtils.isNotEmpty(username)) {
					result.add(new ObjectRequest(SocialType.FACEBOOK, username, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
				}
			} else if (path.matches("^/[0-9a-zA-Z.]+/posts/[0-9]+/?$")) {
				// case post with URL https://vi-vn.facebook.com/zeddvietnam/posts/807057936074391
				String[] parts = StringUtils.split(path, "/");
				if (parts.length == 3) {
					String profileIdOrUn = parts[0];
					String postId = parts[2];
					if (StringUtils.isNotEmpty(profileIdOrUn) && StringUtils.isNotEmpty(postId)) {
						result.add(new ObjectRequest(SocialType.FACEBOOK, profileIdOrUn, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
						String compositePostId = String.format(FORMAT_COMPOSITE_POST_ID, profileIdOrUn, postId);
						result.add(new ObjectRequest(SocialType.FACEBOOK, compositePostId, ObjectType.POST, DEFAULT_LOOP_TIME_POST));
					}
				}
			} else if (path.matches("^/[0-9a-zA-Z.]+/videos/[0-9]+/?$")) {
				// case post with URL https://www.facebook.com/taychaytanhiepphat/videos/1580225455528404/
				String[] parts = StringUtils.split(path, "/");
				if (parts.length == 3) {
					String profileIdOrUn = parts[0];
					String postId = parts[2];
					if (StringUtils.isNotEmpty(profileIdOrUn) && StringUtils.isNotEmpty(postId)) {
						result.add(new ObjectRequest(SocialType.FACEBOOK, profileIdOrUn, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
						String compositePostId = String.format(FORMAT_COMPOSITE_POST_ID, profileIdOrUn, postId);
						result.add(new ObjectRequest(SocialType.FACEBOOK, compositePostId, ObjectType.POST, DEFAULT_LOOP_TIME_POST));
					}
				}
			} else if (StringUtils.equalsIgnoreCase(path, "/permalink.php")) {
				// case post with URL https://www.facebook.com/permalink.php?story_fbid=341293776069362&id=318099571722116
				Map<String, List<String>> params = viettel.nfw.social.facebook.core.Parser.splitQuery(uri);
				if (MapUtils.isNotEmpty(params)) {
					String postId = "";
					String profileId = "";
					List<String> storyFbids = params.get("story_fbid");
					if (storyFbids != null && !storyFbids.isEmpty()) {
						postId = storyFbids.get(0);
					}
					List<String> profileIds = params.get("id");
					if (profileIds != null && !profileIds.isEmpty()) {
						profileId = profileIds.get(0);
					}
					if (StringUtils.isNotEmpty(profileId) && StringUtils.isNotEmpty(postId)) {
						result.add(new ObjectRequest(SocialType.FACEBOOK, profileId, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
						String compositePostId = String.format(FORMAT_COMPOSITE_POST_ID, profileId, postId);
						result.add(new ObjectRequest(SocialType.FACEBOOK, compositePostId, ObjectType.POST, DEFAULT_LOOP_TIME_POST));
					}
				}
			} else if (path.matches("^/[0-9a-zA-Z.]+/photos/[0-9a-zA-Z.]+/[0-9]+/?$")) {
				// case post is photo with URL https://vi-vn.facebook.com/tran.thanh.ne/photos/a.682505288445462.1073741829.488035214559138/1086281714734482/
				String[] parts = StringUtils.split(path, "/");
				if (parts.length == 4) {
					String profileUsername = parts[0];
					int lastDot = StringUtils.lastIndexOf(parts[2], ".");
					String profileId = StringUtils.substring(parts[2], lastDot + 1);
					String postId = parts[3];

					if (StringUtils.isNotEmpty(profileId) && StringUtils.isNotEmpty(postId)) {

						try {
							if (!StringUtils.equalsIgnoreCase(profileUsername, profileId)) {
								RunUpdateNews.mappingUsername2IdRepositpory.write(profileUsername.getBytes(), profileId.getBytes());
							}
						} catch (Exception ex) {
							LOG.error(ex.getMessage(), ex);
						}

						result.add(new ObjectRequest(SocialType.FACEBOOK, profileId, ObjectType.PAGE, DEFAULT_LOOP_TIME_PROFILE));
						String compositePostId = String.format(FORMAT_COMPOSITE_POST_ID, profileId, postId);
						result.add(new ObjectRequest(SocialType.FACEBOOK, compositePostId, ObjectType.POST, DEFAULT_LOOP_TIME_POST));
					}
				}
			}
		}
		return result;
	}
}
