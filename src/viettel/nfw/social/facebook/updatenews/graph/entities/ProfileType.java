package viettel.nfw.social.facebook.updatenews.graph.entities;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public enum ProfileType {

	PAGE_AUTOGEN(1, "page_autogen"),
	PAGE_REAL(2, "page_real"),
	GROUP_PUBLIC(3, "group_public"),
	GROUP_PRIVATE(4, "group_private"),
	UNKNOWN(5, "unknown"),
	USER(6, "user");

	private static final Map<String, ProfileType> lookupByShortName = new HashMap<>();
	private static final Int2ObjectMap<ProfileType> lookupById = new Int2ObjectOpenHashMap<>();

	static {
		for (ProfileType l : ProfileType.values()) {
			ProfileType old = lookupByShortName.put(l.getShortName(), l);
			if (old != null) {
				throw new RuntimeException("Duplicate profile type short name: " + l.getShortName());
			}

			old = lookupById.put(l.getId(), l);
			if (old != null) {
				throw new RuntimeException("Duplicate profile type id: " + l.getId());
			}
		}
	}

	public static ProfileType getById(int id) {
		ProfileType result = lookupById.get(id);
		if (result == null) {
			throw new RuntimeException("Unknown profile type id: " + id);
		}

		return result;
	}

	public static ProfileType getByShortName(String name) {
		ProfileType result = (name != null ? lookupByShortName.get(name) : null);
		if (result == null) {
			//throw new RuntimeException(+name);
			LOG.warn("Unknown language name: {}.Please check", name);
			return ProfileType.UNKNOWN;
		}

		return result;
	}

	private static final Logger LOG = LoggerFactory.getLogger(ProfileType.class);
	private final int id;
	private final String shortName;

	private ProfileType(int id, String description) {
		this.id = id;
		this.shortName = description;
	}

	public int getId() {
		return id;
	}

	public String getShortName() {
		return shortName;
	}

	@Override
	public String toString() {
		return "ProfileType{" + "id=" + id + ", shortName=" + shortName + '}';
	}
}
