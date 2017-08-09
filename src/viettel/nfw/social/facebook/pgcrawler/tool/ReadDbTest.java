package viettel.nfw.social.facebook.pgcrawler.tool;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.graph.entities.ProfileType;
import viettel.nfw.social.utils.FileUtils;
import viettel.nfw.social.utils.Pair;
import vn.itim.detector.Language;

/**
 *
 * @author Duong
 */
public class ReadDbTest {

	private static final Logger LOG = LoggerFactory.getLogger(ReadDbTest.class);

	public static void main(String[] args) throws IOException {

		// read from list quick check
		Pair<Map<String, String>, Map<String, DumpDataQuickCheck>> data = readQuickCheckFile();
		Map<String, String> failedIdToMessages = data.first;
		Map<String, DumpDataQuickCheck> idToDumpDataQC = data.second;
		System.out.println(failedIdToMessages.size());
		System.out.println(idToDumpDataQC.size());

		// read from list focus
		List<DumpData> dumpDatas = readDataFile();
		System.out.println(dumpDatas.size());

		List<DumpData> pages = new ArrayList<>();
		List<DumpData> groups = new ArrayList<>();
		List<DumpData> users = new ArrayList<>();
		for (DumpData dumpData : dumpDatas) {
			if (dumpData.profileType.equals(ProfileType.PAGE_REAL)) {
				DumpData x = new DumpData();
				DumpDataQuickCheck qc = idToDumpDataQC.get(dumpData.profileID);
				if (qc != null) {
					String profileID;
					if (qc.profileID.equalsIgnoreCase(qc.newProfileID)) {
						profileID = qc.profileID;
					} else {
						profileID = qc.newProfileID;
					}
					x.profileID = profileID;
					x.username = dumpData.username;
					x.fullname = qc.fullname;
					x.url = qc.url;
					x.likesOrMembers = qc.likesOrMembers;
					x.language = dumpData.language;
					x.profileType = dumpData.profileType;
				} else {
					x = dumpData;
				}
				pages.add(x);
			} else if (dumpData.profileType.equals(ProfileType.GROUP_PUBLIC)) {
				DumpData x = new DumpData();
				DumpDataQuickCheck qc = idToDumpDataQC.get(dumpData.profileID);
				if (qc != null) {
					String profileID;
					if (qc.profileID.equalsIgnoreCase(qc.newProfileID)) {
						profileID = qc.profileID;
					} else {
						profileID = qc.newProfileID;
					}
					x.profileID = profileID;
					x.username = dumpData.username;
					x.fullname = qc.fullname;
					x.url = qc.url;
					x.likesOrMembers = qc.likesOrMembers;
					x.language = dumpData.language;
					x.profileType = dumpData.profileType;
				} else {
					x = dumpData;
				}
				groups.add(x);
			} else if (dumpData.profileType.equals(ProfileType.USER)) {
				users.add(dumpData);
			}
		}

		List<String> pageRows = new ArrayList<>();
		Set<String> pagesIds = new HashSet<>();
		for (DumpData r : pages) {
			if (!pagesIds.contains(r.profileID)) {
				String row = r.profileID
						+ "\t" + (StringUtils.isEmpty(r.username) ? "null" : r.username)
						+ "\t" + (StringUtils.isEmpty(r.fullname) ? "null" : r.fullname)
						+ "\t" + (StringUtils.isEmpty(r.url) ? "null" : r.url)
						+ "\t" + (r.likesOrMembers == -1 ? "0" : String.valueOf(r.likesOrMembers))
						+ "\t" + r.language.getShortName()
						+ "\t" + r.profileType.getShortName();
				pageRows.add(row);
				pagesIds.add(r.profileID);
			}

		}
		FileUtils.write(new File("input_data_pages" + String.valueOf(System.currentTimeMillis()) + ".txt"), pageRows);

		List<String> groupRows = new ArrayList<>();
		Set<String> groupIds = new HashSet<>();
		for (DumpData r : groups) {
			if (!groupIds.contains(r.profileID)) {
				String row = r.profileID
						+ "\t" + (StringUtils.isEmpty(r.username) ? "null" : r.username)
						+ "\t" + (StringUtils.isEmpty(r.fullname) ? "null" : r.fullname)
						+ "\t" + (StringUtils.isEmpty(r.url) ? "null" : r.url)
						+ "\t" + (r.likesOrMembers == -1 ? "0" : String.valueOf(r.likesOrMembers))
						+ "\t" + r.language.getShortName()
						+ "\t" + r.profileType.getShortName();
				groupRows.add(row);
				groupIds.add(r.profileID);
			}
		}
		FileUtils.write(new File("input_data_groups" + String.valueOf(System.currentTimeMillis()) + ".txt"), groupRows);

		List<String> userRows = new ArrayList<>();
		Set<String> userIds = new HashSet<>();
		for (DumpData r : users) {
			if (!userIds.contains(r.profileID)) {
				String row = r.profileID
						+ "\t" + (StringUtils.isEmpty(r.username) ? "null" : r.username)
						+ "\t" + (StringUtils.isEmpty(r.fullname) ? "null" : r.fullname)
						+ "\t" + (StringUtils.isEmpty(r.url) ? "null" : r.url)
						+ "\t" + (r.likesOrMembers == -1 ? "0" : String.valueOf(r.likesOrMembers))
						+ "\t" + r.language.getShortName()
						+ "\t" + r.profileType.getShortName();
				userRows.add(row);
				userIds.add(r.profileID);
			}
		}
		FileUtils.write(new File("input_data_users" + String.valueOf(System.currentTimeMillis()) + ".txt"), userRows);
	}

