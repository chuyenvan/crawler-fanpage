package viettel.nfw.social.utils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.hadoop.conf.Configuration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Duong
 */
public class EngineConfiguration extends Configuration {

	private static final Logger LOG = LoggerFactory.getLogger(EngineConfiguration.class);

	// singleton instance
	private static EngineConfiguration INSTANCE = null;

	// property to deploy specified jars when running MR job cluster
	private static final String TMPJARS_NAME = "tmpjars";

	private EngineConfiguration() {
		super();
		addEngineResources();
		setPropertyTmpjars();
	}

	private void addEngineResources() {
		addResource("engine-default.xml");
	}

	/**
	 * Sets tmpjar property according to CLASSPATH elements.
	 */
	private void setPropertyTmpjars() {
		// get CLASSPATH system variable
		String classpath = System.getenv("CLASSPATH");
		if (classpath == null) {
			LOG.error("CLASSPATH is not set");
			return;
		}

		StringBuilder tmpjars = new StringBuilder();

		String[] elements = classpath.split(":");
		// process each element of classpath
		for (String element : elements) {
			if (element == null || element.length() == 0) {
				continue;
			}
			// don't process non-jars
			if (!element.endsWith(".jar")) {
				continue;
			}
			// don't process nonexistent jars
			if (!new File(element).exists()) {
				continue;
			}

			// all checks are passed; add element to tmpjar
			if (tmpjars.length() != 0) {
				tmpjars.append(',');
			}
			tmpjars.append("file://").append(element);
		}

		// update hadoop conf with tmpjars
		this.set(TMPJARS_NAME, tmpjars.toString());
		LOG.trace("{}={}", TMPJARS_NAME, tmpjars);
	}

	/**
	 * Returns cached configuration.
	 *
	 * @return
	 */
	public static EngineConfiguration get() {
		if (INSTANCE == null) {
			INSTANCE = new EngineConfiguration();
			LOG.info("EngineConfiguration loaded.");
		}
		return INSTANCE;
	}

	/**
	 * get keys matching the the regex
	 *
	 * @param regex
	 * @return
	 */
	public Map<String, String> getValByRegex(String regex) {
		Pattern p = Pattern.compile(regex);

		Map<String, String> result = new HashMap<>();

		for (Map.Entry<String, String> item : this) {
			if (item.getKey() != null && item.getValue() != null) {
				Matcher m = p.matcher(item.getKey());
				if (m.find()) { // match
					result.put(item.getKey(), item.getValue());
				}
			}
		}
		return result;
	}

	public int getIntOrFail(@NotNull String key) {
		String value = get(key);
		if (value == null) {
			throw new RuntimeException(key + " isn't configured");
		} else {
			return Integer.valueOf(value);
		}
	}

	public String getStringOrFail(@NotNull String key) {
		String value = get(key);
		if (value == null) {
			throw new RuntimeException(key + " isn't configured");
		} else {
			return value;
		}
	}
}
