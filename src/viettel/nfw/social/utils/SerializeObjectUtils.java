package viettel.nfw.social.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.apache.hadoop.io.Writable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Duong
 */
public class SerializeObjectUtils {

	private static final Logger LOG = LoggerFactory.getLogger(SerializeObjectUtils.class);

	public static byte[] serializeObjectToByteArrayUsingGzip(Object o) {
		return serializeObjectToByteArray(o, true);
	}

	public static byte[] serializeObjectToByteArray(Object o, boolean useGzip) {
		ObjectOutputStream oos = null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			if (useGzip) {
				oos = new ObjectOutputStream(new GZIPOutputStream(baos));
			} else {
				oos = new ObjectOutputStream(baos);
			}
			oos.writeObject(o);
			oos.close();
			return baos.toByteArray();
		} catch (IOException ex) {
			LOG.error("Error in serializing object", ex);
			return null;
		} finally {
			if (oos != null) {
				try {
					oos.close();
				} catch (IOException ex) {
				}
			}
		}
	}

	public static Object readingObjectFromByteArrayUsingGzip(byte[] bytes) {
		return readingObjectFromByteArray(bytes, true);

	}

	public static Object readingObjectFromByteArray(byte[] bytes, boolean usingGzip) {
		ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		ObjectInputStream ois = null;
		try {
			if (usingGzip) {
				ois = new ObjectInputStream(new GZIPInputStream(bais));
			} else {
				ois = new ObjectInputStream(bais);
			}

			Object o = ois.readObject();
			ois.close();
			return o;
		} catch (Exception ex) {
			LOG.error("Error in readingObjectFromByteArray", ex);
			return null;
		} finally {
			if (ois != null) {
				try {
					ois.close();
				} catch (IOException ex) {
				}
			}
		}
	}

	public static byte[] fromWriteableObjecToByteArray(Writable ob) {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		try {
			try (DataOutputStream dao = new DataOutputStream(bao)) {
				ob.write(dao);
			}
			bao.close();
			return bao.toByteArray();
		} catch (IOException ex) {
			LOG.error("Error in fromWriteableObjecToByteArray", ex);
			return null;
		} finally {
			if (bao != null) {
				try {
					bao.close();
				} catch (IOException ex) {
				}
			}
		}
	}

	public static byte[] fromWriteableObjecToByteArrayWithGzip(Writable ob) {
		ByteArrayOutputStream bao = new ByteArrayOutputStream();
		try {
			try (DataOutputStream dao = new DataOutputStream(new GZIPOutputStream(bao))) {
				ob.write(dao);
			}
			bao.close();
			return bao.toByteArray();
		} catch (IOException ex) {
			LOG.error("Error in fromWriteableObjecToByteArrayWithGzip", ex);
			return null;
		} finally {
			if (bao != null) {
				try {
					bao.close();
				} catch (IOException ex) {
				}
			}
		}
	}
}
