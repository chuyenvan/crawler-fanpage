package viettel.nfw.social.utils;

import static viettel.nfw.social.utils.Preconditions.checkIsTrue;
import static viettel.nfw.social.utils.Preconditions.checkNotNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.iq80.snappy.SnappyInputStream;
import org.iq80.snappy.SnappyOutputStream;
import org.jetbrains.annotations.Nullable;

public final class IOUtils {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(IOUtils.class);
    private static final String FILE_EXTENSION_GZIP = ".gz";
    private static final String FILE_EXTENSION_SNAPPY = ".snappy";
    private static final String FILE_EXTENSION_BZ2 = ".bz2";

    /**
     * Closes all streams independently.
     */
    public static void closeAll(Closeable... closeables) throws IOException {
        if (closeables != null) {
            closeAll(Arrays.asList(closeables));
        }
    }

    /**
     * Closes all streams independently.
     */
    public static void closeAll(Iterable<? extends Closeable> closeables) throws IOException {
        if (closeables == null) {
            return;
        }
        IOException e2 = null;
        for (Closeable closeable : closeables) {
            if (closeable == null) {
                continue;
            }
            try {
                closeable.close();
            } catch (IOException e) {
                e2 = e;
            }
        }
        if (e2 != null) {
            throw e2;
        }
    }

    public static void closeQuietlyAll(Closeable... closeables) {
        if (closeables != null) {
            closeQuietlyAll(Arrays.asList(closeables));
        }
    }

    public static void closeQuietlyAll(Iterable<? extends Closeable> closeables) {
        if (closeables != null) {
            for (Closeable closeable : closeables) {
                closeQuietly(closeable);
            }
        }
    }

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                LOG.warn("Failed to close : {}", e.getMessage());
            }
        }
    }

    /**
     * Returns a new {@code Closeable} object that closes the given {@code closeable} in its {@code close()} method.
     * <p>
     * It allows to use try-with-resource with an already created resource:<br/>
     * <tt>try (Closeable wrap = wrapCloseable(writer)) {}</tt>
     *
     * @param closeable the closeable to be wrapped or {@code null}
     * @return a new {@code Closeable} that wraps the given one
     */
    public static Closeable wrapCloseable(final @Nullable Closeable closeable) {
        return new Closeable() {

            @Override
            public void close() throws IOException {
                if (closeable != null) {
                    closeable.close();
                }

            }
        };
    }

    public static Closeable wrapDeleter(final @Nullable File file) {
        return new Closeable() {

            @Override
            public void close() throws IOException {
                if (file != null) {
                    FileUtils.deleteRecursively(file);
                }

            }
        };
    }

    public static DataOutputStream createDataOutputStream(File file, int bufferSize) throws IOException {
        checkNotNull(file, "file");
        return new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file), bufferSize));
    }

    public static DataInputStream createDataInputStream(File file, int bufferSize) throws IOException {
        checkNotNull(file, "file");
        return new DataInputStream(new BufferedInputStream(new FileInputStream(file), bufferSize));
    }

    public static void copy(InputStream input, OutputStream output, byte[] buffer) throws IOException {
        // we don't need to fill buffer fully before writing it into output,
        // because input.read does this almost always - TODO check
        int cnt = 0;
        while ((cnt = input.read(buffer)) != -1) {
            output.write(buffer, 0, cnt);
        }
    }

    /**
     * Copies <tt>size</tt> bytes from <tt>input</tt> to <tt>output</tt>.
     */
    public static void copy(InputStream input, OutputStream output, long size, byte[] buffer) throws IOException {
        checkNotNull(input, "input");
        checkNotNull(output, "output");
        checkIsTrue(size > 0, "size <= 0");
        checkNotNull(buffer, "buffer");
        int bufferLen = buffer.length;
        checkIsTrue(bufferLen > 0, "buffer.length == 0");

        while (size > bufferLen) {
            readFully(input, buffer, 0, bufferLen);
            output.write(buffer, 0, bufferLen);
            size -= bufferLen;
        }
        if (size > 0) {
            bufferLen = (int) size;
            readFully(input, buffer, 0, bufferLen);
            output.write(buffer, 0, bufferLen);
        }

    }

    /**
     * Copies <tt>size</tt> bytes from <tt>input</tt> to <tt>output</tt>.
     */
    public static void copy(DataInput input, DataOutput output, long size, byte[] buffer) throws IOException {
        checkNotNull(input, "input");
        checkNotNull(output, "output");
        checkIsTrue(size > 0, "size <= 0");
        checkNotNull(buffer, "buffer");
        int bufferLen = buffer.length;
        checkIsTrue(bufferLen > 0, "buffer.length == 0");

        while (size > bufferLen) {
            input.readFully(buffer, 0, bufferLen);
            output.write(buffer, 0, bufferLen);
            size -= bufferLen;
        }
        if (size > 0) {
            bufferLen = (int) size;
            input.readFully(buffer, 0, bufferLen);
            output.write(buffer, 0, bufferLen);
        }

    }

    /**
     * Reads exactly <tt>len</tt> bytes from the <tt>input</tt> stream into an array of bytes. The first byte read is
     * stored into element
     * <tt>buffer[off]</tt>, the next one into <tt>buffer[off + 1]</tt>, and so on.
     */
    public static void readFully(InputStream input, byte[] buffer, int off, int len) throws IOException {
        while (len > 0) {
            int cnt = input.read(buffer, off, len);
            if (cnt == -1) {
                throw new EOFException();
            }
            off += cnt;
            len -= cnt;
        }
    }

    public static InputStream openFileInputStream(File file, int bufferSize) throws IOException {
        checkNotNull(file, "file");
        checkIsTrue(bufferSize > 0, "not valid buffer size");
        return wrapInputStream(new FileInputStream(file), file.getName(), bufferSize);
    }

    public static InputStream openResourceInputStream(String path, int bufferSize) throws IOException {
        checkNotNull(path, "path");
        checkIsTrue(bufferSize > 0, "not valid buffer size");
        InputStream input = IOUtils.class.getResourceAsStream(path);
        if (input == null) {
            throw new FileNotFoundException("resource:" + path);
        }
        return wrapInputStream(input, path, bufferSize);
    }

    public static InputStream wrapInputStream(InputStream input, String name, int bufferSize) throws IOException {
        if (name.endsWith(FILE_EXTENSION_GZIP)) {
            input = new GZIPInputStream(input, bufferSize);
        } else if (name.endsWith(FILE_EXTENSION_SNAPPY)) {
            input = new SnappyInputStream(input, false);
        } else if (name.endsWith(FILE_EXTENSION_BZ2)) {
            input = new BZip2CompressorInputStream(input, false);
        } else {
            input = new BufferedInputStream(input, bufferSize);
        }
        return input;
    }

    public static OutputStream openFileOutputStream(File file, int bufferSize) throws IOException {
        checkNotNull(file, "file");
        checkIsTrue(bufferSize > 0, "not valid buffer size");

        OutputStream output = new FileOutputStream(file);
        String fileName = file.getName();
        if (fileName.endsWith(FILE_EXTENSION_GZIP)) {
            output = new GZIPOutputStream(output, bufferSize);
        } else if (fileName.endsWith(FILE_EXTENSION_SNAPPY)) {
            output = new SnappyOutputStream(new BufferedOutputStream(output, bufferSize));
        } else if (fileName.endsWith(FILE_EXTENSION_BZ2)) {
            output = new BZip2CompressorOutputStream(output, 9);
        } else {
            output = new BufferedOutputStream(output, bufferSize);
        }
        return output;
    }

    private IOUtils() {
    }

}
