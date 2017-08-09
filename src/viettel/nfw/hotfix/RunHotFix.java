package viettel.nfw.hotfix;

import viettel.nfw.social.common.ApplicationConfiguration;
import viettel.nfw.social.common.ConfigurationChangeListner;
import viettel.nfw.social.utils.Funcs;

/**
 *
 * @author duongth5
 */
public class RunHotFix {

    public static void main(String[] args) {
        ConfigurationChangeListner configListner = new ConfigurationChangeListner("conf/app-update-news.properties");
        new Thread(configListner).start();
        Funcs.sleep(2000);
        
        // Get information of ActiveMQ server
        String host = ApplicationConfiguration.getInstance().getConfiguration("activemq.ip");
        int port = Integer.parseInt(ApplicationConfiguration.getInstance().getConfiguration("activemq.port"));
        String activeMQUrl = String.format("tcp://%s:%d", host, port);
        String activeMQUsername = ApplicationConfiguration.getInstance().getConfiguration("activemq.username");
        String activeMQPassword = ApplicationConfiguration.getInstance().getConfiguration("activemq.password");

        GetPageGroupConsumerHotFix hotfix = new GetPageGroupConsumerHotFix(activeMQUrl, activeMQUsername, activeMQPassword);
        new Thread(hotfix).start();
    }
}
