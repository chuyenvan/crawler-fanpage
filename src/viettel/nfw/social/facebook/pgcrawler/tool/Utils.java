/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package viettel.nfw.social.facebook.pgcrawler.tool;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.automanacc.object.ObjectPost;

/**
 *
 * @author chuyennd2
 */
public class Utils {

	private static final Logger LOG = LoggerFactory.getLogger(Utils.class);
	private static final String WEB_FACE = "https://www.facebook.com";
	private static final String WEB_MFACE = "https://m.facebook.com";
	private static final String NEWS_FEED = "News Feed";
	private static final String T_IDFULLNAME = "oauth_signup_client_fullname";
	private static final String T_IDMAIL = "oauth_signup_client_phone_number";
	private static final String T_IDUSERNAME = "oauth_signup_client_screen_name";
	private static final String T_IDPASSWORD = "oauth_signup_client_password";
	private static final String T_PHONE_NUMBER = "phone_number";
	private static final String T_COMMIT = "commit";
	private static final String T_PIN = "pin";
	private static final String T_REPASSWORD = "password";
	private static final String NEW_LINE = "\n";
	private static final String GMAIL = "@gmail.com";
	private static final String YMAIL = "@yahoo.com";
	private static final String WEB_GMAIL = "https://gmail.com/";
	private static final String WEB_YMAIL = "https://mail.yahoo.com/";
	private static final String WEB_HOTMAIL = "https://mail.live.com/";
	private static final String PASS1 = "123456ab@!";
	private static final String PASS2 = "Abcd#@2015";
	private static final ArrayList<String> listUserName = new ArrayList<>();
	private static Map<String, List<ObjectPost>> listDataUsePost = new HashMap<>();

	public static WebElement getElement(RemoteWebDriver driver, By... bys) {
		for (By by : bys) {
			try {
				WebElement we = (driver.findElement(by));
				if (we != null & we.isDisplayed()) {
					return we;
				}
			} catch (NoSuchElementException ex) {
//				ex.printStackTrace();
			}
		}
		return null;
	}
	
	public static void main(String[] args) {
		System.out.println(URLDecoder.decode("https://www.youtube.com/browse_ajax?action_continuation=1&continuation=4qmFsgJAEhhVQ0ZNRVlUdjZONjRoSUw5RmxRX2h4QncaJEVnWjJhV1JsYjNNZ0FEZ0JZQUZxQUhvQk1yZ0JBQSUzRCUzRA%253D%253D"));
	}
}