	private static List<DumpData> readDataFile() throws IOException {
		String filename = "D:\\git\\pagesgroups_2.data";
		List<DumpData> dumpDatas = new ArrayList<>();
		List<String> rows = FileUtils.readList(new File(filename));
		for (String row : rows) {
			if (!StringUtils.startsWith(row, "#")) {
				String[] parts = row.split("\t");
				DumpData dumpData = new DumpData();
				String profileId = parts[0];
				if (StringUtils.isEmpty(profileId)) {
					continue;
				} else {
					dumpData.profileID = profileId;
				}
				String username = parts[1];
				if (StringUtils.isEmpty(username) || StringUtils.equalsIgnoreCase(username, "null")) {
					dumpData.username = "";
				} else {
					dumpData.username = username;
				}
				String fullname = parts[2];
				if (StringUtils.isEmpty(fullname) || StringUtils.equalsIgnoreCase(fullname, "null")) {
					dumpData.fullname = "";
				} else {
					dumpData.fullname = fullname;
				}
				String url = parts[3];
				if (StringUtils.isEmpty(url) || StringUtils.equalsIgnoreCase(url, "null")) {
					dumpData.url = "";
				} else {
					dumpData.url = url;
				}
				String likesOrMembersStr = parts[4];
				if (StringUtils.isEmpty(likesOrMembersStr) || StringUtils.equalsIgnoreCase(likesOrMembersStr, "null")) {
					dumpData.likesOrMembers = -1;
				} else {
					dumpData.likesOrMembers = Long.parseLong(likesOrMembersStr);
				}
				String languageStr = parts[5];
				if (StringUtils.isEmpty(languageStr) || StringUtils.equalsIgnoreCase(languageStr, "null")) {
					dumpData.language = Language.UNKNOWN;
				} else {
					dumpData.language = Language.getByShortName(languageStr);
				}
				String profileTypeStr = parts[6];
				if (StringUtils.isEmpty(profileTypeStr) || StringUtils.equalsIgnoreCase(profileTypeStr, "null")) {
					dumpData.profileType = ProfileType.UNKNOWN;
				} else {
					dumpData.profileType = ProfileType.getByShortName(profileTypeStr);
				}
				dumpDatas.add(dumpData);
			}
		}
		return dumpDatas;
	}

	private static class DumpData {

		public String profileID;
		public String username;
		public String fullname;
		public String url;
		public long likesOrMembers;
		public Language language;
		public ProfileType profileType;
	}

