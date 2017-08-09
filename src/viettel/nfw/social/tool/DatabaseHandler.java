package viettel.nfw.social.tool;

import java.io.IOException;
import java.util.Map;
import static org.fusesource.leveldbjni.JniDBFactory.asString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class DatabaseHandler {

    private static final Logger LOG = LoggerFactory.getLogger(DatabaseHandler.class);

    private static DatabaseHandler instance = null;

    public static DatabaseHandler getInstance() {
        try {
            return instance == null ? instance = new DatabaseHandler() : instance;
        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
            return null;
        }
    }

    private static final String DB_MAPING_UN_ID = "username2id.db";
    private static final String DB_ERROR_UN = "errorusername.db";
    private final StringToStringRepository username2IdRepository;
    private final StringToStringRepository errorUsernameRepository;

    DatabaseHandler() throws IOException {
        username2IdRepository = new StringToStringRepository(DB_MAPING_UN_ID);
        errorUsernameRepository = new StringToStringRepository(DB_ERROR_UN);
    }

    public void shutdown() {
        try {
            username2IdRepository.close();
            errorUsernameRepository.close();
        } catch (IOException ex) {
            LOG.error("Error while closing database", ex);
        }
    }

    public synchronized String getId(String username) {
        byte[] key = username.trim().toLowerCase().getBytes();
        byte[] value = username2IdRepository.get(key);
        return asString(value);
    }

    public synchronized boolean containsUsername(String username) {
        String id = getId(username);
        return id != null;
    }

    public synchronized boolean writeUnId(String username, String id) {
        boolean isAdded = false;
        byte[] key = username.trim().toLowerCase().getBytes();
        byte[] value = id.trim().toLowerCase().getBytes();
        try {
            username2IdRepository.write(key, value);
            isAdded = true;
        } catch (IOException ex) {
            LOG.error("Error while writing to username-id repo", ex);
        }
        return isAdded;
    }

    public synchronized long sizeOfUnId() {
        Map<String, String> all = username2IdRepository.getAllData();
        if (all != null) {
            return all.size();
        }
        return -1;
    }

    public synchronized boolean containsErrorUsername(String username) {
        byte[] key = username.trim().toLowerCase().getBytes();
        String value = asString(errorUsernameRepository.get(key));
        return value != null;
    }

    public synchronized boolean writeErrorUsername(String username) {
        boolean isAdded = false;
        byte[] keyAndValue = username.trim().toLowerCase().getBytes();
        try {
            errorUsernameRepository.write(keyAndValue, keyAndValue);
            isAdded = true;
        } catch (IOException ex) {
            LOG.error("Error while writing to error-username repo", ex);
        }
        return isAdded;
    }

    public static void main(String[] args) throws IOException {
        StringToStringRepository repo = StringToStringRepository.getInstance();
        String key = "duongth5";
        repo.write(key.getBytes(), "data".getBytes());
        byte[] valueByteArray = repo.get(key.getBytes());
        System.out.println(valueByteArray.length);
        String value = asString(valueByteArray);
        System.out.println(value);

        String key2 = "lynd";
        byte[] valueByteArray2 = repo.get(key2.getBytes());
        String value2 = asString(valueByteArray2);
        System.out.println(value2);
    }

}
