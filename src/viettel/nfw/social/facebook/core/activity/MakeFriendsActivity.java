package viettel.nfw.social.facebook.core.activity;

import java.net.Proxy;
import java.net.URI;
import java.util.Map;
import java.util.Random;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.core.FacebookAction;
import static viettel.nfw.social.facebook.core.FacebookAction.crawl;
import viettel.nfw.social.facebook.core.Parser;
import viettel.nfw.social.facebook.entity.AccountStatus;
import viettel.nfw.social.facebook.entity.IActivity;
import viettel.nfw.social.facebook.core.HttpRequest;

/**
 *
 * @author duongth5
 */
public class MakeFriendsActivity implements IActivity {

    private static final Logger LOG = LoggerFactory.getLogger(MakeFriendsActivity.class);

    private final FacebookAction fbAction;
    private MethodType chosenMethod;
    private static final Random randomize = new Random();
    private AccountStatus status;

    private static enum MethodType {

        MAKE_FRIENDS_1
    }

    public MakeFriendsActivity(FacebookAction fbAction) {
        this.fbAction = fbAction;
    }

    @Override
    public void run() {
        LOG.info("Run {}", MakeFriendsActivity.class.getName());
        AccountStatus retStatus = null;
        boolean isFunctionReady = false;
        if (isFunctionReady) {
            setMethod();

            switch (chosenMethod) {
                case MAKE_FRIENDS_1:
                    doMethod1(fbAction);
                    break;
            }
        }
        // set status
        status = retStatus;
    }

    @Override
    public AccountStatus getStatus() {
        return status;
    }

    @Override
    public void setMethod() {
        int sizeOfMethod = MethodType.values().length;
        int pos = randomize.nextInt(sizeOfMethod);
        chosenMethod = MethodType.values()[pos];
    }

    private void doMethod1(FacebookAction fbAction) {
        addFriend("", fbAction.getHttp(), fbAction.getProxy());
    }

    private void addFriend(String profileUrl, HttpRequest http, Proxy proxy) {
        try {
            // visit this profile
            String response = crawl(profileUrl, http, proxy);
            AccountStatus responseKOT = Parser.verifyResponseHtml(profileUrl, response, true);
            if (!responseKOT.equals(AccountStatus.ACTIVE)) {
                LOG.info("ret {}", responseKOT);
            }
            Map<URI, String> allLinks = Parser.getAllLinksOfCurrentHtml(new URI(profileUrl), response);
            URI addFriendUri = null;
            for (Map.Entry<URI, String> entrySet : allLinks.entrySet()) {
                URI key = entrySet.getKey();
                String value = entrySet.getValue();
                if (StringUtils.equalsIgnoreCase(value, "Add Friend")) {
                    addFriendUri = key;
                    break;
                }
            }

            if (addFriendUri != null) {
                String addFriendResponse = crawl(addFriendUri.toString(), http, proxy);
                AccountStatus addFriendResponseKOT = Parser.verifyResponseHtml(addFriendUri.toString(), addFriendResponse, true);
                LOG.info("add {} - ret {}", profileUrl, addFriendResponseKOT);
            }

        } catch (Exception ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

//    public AccountStatus surfMyHome(String homeUrl, AddFriendAction action) {
//        try {
//            String response = crawl(homeUrl, http, proxy);
//            AccountStatus responseKOT = Parser.verifyResponseHtml(homeUrl, response, false);
//            if (!responseKOT.equals(AccountStatus.ACTIVE)) {
//                return responseKOT;
//            }
//
//            Parser.getHeaderBarOfCurrentHtml(new URI(homeUrl), response);
//            Map<URI, String> allLinks = Parser.getAllLinksOfCurrentHtml(new URI(homeUrl), response);
//
//            if (action.equals(AddFriendAction.SURF_FEED_SEE_MORE)) {
//                URI seeMoreUri = null;
//                for (Map.Entry<URI, String> entrySet : allLinks.entrySet()) {
//                    URI key = entrySet.getKey();
//                    String value = entrySet.getValue();
//                    String keyPath = key.getPath();
//                    if (StringUtils.contains(value, "See more stories") && StringUtils.contains(keyPath, "/stories.php")) {
//                        seeMoreUri = key;
//                    }
//                }
//                if (seeMoreUri != null) {
//                    Thread.sleep(5 * 1000); // 5 seconds
//                    String seeMoreResponse = crawl(seeMoreUri.toString(), http, proxy);
//                    AccountStatus seeMoreResponseKOT = Parser.verifyResponseHtml(seeMoreUri.toString(), seeMoreResponse, false);
//                    if (!seeMoreResponseKOT.equals(AccountStatus.ACTIVE)) {
//                        return seeMoreResponseKOT;
//                    }
//                    // TODO update home url
//                    FacebookHeaderBar seeMoreHeader = Parser.getHeaderBarOfCurrentHtml(seeMoreUri, seeMoreResponse);
//
//                }
//            } else if (action.equals(AddFriendAction.SURF_FEED_POST)) {
//                List<URI> stories = new ArrayList<>();
//                for (Map.Entry<URI, String> entrySet : allLinks.entrySet()) {
//                    URI key = entrySet.getKey();
//                    String value = entrySet.getValue();
//                    if (StringUtils.contains(value, "Full Story")) {
//                        stories.add(key);
//                    }
//                }
//                if (!stories.isEmpty()) {
//                    try {
//                        URI selectedStory = stories.remove(0);
//                        if (!isStoryVisited(selectedStory, account.getUsername())) {
//                            String storyResponse = crawl(selectedStory.toString(), http, proxy);
//                            AccountStatus storyResponseKOT = Parser.verifyResponseHtml(selectedStory.toString(), storyResponse, true);
//                            if (!storyResponseKOT.equals(AccountStatus.ACTIVE)) {
//                                // return status
//                                return storyResponseKOT;
//                            }
//                            // select people who commente
//                            Set<URI> setCommentProfiles = Parser.getPeopleCommentOrLike(selectedStory, storyResponse);
//                            List<URI> listCommentProfiles = new ArrayList<>();
//                            listCommentProfiles.addAll(setCommentProfiles);
//                            // TODO complete this
//                            URI profileUri = listCommentProfiles.remove(0);
//                            addFriend(profileUri.toString());
//                        }
//                    } catch (Exception ex) {
//                        LOG.error(ex.getMessage(), ex);
//                    }
//                }
//            } else if (action.equals(AddFriendAction.FIND_FRIEND_HOVERCARD)) {
//            }
//
//        } catch (Exception ex) {
//            LOG.error(ex.getMessage(), ex);
//        }
//        return AccountStatus.ACTIVE;
//    }
    public boolean isStoryVisited(URI uri, String account) {
        // TODO complete this
        return false;
    }

    public static enum AddFriendAction {

        SURF_FEED_SEE_MORE, SURF_FEED_POST, FIND_FRIEND_HOVERCARD, SEARCH_FORM
    }

}
