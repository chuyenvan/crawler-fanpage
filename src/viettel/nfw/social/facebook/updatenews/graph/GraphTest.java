package viettel.nfw.social.facebook.updatenews.graph;

import com.restfb.DefaultFacebookClient;
import com.restfb.FacebookClient;
import com.restfb.FacebookClient.AccessToken;
import com.restfb.FacebookClient.DebugTokenInfo;
import com.restfb.Parameter;
import com.restfb.Version;
import com.restfb.exception.FacebookException;
import com.restfb.types.Page;
import java.io.IOException;
import java.net.Proxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.pgcrawler.crawler.NewGraphCrawler;
import viettel.nfw.social.facebook.updatenews.graph.entities.FacebookApp;
import viettel.nfw.social.facebook.updatenews.graph.entities.ProfileType;
import viettel.nfw.social.model.facebook.FacebookObject;

/**
 *
 * @author duongth5
 */
public class GraphTest {

    private static final Logger LOG = LoggerFactory.getLogger(GraphTest.class);

    public static void main(String[] args) throws FacebookException, IOException {
        String appId = "1572398473056999";
        String userAccessToken = "EAAWWFoxukucBAIYZB5fs1kHZCZCfwZBFE9rdfYtjAMx24CL5MDzhlDiwnyXfTM9djdnB1xxTPau1jbSq96tgZAyPsL3FdKorDoS81oBXiWgasIS6zaQIST8PZCa8RBBGlutY4mrLJ0dsvIymhTrbvkVQZAcRu4mglZAjoiAulQmuFAZDZD";
        String appSecret = "f3c60f837d8e5ce3aa6ede9f38b413f8";
        Version apiVersion = Version.VERSION_2_7;
        FacebookApp appInfo = new FacebookApp(null, null, null, appId, apiVersion.toString(), appSecret, userAccessToken, null);
        FacebookGraphActions fga = new FacebookGraphActions(appInfo);
        String appAcess = fga.getAppAccessToken();
        appInfo.setUserAccessToken(appAcess);
//        if (fga.initApp()) {
//            Pair<Pair<Set<String>, Set<String>>, Integer> profile;
//            profile = fga.getPosts("VietnamGameTV", null, appAcess, Proxy.NO_PROXY);
//            Iterator iter=profile.first.first.iterator();
//            while(iter.hasNext())
//            {
//                System.out.println(iter.next());
//            }
//            
//        }
        NewGraphCrawler ngc = new NewGraphCrawler(appInfo, null, Proxy.NO_PROXY);
        FacebookObject fo = ngc.doIt("206109502763824", ProfileType.PAGE_REAL, null, true);
        System.out.println(fo.toString());
        System.out.println(fo.getInfo().getFullname() );
        
        
        

        LOG.info("#######################");

//        test(userAccessToken, appId, appSecret, apiVersion);
        LOG.info("#######################");

//        String appId2 = "1449953991987089";
//        String userAccessToken2 = "CAAUmubiiD5EBAP8OUnfFiWvIWpLbw5JmMwEeTIdvFkrK7fFnpS1qcOpXoXZBH1h2kPI0MxqgnnHYcdUmVRyd9qjnNL18yTy8BhSU6FjgxhXqRZCUpZAzAqRDFKoSGPelvYIxsTzKKsuZBCikMXwxZA2vkzsHqz38oczZA7nxP3ZB7t9g1mt0R9HecnBOlv9Lve4x0Fsp09Gn88A32ln8APQ";
//        String appSecret2 = "ad6376294925f0a80e38eeb7c0283380";
//        Version apiVersion2 = Version.VERSION_2_3;
//        test(userAccessToken2, appId2, appSecret2, apiVersion2);
    }

    private static void test(String userAccessToken, String appId, String appSecret, Version apiVersion) {
        FacebookClient facebookClient = new DefaultFacebookClient(userAccessToken, appSecret, apiVersion);

        AccessToken appAccessToken = facebookClient.obtainAppAccessToken(appId, appSecret);
        LOG.info("App access token: {}", appAccessToken.toString());
        String AccessTokenStr = appAccessToken.getAccessToken();
        LOG.info("Hoang:   " + AccessTokenStr);
        DebugTokenInfo debugTokenInfo = facebookClient.debugToken(userAccessToken);
        LOG.info(debugTokenInfo.toString());
        LOG.info("is valid: {}", debugTokenInfo.isValid());
        LOG.info("expire time: {}", debugTokenInfo.getExpiresAt());

        AccessToken newAccessToken = facebookClient.obtainExtendedAccessToken(appId, appSecret);
        LOG.info(newAccessToken.toString());
        String newAccessTokenStr = newAccessToken.getAccessToken();
        LOG.info("new access token: {}", newAccessTokenStr);

        // when not refresh
        LOG.info("\n----------------------------");
        LOG.info("WHEN NOT refresh");
//        Page page = facebookClient.fetchObject("tinhte", Page.class);
//        LOG.info(page.toString());

        Page page = facebookClient.fetchObject("390567570966109", Page.class,
                Parameter.with("fields", "name,username,id,description,birthday,phone"));
        LOG.info(page.toString());

        LOG.info("\n----------------------------");
        LOG.info("WHEN DO refresh");
        facebookClient = new DefaultFacebookClient(newAccessTokenStr, appSecret, apiVersion);
        page = facebookClient.fetchObject("tinhte", Page.class);
        LOG.info(page.toString());

        page = facebookClient.fetchObject("tinhte", Page.class,
                Parameter.with("fields", "name,username,id,description,birthday,phone"));
        LOG.info(page.toString());

    }
}