	private static Pair<Map<String, String>, Map<String, DumpDataQuickCheck>> readQuickCheckFile() throws IOException {
		List<String> filenames = new ArrayList<>();
		filenames.add("D:\\git\\social-actions\\ids.txt.result.txt");
//		filenames.add("D:\\git\\resultqc\\pageqc2.txt");
//		filenames.add("D:\\git\\resultqc\\pageqc3.txt");
//		filenames.add("D:\\git\\resultqc\\pageqc4.txt");
//		filenames.add("D:\\git\\resultqc\\pageqc5.txt");

		Map<String, String> failedIdToMessages = new HashMap<>();
		Map<String, DumpDataQuickCheck> idToDumpDataQC = new HashMap<>();

		for (String filename : filenames) {
			List<String> rows = FileUtils.readList(new File(filename));
			LOG.info("Readed {}", filename);
			for (String row : rows) {
				if (!StringUtils.startsWith(row, "#")) {
					String[] parts = row.split("\t");
					int length = parts.length;
					if (length == 2) {
						failedIdToMessages.put(parts[0], parts[1]);
					} else if (length == 8) {
						DumpDataQuickCheck qcData = new DumpDataQuickCheck();
						String profileID = parts[0];
						if (StringUtils.isEmpty(profileID) || StringUtils.equalsIgnoreCase(profileID, "null")) {
							continue;
						} else {
							qcData.profileID = profileID;
						}
						String fullname = parts[1];
						if (StringUtils.isEmpty(fullname) || StringUtils.equalsIgnoreCase(fullname, "null")) {
							qcData.fullname = "";
						} else {
							qcData.fullname = fullname;
						}
						String url = parts[2];
						if (StringUtils.isEmpty(url) || StringUtils.equalsIgnoreCase(url, "null")) {
							qcData.url = url;
						} else {
							qcData.url = url;
						}
						String profileTypeStr = parts[3];
						if (StringUtils.isEmpty(profileTypeStr) || StringUtils.equalsIgnoreCase(profileTypeStr, "null")) {
							qcData.profileType = ProfileType.UNKNOWN;
						} else {
							if (StringUtils.equalsIgnoreCase(profileTypeStr, "PAGE")) {
								qcData.profileType = ProfileType.PAGE_REAL;
								if (StringUtils.endsWithIgnoreCase(qcData.fullname, " | Facebook")) {
									qcData.profileType = ProfileType.PAGE_AUTOGEN;
								}
							} else if (StringUtils.equalsIgnoreCase(profileTypeStr, "PAGE_AUTOGEN")) {
								qcData.profileType = ProfileType.PAGE_AUTOGEN;
							} else {
								qcData.profileType = ProfileType.UNKNOWN;
							}
						}
						String likesOrMembersStr = parts[4];
						if (StringUtils.isEmpty(likesOrMembersStr) || StringUtils.equalsIgnoreCase(likesOrMembersStr, "null")) {
							qcData.likesOrMembers = -1;
						} else {
							String numberOnly = likesOrMembersStr.replaceAll("\\D+", "");
							qcData.likesOrMembers = Long.parseLong(numberOnly);
						}
						String newProfileID = parts[5];
						if (StringUtils.isEmpty(newProfileID) || StringUtils.equalsIgnoreCase(newProfileID, "null")) {
							qcData.newProfileID = "";
						} else {
							qcData.newProfileID = newProfileID;
						}
						String titleLanguageStr = parts[6];
						if (StringUtils.isEmpty(titleLanguageStr) || StringUtils.equalsIgnoreCase(titleLanguageStr, "null")) {
							qcData.titleLanguage = Language.UNKNOWN;
						} else {
							qcData.titleLanguage = Language.getByShortName(titleLanguageStr);
						}
						String contentLanguageStr = parts[7];
						if (StringUtils.isEmpty(contentLanguageStr) || StringUtils.equalsIgnoreCase(contentLanguageStr, "null")) {
							qcData.contentLanguage = Language.UNKNOWN;
						} else {
							qcData.contentLanguage = Language.getByShortName(contentLanguageStr);
						}

						idToDumpDataQC.put(profileID, qcData);
					} else {
						System.out.println(row);
					}
				}
			}
		}

		return new Pair<>(failedIdToMessages, idToDumpDataQC);
	}

	private static class DumpDataQuickCheck {

		public String profileID;
		public String fullname;
		public String url;
		public ProfileType profileType;
		public long likesOrMembers;
		public String newProfileID;
		public Language titleLanguage;
		public Language contentLanguage;

	}

}
