/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viettel.nfw.group.crawler;

import viettel.nfw.page.crawler.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.RootLogger;
import viettel.nfw.social.utils.EngineConfiguration;

/**
 *
 * @author hoangvv
 */
public class MasterService {

	private static final Logger logger = RootLogger.getLogger(MasterService.class);
	private static final String USER_AGENT = "Mozilla/5.0";
	private static final String IP_MASTER_SERVICE = EngineConfiguration.get().get("masterservice.ipconfig", "192.168.101.9");
	private static final String PORT_MASTER_SERVICE = EngineConfiguration.get().get("masterservice.portconfig", "8010");
	private static final String urlPage = "http://" + IP_MASTER_SERVICE + ":" + PORT_MASTER_SERVICE + "/api/?f=get_pages&number=";
	private static final String urlGroup = "http://" + IP_MASTER_SERVICE + ":" + PORT_MASTER_SERVICE + "/api/?f=get_groups&number=";
	private static final String removePage = "http://" + IP_MASTER_SERVICE + ":" + PORT_MASTER_SERVICE + "/api/?f=remove_page&page_id=";
	private static final String removeGroup = "http://" + IP_MASTER_SERVICE + ":" + PORT_MASTER_SERVICE + "/api/?f=remove_group&group_id=";

	public static String getPage(String number) throws MalformedURLException, IOException {
		try {
			URL obj = new URL(urlPage + number);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", USER_AGENT);
			StringBuilder response;
			try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
				String inputLine;
				response = new StringBuilder();
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
			}
			String text = response.toString();
			return text;
		} catch (IOException ex) {
			logger.error(ex.getMessage(), ex);
		}
		return null;
	}

	public static boolean removePage(String ID) throws MalformedURLException, IOException {
		URL obj = new URL(removePage + ID);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("User-Agent", USER_AGENT);
		StringBuilder response;
		try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
			String inputLine;
			response = new StringBuilder();
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
		}
		String text = response.toString();
		return text.contains("OK");
	}

	public static String getGroup(String number) throws MalformedURLException, IOException {
		try {
			URL obj = new URL(urlGroup + number);
			HttpURLConnection con = (HttpURLConnection) obj.openConnection();
			con.setRequestMethod("GET");
			con.setRequestProperty("User-Agent", USER_AGENT);
			StringBuilder response;
			try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
				String inputLine;
				response = new StringBuilder();
				while ((inputLine = in.readLine()) != null) {
					response.append(inputLine);
				}
			}
			String text = response.toString();
			return text;

		} catch (IOException ex) {
			logger.error(ex.getMessage(), ex);
		}
		return null;
	}

	public static boolean removeGroup(String ID) throws MalformedURLException, IOException {
		URL obj = new URL(removePage + ID);
		HttpURLConnection con = (HttpURLConnection) obj.openConnection();
		con.setRequestMethod("GET");
		con.setRequestProperty("User-Agent", USER_AGENT);
		StringBuilder response;
		try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
			String inputLine;
			response = new StringBuilder();
			while ((inputLine = in.readLine()) != null) {
				response.append(inputLine);
			}
		}
		String text = response.toString();
		return text.contains("OK");
	}

	public static void addSpecialPage(String ID) {

	}

	public static void removeSpecialPage(String ID) {

	}

	public static void addSpecialGroup(String ID) {

	}

	public static void removeSpecialGroup(String ID) {

	}

	public static void main(String[] args) throws IOException {
		System.out.println(urlPage + "1000");
	}

}
