package viettel.nfw.social.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class ApplicationConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationConfiguration.class);

    private final static ApplicationConfiguration INSTANCE = new ApplicationConfiguration();

    public static ApplicationConfiguration getInstance() {
        return INSTANCE;
    }

    private static final Properties configuration = new Properties();

    private static Properties getConfiguration() {
        return configuration;
    }

    public void initilize(final String file) {
        try {
            InputStream in = new FileInputStream(new File(file));
            configuration.load(in);
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    public String getConfiguration(final String key) {
        return (String) getConfiguration().get(key);
    }

    public String getConfigurationWithDefaultValue(final String key, final String defaultValue) {
        return getConfiguration().getProperty(key, defaultValue);
    }
}
