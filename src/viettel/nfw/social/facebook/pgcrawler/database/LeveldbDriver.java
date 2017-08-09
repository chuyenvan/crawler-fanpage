package viettel.nfw.social.facebook.pgcrawler.database;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.fusesource.leveldbjni.JniDBFactory.asString;
import static org.fusesource.leveldbjni.JniDBFactory.factory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Duong
 */
public class LeveldbDriver {

	private final DB db;
	private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(LeveldbDriver.class);
        
	public LeveldbDriver(String dbFile) throws IOException {
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

	public void close() throws IOException {
		db.close();
	}

	public Set<String> getKeys() {
		Set<String> keys = new HashSet<>();
		DBIterator iterator = db.iterator();
		try {
			for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
				String key = asString(iterator.peekNext().getKey());
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

	public Map<String, byte[]> getAllData() {
		Map<String, byte[]> data = new HashMap<>();
		DBIterator iterator = db.iterator();
		try {
			for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
				String key = asString(iterator.peekNext().getKey());
				byte[] value = iterator.peekNext().getValue();
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
}
