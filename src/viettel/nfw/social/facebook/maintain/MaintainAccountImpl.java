package viettel.nfw.social.facebook.maintain;

import viettel.nfw.social.facebook.core.activity.VisitOtherProfileActivity;
import viettel.nfw.social.facebook.core.activity.MakeFriendsActivity;
import viettel.nfw.social.facebook.core.activity.SharePostActivity;
import viettel.nfw.social.facebook.core.activity.VisitMyTimelineActivity;
import viettel.nfw.social.facebook.core.activity.ReadMyFriendsTabActivity;
import viettel.nfw.social.facebook.core.activity.PostToGroupActivity;
import viettel.nfw.social.facebook.core.activity.PostMyStatusActivity;
import viettel.nfw.social.facebook.core.activity.LikePostActivity;
import viettel.nfw.social.facebook.core.activity.VisitMyNewsFeedActivity;
import viettel.nfw.social.facebook.core.activity.CommentPostActivity;
import viettel.nfw.social.facebook.core.activity.ReadMyNotificationActivity;
import viettel.nfw.social.facebook.core.activity.JoinGroupPageActivity;
import viettel.nfw.social.facebook.entity.ActionInDay;
import java.net.CookieManager;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.facebook.core.FacebookAction;
import viettel.nfw.social.common.ServiceOutlinks;
import viettel.nfw.social.common.Account;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.entity.Activity;
import viettel.nfw.social.facebook.entity.IActivity;
import viettel.nfw.social.utils.DateUtils;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class MaintainAccountImpl implements Runnable {

	private static final Logger LOG = LoggerFactory.getLogger(MaintainAccountImpl.class);
	private static final Random randomize = new Random();
	private final Account account;
	private final Proxy proxy;

	public MaintainAccountImpl(Account account, Proxy proxy) {
		this.account = account;
		this.proxy = proxy;
	}

	@Override
	public void run() {
		Thread.currentThread().setName(account.getUsername());
		CookieManager cookieManager = new CookieManager();
		FacebookAction fbAction = new FacebookAction(account, cookieManager, proxy);
		boolean isLogin = false;
		boolean isContinue = true;

		while (true) {
			long wakeUpTime = System.currentTimeMillis();
			LOG.info("WAKE UP at {}", new Date(wakeUpTime).toString());
			ServiceOutlinks.sendLog(account.getUsername(), "WAKE UP");
			long midnightTime = DateUtils.getMidnight();

			// wake up and DO your JOB
			if (!isLogin) {
				AccountStatus accStatus = fbAction.login();
				if (accStatus.equals(AccountStatus.LOGIN_OK)) {
					isLogin = true;
				} else {
					// send error to master
					ServiceOutlinks.sendError(account.getUsername(), accStatus.toString());
					String querySvcMode = ApplicationConfiguration.getInstance().getConfiguration("service.query.mode");
					if (StringUtils.equals(querySvcMode, "RESTRICTIONS")) {

					} else {
						// send lock account to master
						ServiceOutlinks.addLockedAccount(account.getUsername(), accStatus.toString());
					}
					// remove account from active list
					RunMaintain.activeAccounts.remove(account.getUsername());
					LOG.warn("Account locked {}. BREAK!", account.getUsername());
					break;
				}
			}

			LOG.info("Start generate activities");
			// generate activities
			List<ActionInDay> todayActions = new ArrayList<>();
			if (RunMaintain.TEST_MODE) {
				ActionInDay testAID = new ActionInDay();
				testAID.activity = Activity.COMMENT_POST;
				testAID.startTime = System.currentTimeMillis() + 30 * 1000;
				todayActions.add(testAID);
			} else {
				todayActions = scheduledActivitiesByTime(account, wakeUpTime, midnightTime);
			}
			for (ActionInDay todayAction : todayActions) {
				LOG.info(todayAction.toString());
			}

			// doing activity in day
			int currentActivity = 0;
			while (currentActivity < todayActions.size()) {
				long currentTime = System.currentTimeMillis();
				if (currentTime < todayActions.get(currentActivity).startTime) {
					try {
						// do nothing
						Thread.sleep(1000);
					} catch (InterruptedException ex) {
						LOG.error(ex.getMessage(), ex);
					}
					continue;
				}
				Activity act = todayActions.get(currentActivity).activity;
				LOG.info("Start {} at {}", act.name(), new Date(currentTime).toString());

				// TODO send log activity to Master
				ServiceOutlinks.sendLog(account.getUsername(), act.name());

				IActivity t = null;
				switch (act) {
					case COMMENT_POST:
						t = new CommentPostActivity(fbAction);
						break;
					case JOIN_GROUP_PAGE:
						t = new JoinGroupPageActivity(fbAction);
						break;
					case LIKE_POST:
						t = new LikePostActivity();
						break;
					case MAKE_FRIENDS:
						t = new MakeFriendsActivity(fbAction);
						break;
					case POST_MY_STATUS:
						t = new PostMyStatusActivity(fbAction);
						break;
					case POST_TO_GROUP:
						t = new PostToGroupActivity();
						break;
					case READ_MY_FRIENDS_TAB:
						t = new ReadMyFriendsTabActivity();
						break;
					case READ_MY_NOTIFICATION:
						t = new ReadMyNotificationActivity(fbAction);
						break;
					case SHARE_POST:
						t = new SharePostActivity();
						break;
					case VISIT_MY_NEWS_FEED:
						t = new VisitMyNewsFeedActivity(fbAction);
						break;
					case VISIT_MY_TIMELINE:
						t = new VisitMyTimelineActivity(fbAction);
						break;
					case VISIT_OTHER_PROFILE:
						t = new VisitOtherProfileActivity();
						break;
					default:
						break;
				}
				if (t != null) {
					t.run();
					LOG.info("End {} at {}", act.name(), new Date(System.currentTimeMillis()).toString());
					AccountStatus accStatus = t.getStatus();
					if (accStatus != null) {
						LOG.info("Status of activity {}: {}", act.name(), accStatus.toString());
						int code = accStatus.getCode();
						if (code == AccountStatus.KICKOUT_LEVEL_2.getCode()
							|| code == AccountStatus.KICKOUT_UNKNOWN.getCode()) {
                            // kick out
							// send error to master
							ServiceOutlinks.sendError(account.getUsername(), accStatus.toString());
							String querySvcMode = ApplicationConfiguration.getInstance().getConfiguration("service.query.mode");
							if (StringUtils.equals(querySvcMode, "RESTRICTIONS")) {

							} else {
								// send lock account to master
								ServiceOutlinks.addLockedAccount(account.getUsername(), accStatus.toString());
							}
							// remove account from active list
							RunMaintain.activeAccounts.remove(account.getUsername());
							LOG.warn("Account locked {}. BREAK!!!!!!!", account.getUsername());
							isContinue = false;
							break;
						}
					}
				}
				LOG.info("aaaaaaa");
				currentActivity++;
			}
			LOG.info("bbbbbbb");
			if (!isContinue) {
				break;
			}

			ServiceOutlinks.sendLog(account.getUsername(), "SLEEP");
			// calcualte for sleep
			while (System.currentTimeMillis() < midnightTime) {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException ex) {
					LOG.error(ex.getMessage(), ex);
				}
			}
			LOG.info("pass midnight");
			// set bedTime
			long sleepHour = Funcs.randInt(5 * 60 * 60 * 1000, 7 * 60 * 60 * 1000); // rand between 5 - 7 hours
			LOG.info("next wake up time at {}", new Date(System.currentTimeMillis() + sleepHour).toString());
			// sleep for next wake up
			try {
				Thread.sleep(sleepHour);
			} catch (InterruptedException ex) {
				LOG.error(ex.getMessage(), ex);
			}
		}
	}

	private static List<Activity> genActivitiesToday(long createdAccountTime) {
        // random will do activity or not
		// random number time for doing this activity
		List<Activity> willDoActivities = new ArrayList<>();
		for (Activity value : Activity.values()) {
			if (value.equals(Activity.MAKE_FRIENDS)) {
				long timeFromCreatedAccToNow = System.currentTimeMillis() - createdAccountTime;
				long validTimeToMakeFriends = 3 * 24 * 60 * 60 * 1000; // 3 days
				if (timeFromCreatedAccToNow <= validTimeToMakeFriends) {
					continue;
				}
			}
			int randomRate = randomize.nextInt(100);
			if (randomRate <= value.getRate()) {
				int randomNoTime = randomize.nextInt(value.getLimitNumberPerDay());
				for (int i = 0; i < randomNoTime; i++) {
					willDoActivities.add(value);
				}
			}
		}
		return willDoActivities;
	}

	private static List<ActionInDay> scheduledActivitiesByTime(Account account, long wakeUpTime, long midnightTime) {
		List<Activity> todayActivities = new ArrayList<>();
		while (todayActivities.isEmpty()) {
			todayActivities = genActivitiesToday(account.getAddedTime());
		}
		// order time to all activities
		Collections.shuffle(todayActivities);
		int totalActiveTime = (int) (midnightTime - wakeUpTime);
		int averageTime = totalActiveTime / todayActivities.size();
		LOG.info("wakeUpTime   {}", new Date(wakeUpTime).toString());
		LOG.info("midnightTime {}", new Date(midnightTime).toString());
		LOG.info("averageTime  {} ms", averageTime);
		long startActivityTime = wakeUpTime;
		List<ActionInDay> todayActions = new ArrayList<>();
		for (Activity todayActivity : todayActivities) {
			int randStep = randomize.nextInt(averageTime);
			startActivityTime += randStep;
			LOG.info("randStep {} - startActivityTime {}", randStep, new Date(startActivityTime).toString());
			ActionInDay todayAction = new ActionInDay();
			todayAction.activity = todayActivity;
			todayAction.startTime = startActivityTime;
			todayActions.add(todayAction);
		}
		return todayActions;
	}
}
