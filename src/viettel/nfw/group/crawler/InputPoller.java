/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viettel.nfw.group.crawler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.RootLogger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 *
 * @author hoangvv
 */
public class InputPoller {

	private static final Logger logger = RootLogger.getLogger(InputPoller.class);

	public static Collection<String> loadDataPage() {
		logger.info("loadData Page to Queue");
		Collection pageIds = new ArrayList();
		try {
			String response = MasterService.getPage("500"); //get 1000 page from master service
			if (response != null) {
				JSONParser json = new JSONParser();
				JSONArray array = (JSONArray) json.parse(response);
				if (array != null && !array.isEmpty()) {
					for (Object obj : array) {
						JSONObject object = (JSONObject) obj;
						String ID = (String) object.get("page_id");
						if (ID != null) {
							boolean isDone = pageIds.add(ID);
							if (!isDone) {
								logger.info(ID + " :is failed");
							}
						} else {
							logger.warn("Page_id is null");
						}
					}
				}
//			logger.info("Size of queue: " + queueGroup.size());
			} else {
				logger.error("Master Service is failed");
			}
		} catch (IOException | ParseException e) {
			logger.error(e.getMessage(), e);
		}
		return pageIds;
	}

//	public BlockingQueue testGroup() throws FileNotFoundException, IOException {
//		File file = new File("testGroup.txt");
//		BufferedReader br = null;
//		br = new BufferedReader(new FileReader(file));
//		String line = "";
//		
//		while ((line = br.readLine()) != null) {
//			final String ID = line.trim();
//			if (!line.isEmpty()) {
//				queuePage.add(ID);
//				logger.info(ID + " add to queue");
//			}
//		}
//		logger.info("Size of queue: " + queuePage.size());
//		return queuePage;
//	}
	public static Collection<String> loadDataGroup() {
		logger.info("loadData Group to Queue");
		Collection<String> groupIds = new LinkedBlockingQueue();
		try {
			String response = MasterService.getGroup("500"); //get 100 page from master service
			if (response != null) {
				JSONParser json = new JSONParser();
				JSONArray array = (JSONArray) json.parse(response);
				if (array != null && !array.isEmpty()) {
					for (Object obj : array) {
						JSONObject object = (JSONObject) obj;
						String ID = (String) object.get("group_id");
						if (ID != null) {
							boolean isDone = groupIds.add(ID);
							if (!isDone) {
								logger.info(ID + " :is failed");
							}
						} else {
							logger.warn("Group id is null");
						}
					}
				}
//			logger.info("Size of queue: " + queueGroup.size());
			} else {
				logger.error("Master Service is failed");
			}
		} catch (IOException | ParseException e) {
			logger.error(e.getMessage(), e);
		}
		return groupIds;
	}

//	public BlockingQueue testPage() throws FileNotFoundException, IOException {
//		File file = new File("testPage.txt");
//		BufferedReader br = null;
//		br = new BufferedReader(new FileReader(file));
//		String line = "";
//		while ((line = br.readLine()) != null) {
//			final String ID = line.trim();
//			if (!line.isEmpty()) {
//				queuePage.add(ID);
//				logger.info(ID + " add to queue");
//			}
//		}
//		logger.info("Size of queue: " + queuePage.size());
//		return queuePage;
//	}
}
