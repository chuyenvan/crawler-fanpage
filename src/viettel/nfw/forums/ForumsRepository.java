package viettel.nfw.forums;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import static org.fusesource.leveldbjni.JniDBFactory.asString;
import static org.fusesource.leveldbjni.JniDBFactory.factory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.slf4j.LoggerFactory;
import viettel.nfw.social.facebook.updatenews.graph.entities.ObjectRequest;
import viettel.nfw.social.facebook.updatenews.repo.CrawledFacebookObjectRepository;
import vn.viettel.utils.SerializeObjectUtils;

/**
 *
 * @author duongth5
 */
public class ForumsRepository {

    private final DB db;
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(CrawledFacebookObjectRepository.class);

    public ForumsRepository(String dbFile) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        try {
            db = factory.open(new File(dbFile), options);
        } catch (IOException ex) {
            LOG.error("Error in opening data base {}", dbFile);
            throw ex;
        }
    }

    public void write(byte[] key, byte[] value) throws IOException {
        try (WriteBatch batch = db.createWriteBatch()) {
            batch.put(key, value);
            db.write(batch);
        }
    }

    public void delete(byte[] key) throws IOException {
        try (WriteBatch batch = db.createWriteBatch()) {
            batch.delete(key);
            db.write(batch);
        }
    }

    public byte[] get(byte[] key) {
        return db.get(key);
    }

    public Map<String, ThreadInfo> getAllData() {
        Map<String, ThreadInfo> data = new HashMap<>();
        DBIterator iterator = db.iterator();
        try {
            for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                String key = asString(iterator.peekNext().getKey());
                // String value = asString(iterator.peekNext().getValue());
                ThreadInfo value = (ThreadInfo) SerializeObjectUtils.readingObjectFromByteArrayUsingGzip(iterator.peekNext().getValue());
                data.put(key, value);
            }
        } finally {
            try {
                // Make sure you close the iterator to avoid resource leaks.
                iterator.close();
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
        return data;
    }

    public void close() throws IOException {
        db.close();
    }
    private static ForumsRepository instance = null;

    public static ForumsRepository getInstance(String dbFile) {
        try {
            return instance == null ? instance = new ForumsRepository(dbFile) : instance;
        } catch (IOException ex) {
            return null;
        }
    }
}
