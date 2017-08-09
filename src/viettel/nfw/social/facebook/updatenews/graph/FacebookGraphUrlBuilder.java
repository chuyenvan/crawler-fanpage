package viettel.nfw.social.facebook.updatenews.graph;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.Pair;

/**
 *
 * @author duongth5
 */
public class FacebookGraphUrlBuilder {

	private static final Logger LOG = LoggerFactory.getLogger(FacebookGraphUrlBuilder.class);
	private static final String GRAPH_URL = "https://graph.facebook.com/";
	private static final String GRAPH_URL_WITH_VERSION = "https://graph.facebook.com/%s/";

	private static String builder(String apiVersion, String path, Map<String, String> params) {
		StringBuilder sb = new StringBuilder();
		if (StringUtils.isEmpty(apiVersion)) {
			sb.append(GRAPH_URL);
		} else {
			sb.append(String.format(GRAPH_URL_WITH_VERSION, apiVersion));
		}
		if (StringUtils.isEmpty(path)) {
			return null;
		}
		sb.append(path);
		if (params != null) {
			List<String> keyValues = new ArrayList<>();
			for (Map.Entry<String, String> entry : params.entrySet()) {
				StringBuilder sbKV = new StringBuilder();
				String key = entry.getKey();
				String value = entry.getValue();
				try {
					sbKV.append(URLEncoder.encode(key, "UTF-8"));
					sbKV.append("=");
					sbKV.append(URLEncoder.encode(value, "UTF-8"));
					keyValues.add(sbKV.toString());
				} catch (UnsupportedEncodingException ex) {
					LOG.error("Error while encoding key " + key + " value " + value, ex);
				}
			}
			boolean isFirst = true;
			for (String keyValue : keyValues) {
				if (isFirst) {
					sb.append("?");
					isFirst = false;
				} else {
					sb.append("&");
				}
				sb.append(keyValue);
			}
		}

		String finalUrl = sb.toString();
		LOG.debug("BUILDED_URL {}", finalUrl);
		return finalUrl;
	}

	public static String buildPostInfoUrl(String apiVersion, String postId, String accessToken) {
		if (StringUtils.isEmpty(postId) || StringUtils.isEmpty(accessToken)) {
			return null;
		}
		String path = String.format("%s", postId);
		Map<String, String> params = new HashMap<>();
		params.put("fields", "id,from,to,status_type,message,description,link,likes.limit(10),comments.limit(10),created_time,updated_time,shares,type,caption");
		params.put("access_token", accessToken);

		return builder(apiVersion, path, params);
	}

	public static String buildGroupInfoUrl(String apiVersion, String groupId, String accessToken) {
		if (StringUtils.isEmpty(groupId) || StringUtils.isEmpty(accessToken)) {
			return null;
		}
		String path = String.format("%s", groupId);
		Map<String, String> params = new HashMap<>();
		params.put("fields", "id,name,email,privacy");
		params.put("access_token", accessToken);

		return builder(apiVersion, path, params);
	}

	public static String buildCountLikesCommentOfPostUrl(String apiVersion, String postId, String accessToken) {
		if (StringUtils.isEmpty(postId) || StringUtils.isEmpty(accessToken)) {
			return null;
		}
		String path = String.format("%s", postId);
		Map<String, String> params = new HashMap<>();
		params.put("fields", "likes.summary(true),comments.summary(true)");
		params.put("access_token", accessToken);

		return builder(apiVersion, path, params);
	}

	public static String buildListFeedUrl(String apiVersion, String profileId, String accessToken) {
		if (StringUtils.isEmpty(profileId) || StringUtils.isEmpty(accessToken)) {
			return null;
		}
		String path = String.format("%s/feed", profileId);
		Map<String, String> params = new HashMap<>();
		params.put("fields", "id,created_time");
		params.put("limit", "5");
		params.put("access_token", accessToken);
		return builder(apiVersion, path, params);
	}

	public static String buildListLikesOfPostUrl(String apiVersion, String postId, String limit, String accessToken) {
		if (StringUtils.isEmpty(postId) || StringUtils.isEmpty(accessToken)) {
			return null;
		}
		String path = String.format("%s/likes", postId);
		Map<String, String> params = new HashMap<>();
		params.put("fields", "id,name");
		params.put("limit", limit);
		params.put("access_token", accessToken);
		return builder(apiVersion, path, params);
	}
        public static String buildListReactionsOfPostUrl(String apiVersion, String postId, String limit, String accessToken) {
		if (StringUtils.isEmpty(postId) || StringUtils.isEmpty(accessToken)) {
			return null;
		}
		String path = String.format("%s/reactions", postId);
		Map<String, String> params = new HashMap<>();
		params.put("fields", "id,name,type");
		params.put("limit", limit);
		params.put("access_token", accessToken);
		return builder(apiVersion, path, params);
	}

	public static void main(String[] args) {
		Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("203.113.152.15", 3456));
		String postId = "35182380619_10154546102315620";
		String profileId = "232917180138659";
		String accessToken = "CAACEdEose0cBAIk75cM44ZCnGDVZAlh8VOnjamobwkKt9isnRa7cVHshplnuI5z3PZAp1A2ZAudvFYBcKhrZAV1uRcrQdUVcCj8qMGHqZALOzyaOuTx3DNIdC5KIRuUM9WpGu0WDK1VUr9hYlrV2PXli2yKRPckxHnnyZA90nDK5kRWFFLoXdwmwZBAA0u9kc6tAIFh9mjuZCrainS0TEm3rL";

//		Pair<viettel.nfw.social.model.facebook.Post, Integer> result = FacebookGraphActions.getSinglePostInfo(postId, ObjectType.POST, accessToken, proxy);
//		System.out.println(result.first);
//		System.out.println(result.second);
		Pair<Pair<Set<String>, Set<String>>, Integer> data = FacebookGraphActions.getPosts(profileId, null, accessToken, proxy);
		Pair<Set<String>, Set<String>> postIds = data.first;
		int counter = data.second;
		Set<String> newPosts = postIds.first;
		Set<String> oldPosts = postIds.second;
		System.out.println(newPosts.size());
		System.out.println(oldPosts.size());
		System.out.println(counter);
	}
}
