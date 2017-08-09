package viettel.nfw.social.facebook.updatenews.repo;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static org.fusesource.leveldbjni.JniDBFactory.asString;
import static org.fusesource.leveldbjni.JniDBFactory.bytes;
import static org.fusesource.leveldbjni.JniDBFactory.factory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.slf4j.LoggerFactory;
import vn.viettel.utils.SerializeObjectUtils;

/**
 *
 * @author ralph
 */
public class ProfilePostsRepository {

    private static final String DB_FILE = "database/profile_posts.db";
    private final DB db;
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(ProfilePostsRepository.class);
    private final Map<String, LastestProfilePostList> profileId2LastPosts = new ConcurrentHashMap<>();
    private final Object o = new Object();

    public ProfilePostsRepository() throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        try {
            db = factory.open(new File(DB_FILE), options);
        } catch (IOException ex) {
            LOG.error("Error in opening data base {}", DB_FILE);
            throw ex;
        }
//        loadFromDb();
    }

    public ProfilePostsRepository(String dbFile) throws IOException {
        Options options = new Options();
        options.createIfMissing(true);
        try {
            db = factory.open(new File(dbFile), options);
        } catch (IOException ex) {
            LOG.error("Error in opening data base {}", dbFile);
            throw ex;
        }
    }

    private void loadFromDb() throws IOException {
        try (DBIterator iterator = db.iterator()) {
            for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                try {
                    String profileId = asString(iterator.peekNext().getKey());
                    LastestProfilePostList posts = (LastestProfilePostList) SerializeObjectUtils.readingObjectFromByteArrayUsingGzip(iterator.peekNext().getValue());
                    synchronized (o) {
                        profileId2LastPosts.put(profileId, posts);
                    }
                } catch (Exception ex) {
                    LOG.error(ex.getMessage(), ex);
                }
            }
        }
    }

    public void saveToDb() throws IOException {
        try (WriteBatch batch = db.createWriteBatch()) {
            synchronized (o) {
                for (Map.Entry<String, LastestProfilePostList> entry : profileId2LastPosts.entrySet()) {
                    try {
                        batch.put(bytes(entry.getKey()), SerializeObjectUtils.serializeObjectToByteArrayUsingGzip(entry.getValue()));
                    } catch (Exception ex) {
                        LOG.error("Error in saving {} ", entry.getKey());
                        LOG.error(ex.getMessage(), ex);
                    }
                }
            }
            db.write(batch);
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

    public List<String> getAllKeys() {
        List<String> keys = new ArrayList<>();
        DBIterator iterator = db.iterator();
        try {
            for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                String key = asString(iterator.peekNext().getKey());
                // String value = asString(iterator.peekNext().getValue());
                keys.add(key);
            }
        } finally {
            try {
                // Make sure you close the iterator to avoid resource leaks.
                iterator.close();
            } catch (IOException ex) {
                LOG.error(ex.getMessage(), ex);
            }
        }
        return keys;
    }

    public void close() throws IOException {
        db.close();
    }
    private static ProfilePostsRepository instance = null;

    public static ProfilePostsRepository getInstance() {
        try {
            return instance == null ? instance = new ProfilePostsRepository() : instance;
        } catch (IOException ex) {
            return null;
        }
    }

    public static class ProfilePost implements Serializable {

        public final long crawledTime;
        public final String postId;
        public final String appId;

        public ProfilePost(String postId, String appId, long crawledTime) {
            this.crawledTime = crawledTime;
            this.appId = appId;
            this.postId = postId;
        }

        @Override
        public String toString() {
            return String.format("%s-%s-%s", appId, postId, new Date(crawledTime).toString());
        }
    }

    public static class LastestProfilePostList implements Serializable {

        private final LinkedList<ProfilePost> posts = new LinkedList<>();
        private int curSize = 0;
        private final int maxSize;

        public LastestProfilePostList(int maxSize) {
            this.maxSize = maxSize;
        }

        public synchronized void addPost(ProfilePost post) {
            if (curSize == maxSize) {
                posts.removeFirst();
                posts.addLast(post);
            } else {
                posts.addLast(post);
                curSize++;
            }
        }

        public List<ProfilePost> getPosts() {
            return posts;
        }
    }
}
