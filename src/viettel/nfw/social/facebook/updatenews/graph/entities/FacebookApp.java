package viettel.nfw.social.facebook.updatenews.graph.entities;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;
import org.apache.hadoop.io.Writable;

/**
 *
 * @author duongth5
 */
public class FacebookApp implements Serializable, Writable {

	private static final long serialVersionUID = 1L;

	/**
	 * Account username
	 */
	private String accountName;
	/**
	 * Account password
	 */
	private String accountPass;
	/**
	 * Application Name
	 */
	private String appName;
	/**
	 * Application Id
	 */
	private String appID;
	/**
	 * The oldest version of the API this application can access Ex: v2.3, v2.4 ...
	 */
	private String apiVersion;
	/**
	 * Application secret key
	 */
	private String appSecret;
	/**
	 * App access token
	 */
	private String appAccessToken;
	/**
	 * User access token
	 */
	private String userAccessToken;

	public FacebookApp() {
	}

	public FacebookApp(String accountName, String accountPass, String appName, String appID, String apiVersion,
			String appSecret, String appAccessToken, String userAccessToken) {
		this.accountName = accountName;
		this.accountPass = accountPass;
		this.appName = appName;
		this.appID = appID;
		this.apiVersion = apiVersion;
		this.appSecret = appSecret;
		this.appAccessToken = appAccessToken;
		this.userAccessToken = userAccessToken;
	}

	public String getAccountName() {
		return accountName;
	}

	public void setAccountName(String accountName) {
		this.accountName = accountName;
	}

	public String getAccountPass() {
		return accountPass;
	}

	public void setAccountPass(String accountPass) {
		this.accountPass = accountPass;
	}

	public String getAppName() {
		return appName;
	}

	public void setAppName(String appName) {
		this.appName = appName;
	}

	public String getAppID() {
		return appID;
	}

	public void setAppID(String appID) {
		this.appID = appID;
	}

	public String getApiVersion() {
		return apiVersion;
	}

	public void setApiVersion(String apiVersion) {
		this.apiVersion = apiVersion;
	}

	public String getAppSecret() {
		return appSecret;
	}

	public void setAppSecret(String appSecret) {
		this.appSecret = appSecret;
	}

	public String getAppAccessToken() {
		return appAccessToken;
	}

	public void setAppAccessToken(String appAccessToken) {
		this.appAccessToken = appAccessToken;
	}

	public String getUserAccessToken() {
		return userAccessToken;
	}

	public void setUserAccessToken(String userAccessToken) {
		this.userAccessToken = userAccessToken;
	}

	@Override
	public void write(DataOutput d) throws IOException {
		d.writeUTF(accountName);
		d.writeUTF(accountPass);
		d.writeUTF(appName);
		d.writeUTF(appID);
		d.writeUTF(apiVersion);
		d.writeUTF(appSecret);
		d.writeUTF(appAccessToken);
		d.writeUTF(userAccessToken);
	}

	@Override
	public void readFields(DataInput di) throws IOException {
		this.accountName = di.readUTF();
		this.accountPass = di.readUTF();
		this.appName = di.readUTF();
		this.appID = di.readUTF();
		this.apiVersion = di.readUTF();
		this.appSecret = di.readUTF();
		this.appAccessToken = di.readUTF();
		this.userAccessToken = di.readUTF();
	}

	@Override
	public String toString() {
		return "{" + accountName + "::" + accountPass + " " + appName + " "
				+ appID + "::" + appSecret + "::" + apiVersion
				+ " AppAccessToken::" + appAccessToken
				+ " UserAccessToken::" + userAccessToken + '}';
	}

}
