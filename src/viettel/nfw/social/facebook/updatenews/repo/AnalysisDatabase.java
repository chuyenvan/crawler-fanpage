package viettel.nfw.social.facebook.updatenews.repo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.utils.FileUtils;

/**
 *
 * @author duongth5
 */
public class AnalysisDatabase {

	private static final Logger LOG = LoggerFactory.getLogger(AnalysisDatabase.class);

	private static final String DIR_FAILED_OBJ = "database/failedobject/";

	public static void main(String[] args) throws FileNotFoundException, IOException {
//		readFailedObject();
//		readObjectRequest();
//		phanbiet();
		analysis();
	}

	private static void analysis() throws IOException {
		List<String> rows = FileUtils.readList(new File("allfailed.txt"));
		System.out.println("rows " + rows.size());

		Map<String, List<String>> idToMessages = new HashMap<>();
		for (String row : rows) {
			String[] parts = StringUtils.split(row, "\t");
			int length = parts.length;
			if (length == 2) {
				String id = parts[0];
				String message = parts[1];
				if (idToMessages.containsKey(id)) {
					List<String> messages = idToMessages.get(id);
					messages.add(message);
					idToMessages.put(id, messages);
				} else {
					List<String> messages = new ArrayList<>();
					messages.add(message);
					idToMessages.put(id, messages);
				}
			}
		}
		System.out.println("ids " + idToMessages.size());

		int countE1 = 0;
		int countL1 = 0;
		int countG1 = 0;

		Set<String> otherMessages = new HashSet<>();
		Map<String, String> oldIdToNewId = new HashMap<>();

		Set<String> notExists = new HashSet<>();
		Set<String> users = new HashSet<>();
		Set<String> maybePages = new HashSet<>();

		Map<String, String> otherIdToMessages = new HashMap();

		for (Map.Entry<String, List<String>> entrySet : idToMessages.entrySet()) {
			String id = entrySet.getKey();
			List<String> messages = entrySet.getValue();
			if (messages.size() == 1) {
				countE1++;
				if (StringUtils.startsWithIgnoreCase(messages.get(0), "Received Facebook error response of type OAuthException:")) {
					if (StringUtils.contains(messages.get(0), "was migrated to page ID")) {
						String regex = "^(.*)(Page ID )([0-9]{9,})( was migrated to page ID )([0-9]{9,})(.*)$";
						Matcher m = Pattern.compile(regex).matcher(messages.get(0));
						if (m.matches()) {
							String oldID = m.group(3);
							String newID = m.group(4);
							oldIdToNewId.put(oldID, newID);
						}
					} else if (StringUtils.contains(messages.get(0), "Some of the aliases you requested do not exist")) {
						notExists.add(id);
					} else if (StringUtils.contains(messages.get(0), "Cannot query users by their username")) {
						users.add(id);
					} else {
						otherIdToMessages.put(id, messages.get(0));
//						otherMessages.add(messages.get(0));
					}
				} else if (StringUtils.startsWithIgnoreCase(messages.get(0), "Received Facebook error response of type GraphMethodException:")) {
					if (StringUtils.contains(messages.get(0), "Unknown identifier,")) {
						maybePages.add(id);
					} else {
						otherMessages.add(messages.get(0));
					}
				} else {
//					System.out.println(id);
//					otherMessages.add(messages.get(0));
				}
			} else if (messages.size() < 1) {
				countL1++;
			} else {
				countG1++;
			}
		}
		System.out.println("count=1: " + countE1 + " - count<1: " + countL1 + " - count>1: " + countG1);
		System.out.println("oldIdToNewId " + oldIdToNewId.size());
		FileUtils.write(new File("otherMessages.txt"), otherMessages);
		FileUtils.write(new File("notExists.txt"), notExists);
		FileUtils.write(new File("users.txt"), users);
	}

	private static void phanbiet() throws IOException {
		Set<String> keys = new HashSet<>();
		keys.addAll(FileUtils.readList(new File("allobjrequest.txt")));

		Set<String> pages = new HashSet<>();
		Set<String> groups = new HashSet<>();
		Set<String> posts = new HashSet<>();

		for (String key : keys) {
			String[] parts = StringUtils.split(key, "#");
			int length = parts.length;
			if (length == 3) {
				String id = parts[1];
				String type = parts[2];
				if (StringUtils.equalsIgnoreCase(type, "POST")) {
					posts.add(id);
				} else if (StringUtils.equalsIgnoreCase(type, "PAGE")) {
					pages.add(id);
				} else if (StringUtils.equalsIgnoreCase(type, "GROUP")) {
					groups.add(id);
				}
			}
		}

		FileUtils.write(new File("groups.txt"), groups);
		FileUtils.write(new File("pages.txt"), pages);
		FileUtils.write(new File("posts.txt"), posts);
	}

	private static void readFailedObject() throws FileNotFoundException {
		Set<String> messages = new HashSet<>();
		try {
			File folder = new File(DIR_FAILED_OBJ);
			File[] listOfFiles = folder.listFiles();
			for (File file : listOfFiles) {
				if (file.isFile()) {
					// LOG.info("File {}", file.getAbsolutePath());
					try (BufferedReader br = new BufferedReader(new FileReader(file))) {
						String line;
						while ((line = br.readLine()) != null) {
							line = line.trim();
							if (StringUtils.isEmpty(line)
								|| StringUtils.startsWith(line, "#")) {
								continue;
							}
							String[] parts = StringUtils.split(line, "\t");
							int length = parts.length;
							if (length >= 3) {
								String socialTypeStr = parts[0];
								String objectIdStr = parts[1];
								String objectTypeStr = parts[2];
								String message = parts[3];

								messages.add(objectIdStr + "\t" + message);
							} else {
								LOG.warn("This line has error format: {} in file {}", line, file.getAbsolutePath());
							}
						}
					}
				} else if (file.isDirectory()) {
					LOG.warn("Directory {}", file.getAbsolutePath());
				}
			}
		} catch (Exception ex) {
			LOG.error(ex.getMessage(), ex);
		}

		FileUtils.write(new File("allfailed.txt"), messages);
	}

	private static void readObjectRequest() throws FileNotFoundException {
		ObjectRequestRepository objRequestRepository = ObjectRequestRepository.getInstance();
		Set<String> keys = new HashSet<>();
		keys.addAll(objRequestRepository.getAllKeys());

		FileUtils.write(new File("allobjectrequest.txt"), keys);
	}
}
