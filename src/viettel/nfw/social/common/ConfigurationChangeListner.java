package viettel.nfw.social.common;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class ConfigurationChangeListner implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationChangeListner.class);

    private String configFileName = null;
    private String fullFilePath = null;

    public ConfigurationChangeListner(final String filePath) {
        this.fullFilePath = filePath;
    }

    @Override
    public void run() {
        Thread.currentThread().setName(ConfigurationChangeListner.class.getName());
        try {
            register(this.fullFilePath);
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    private void register(final String file) throws IOException {
        final int lastIndex = file.lastIndexOf("/");
        String dirPath = file.substring(0, lastIndex + 1);
        String fileName = file.substring(lastIndex + 1, file.length());
        this.configFileName = fileName;

        configurationChanged(file);
        startWatcher(dirPath, fileName);
    }

    private void startWatcher(String dirPath, String file) throws IOException {
        final WatchService watchService = FileSystems.getDefault().newWatchService();
        Path path = Paths.get(dirPath);
        path.register(watchService, ENTRY_MODIFY);

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    watchService.close();
                } catch (IOException ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        });

        while (true) {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.context().toString().equals(configFileName)) {
                        configurationChanged(dirPath + file);
                    }
                }
                boolean reset = key.reset();
                if (!reset) {
                    LOG.warn("Could not reset the watch key.");
                    break;
                }
            } catch (Exception ex) {
                LOG.error("InterruptedException: " + ex.getMessage(), ex);
            }
        }
    }

    public void configurationChanged(final String file) {
        LOG.info("Refreshing the configuration.");
        ApplicationConfiguration.getInstance().initilize(file);
    }
}
