package viettel.nfw.social.common;

import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.utils.Pair;

/**
 *
 * @author duongth5
 */
public class Account {

    private String accType;
    private String username;
    private String password;
    private String userAgent;
    private String serverIp;
    private long addedTime;

    private Integer numberProfilesPerDay;
    private Pair<Integer, Integer> numberProfilePerTurn;
    private Pair<Integer, Integer> sleepTimeBetweenTurns;
    private Pair<Integer, Integer> sleepTimeBetweenProfiles;
    private Pair<Integer, Integer> sleepTimeBetweenRequest;
    private AccountStatus status;

    public Account() {
    }

    public Account(String accType, String username, String password, String userAgent, String serverIp, long addedTime) {
        this.accType = accType;
        this.username = username;
        this.password = password;
        this.userAgent = userAgent;
        this.serverIp = serverIp;
        this.addedTime = addedTime;
    }

    public String getAccType() {
        return accType;
    }

    public void setAccType(String accType) {
        this.accType = accType;
    }

    /**
     * @return the addedTime
     */
    public long getAddedTime() {
        return addedTime;
    }

    /**
     * @param addedTime the addedTime to set
     */
    public void setAddedTime(long addedTime) {
        this.addedTime = addedTime;
    }

    /**
     * @return the username
     */
    public String getUsername() {
        return username;
    }

    /**
     * @param username the username to set
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * @return the password
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password the password to set
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * @return the userAgent
     */
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * @param userAgent the userAgent to set
     */
    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getServerIp() {
        return serverIp;
    }

    public void setServerIp(String serverIp) {
        this.serverIp = serverIp;
    }

    /**
     * @return the numberProfilesPerDay
     */
    public Integer getNumberProfilesPerDay() {
        return numberProfilesPerDay;
    }

    /**
     * @param numberProfilesPerDay the numberProfilesPerDay to set
     */
    public void setNumberProfilesPerDay(Integer numberProfilesPerDay) {
        this.numberProfilesPerDay = numberProfilesPerDay;
    }

    /**
     * @return the numberProfilePerTurn
     */
    public Pair<Integer, Integer> getNumberProfilePerTurn() {
        return numberProfilePerTurn;
    }

    /**
     * @param numberProfilePerTurn the numberProfilePerTurn to set
     */
    public void setNumberProfilePerTurn(Pair<Integer, Integer> numberProfilePerTurn) {
        this.numberProfilePerTurn = numberProfilePerTurn;
    }

    /**
     * @return the sleepTimeBetweenTurns
     */
    public Pair<Integer, Integer> getSleepTimeBetweenTurns() {
        return sleepTimeBetweenTurns;
    }

    /**
     * @param sleepTimeBetweenTurns the sleepTimeBetweenTurns to set
     */
    public void setSleepTimeBetweenTurns(Pair<Integer, Integer> sleepTimeBetweenTurns) {
        this.sleepTimeBetweenTurns = sleepTimeBetweenTurns;
    }

    /**
     * @return the sleepTimeBetweenProfiles
     */
    public Pair<Integer, Integer> getSleepTimeBetweenProfiles() {
        return sleepTimeBetweenProfiles;
    }

    /**
     * @param sleepTimeBetweenProfiles the sleepTimeBetweenProfiles to set
     */
    public void setSleepTimeBetweenProfiles(Pair<Integer, Integer> sleepTimeBetweenProfiles) {
        this.sleepTimeBetweenProfiles = sleepTimeBetweenProfiles;
    }

    /**
     * @return the sleepTimeBetweenRequest
     */
    public Pair<Integer, Integer> getSleepTimeBetweenRequest() {
        return sleepTimeBetweenRequest;
    }

    /**
     * @param sleepTimeBetweenRequest the sleepTimeBetweenRequest to set
     */
    public void setSleepTimeBetweenRequest(Pair<Integer, Integer> sleepTimeBetweenRequest) {
        this.sleepTimeBetweenRequest = sleepTimeBetweenRequest;
    }

    /**
     * @return the status
     */
    public AccountStatus getStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return accType + "::" + username + "::" + password + "::" + serverIp + "::" + addedTime;
    }

}
