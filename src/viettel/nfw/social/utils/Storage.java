package viettel.nfw.social.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 *
 * @author chuyennd
 */
public class Storage {

    public static class Writer {

        private final ObjectOutputStream oos;

        public Writer(File output) throws FileNotFoundException, IOException {
            oos = new ObjectOutputStream(new FileOutputStream(output));
        }

        public void write(Object o) throws IOException {
            oos.writeObject(o);
        }

        public void close() throws IOException {
            oos.close();
        }
    }

    public static class Reader {

        private final ObjectInputStream ois;

        public Reader(File input) throws IOException {
            ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(input)));
        }

        public Object next() {
            try {
                return ois.readObject();
            } catch (IOException | ClassNotFoundException ex) {
                return null;
            }
        }

        public void close() throws IOException {
            ois.close();
        }
    }
}
