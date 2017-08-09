package viettel.nfw.social.utils;

import org.iq80.snappy.SnappyInputStream;
import org.iq80.snappy.SnappyOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class FileUtils {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(FileUtils.class);
    public static final Charset UTF_8 = Charset.forName("UTF-8");

    /**
     * Create a directory if and only if it doesn't exist yet. If the directory can't be created, an IOException will be
     * thrown.
     *
     * @param file
     * @throws IOException
     */
    public static void mkdir(@NotNull File file) throws IOException {
        if (!file.mkdir()) {
            throw new IOException("Can't create directory '" + file + "'");
        }
    }

    /**
     * Create a directory if it doesn't exist yet. If the given <code>file</code> exists but it's not a directory, an
     * IOException will be thrown. If a directory with the given name doesn't exist and can't be created, an IOException
     * will be thrown.
     *
     * @param file
     * @throws IOException
     */
    public static void mkdirIfNotExist(@NotNull File file) throws IOException {
        if (file.exists()) {
            if (!file.isDirectory()) {
                throw new IOException("Can't create directory '" + file + "' : exists && !isDirectory");
            }
        } else if (!file.mkdir()) {
            throw new IOException("Can't create directory '" + file + "'");
        }
    }

    public static InputStream wrapWithSnappy(InputStream inputStream, boolean useSnappy)
            throws IOException {
        if (useSnappy) {
            return new SnappyInputStream(inputStream, true);
        } else {
            return inputStream;
        }
    }

    public static OutputStream wrapWithSnappy(OutputStream outputStream, boolean useSnappy)
            throws IOException {
        if (useSnappy) {
            return new SnappyOutputStream(outputStream);
        } else {
            return outputStream;
        }
    }

    @NotNull
    public static List<File> sortDescendingSize(@NotNull File[] dirs) {
        List<Pair<File, Long>> files = new ArrayList<>();
        for (File f : dirs) {
            files.add(new Pair<>(f, org.apache.commons.io.FileUtils.sizeOfDirectory(f)));
        }
        Collections.sort(files, new Comparator<Pair<File, Long>>() {
            @Override
            public int compare(Pair<File, Long> o1, Pair<File, Long> o2) {
                return -1 * o1.second.compareTo(o2.second);
            }
        });
        List<File> sorted = new ArrayList<>(files.size());
        for (Pair<File, Long> pair : files) {
            sorted.add(pair.first);
        }
        return sorted;
    }

    /**
     * After a successful call, a directory with the given pathname will exist. Uses {@link File#mkdirs()} to create the
     * directory, if it doesn't exist yet.
     */
    public static void mkdirs(@NotNull File file) throws IOException {
        if (file.exists()) {
            if (!file.isDirectory()) {
                throw new IOException(String.format("failed to mkdirs '%s' : exists && !isDirectory", file));
            }
        } else if (!file.mkdirs()) {
            throw new IOException(String.format("failed to mkdirs '%s'", file));
        }
    }

    /**
     * Creates new file with a given full file name. If a file with the same path & name exists then it will be deleted
     * first
     *
     * @param file full file name
     * @throws IOException if delete or create file operation fails
     */
    public static void createOrReplaceFile(File file) throws IOException {
        if (file.exists()) {
            LOG.warn("File exists already: {}", file.getAbsolutePath());
            if (!file.delete()) {
                throw new IOException("Failed to delte file: " + file.getAbsolutePath());
            }
        }

        if (!file.createNewFile()) {
            throw new IOException("Failed to create new file: " + file.getAbsolutePath());
        }
    }

    public static void moveToDirectory(@NotNull File from, @NotNull File dirTo) throws IOException {
        assertDirectoryExists(dirTo);
        File to = new File(dirTo, from.getName());
        renameTo(from, to);
    }

    public static void renameTo(@NotNull File from, @NotNull File to) throws IOException {
        assertExists(from);
        assertNotExist(to);
        if (!from.renameTo(to)) {
            throw new IOException(String.format("failed to rename '%s' to '%s'", from, to));
        }
    }

    /**
     * After a successful call, a file (directory) with the given pathname won't exist. The directory must be empty in
     * order to be deleted.
     *
     * @throws IOException if operation failed
     */
    public static void delete(@NotNull File file) throws IOException {
        if (file.exists() && !file.delete()) {
            if (file.exists()) {
                throw new IOException(String.format("failed to delete '%s'", file));
            }
        }
    }

    /**
     * Tries to delete a file (directory) with the given pathname and logs failed attempts. The directory must be empty
     * in order to be deleted.
     */
    public static void deleteQuietly(File file) {
        try {
            delete(file);
        } catch (IOException e) {
            LOG.warn(e.getMessage());
        }
    }

    /**
     * After a successful call, a file (directory) with the given pathname won't exist. This method also works with
     * non-empty directories.
     *
     * @throws IOException if operation failed
     */
    public static void deleteRecursively(@NotNull File file) throws IOException {
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] childFiles = file.listFiles();
                if (childFiles != null) {
                    for (File childFile : childFiles) {
                        deleteRecursively(childFile);
                    }
                }
            }
            delete(file);
        }
    }

    /**
     * After a successful call, a file (directory) with the given pathname won't exist. This method also works with
     * non-empty directories.
     */
    public static void deleteRecursivelyQuietly(@NotNull File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] childFiles = file.listFiles();
                if (childFiles != null) {
                    for (File childFile : childFiles) {
                        deleteRecursivelyQuietly(childFile);
                    }
                }
            }
            deleteQuietly(file);
        }
    }

    /**
     * Throws an exception if a file (directory) with the given pathname doesn't exist.
     *
     * @throws IOException if file doesn't exist
     */
    public static void assertExists(@NotNull File file) throws IOException {
        if (!file.exists()) {
            throw new IOException(String.format("file '%s' doesn't exist", file));
        }
    }

    /**
     * Throws an exception if the given pathname doesn't denote an existing normal file.
     *
     * @throws IOException if file doesn't exist
     */
    public static void assertFileExists(@NotNull File file) throws IOException {
        if (!file.exists()) {
            throw new IOException(String.format("file '%s' doesn't exist", file));
        }
        if (!file.isFile()) {
            throw new IOException(String.format("file '%s' isn't a normal file", file));
        }
    }

    public static void removeFilesWithPrefix(@NotNull File dir, @NotNull String prefix) throws IOException {
        for (File file : getFilesWithPrefix(dir, prefix)) {
            if (file.isFile()) {
                if (!file.delete()) {
                    throw new IOException("Can't remove " + file);
                }
            }
        }
    }

    @NotNull
    public static List<File> getFilesWithPrefix(@NotNull File dir, @NotNull String prefix) throws IOException {
        File[] files = dir.listFiles(new PrefixFilenameFilter(prefix));
        if (files != null) {
            return Arrays.asList(files);
        } else {
            throw new IOException("Can't obtain list of files in " + dir);
        }
    }

    @NotNull
    public static String read(@NotNull File file) throws IOException {
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        StringBuilder stringBuilder = new StringBuilder();
        char[] buffer = new char[1024 * 1024];
        int size;
        while ((size = bufferedReader.read(buffer)) != -1) {
            stringBuilder.append(buffer, 0, size);
        }
        return stringBuilder.toString();
    }

    /**
     * Method reads last line from file if file exists and not empty.
     *
     * @param path file to read
     * @return last line in file, null in case of empty file
     * @throws IOException
     */
    @Nullable
    public static String readLastLine(@NotNull Path path) throws IOException {
        String prevLine = null;
        try (BufferedReader br = Files.newBufferedReader(path, Charset.forName("UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                prevLine = line;
            }
        }

        return prevLine;
    }

    @NotNull
    public static List<String> readList(@NotNull File file) throws IOException {
        List<String> res = new ArrayList<>();
        addLinesToCollection(file, res);
        return res;
    }

    public static List<String> readList(@NotNull InputStream is, boolean addEmptyLine) throws IOException {
        List<String> res = new ArrayList<>();
        addLinesToCollection(is, res, addEmptyLine);
        return res;
    }

    public static List<String> readList(@NotNull InputStream is) throws IOException {
        return readList(is, true);
    }

    public static void addLinesToCollection(
            @NotNull File file, @NotNull Collection<String> collection)
            throws IOException {
        addLinesToCollection(new FileInputStream(file), collection);
    }

    public static void addLinesToCollection(
            @NotNull InputStream is, @NotNull Collection<String> collection) throws IOException {
        addLinesToCollection(is, collection, true);
    }

    public static void addLinesToCollection(
            @NotNull InputStream is, @NotNull Collection<String> collection, boolean addEmptyLine)
            throws IOException {
        String line;
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(is))) {
            while ((line = bufferedReader.readLine()) != null) {
                if ((!addEmptyLine) && line.trim().isEmpty()) {
                    continue;
                }
                collection.add(line);
            }
        } catch (EOFException ignored) {
        }
    }

    @NotNull
    public static List<Long> readLongs(@NotNull File file) throws IOException {
        String data = FileUtils.read(file);
        List<Long> values = new ArrayList<>();
        for (String line : data.split("\n")) {
            values.add(Long.valueOf(line));
        }
        return values;
    }

    @Deprecated
    /**
     * Please, use Files.write(file.toPath(), value.getBytes()); instead of this method.
     */
    public static void write(@NotNull File file, @NotNull String value) throws IOException {
        Files.write(file.toPath(), value.getBytes());
    }

    @NotNull
    public static BufferedWriter write(@NotNull BufferedWriter bw, @NotNull String... values) throws IOException {
        for (String val : values) {
            bw.append(val);
        }
        return bw;
    }

    @NotNull
    public static BufferedWriter writeln(@NotNull BufferedWriter bw, @NotNull String... values) throws IOException {
        write(bw, values).newLine();
        return bw;
    }

    @NotNull
    public static void write(File output, Collection<String> lines) throws FileNotFoundException {
        try (PrintWriter out = new PrintWriter(new FileOutputStream(output))) {
            for (String line : lines) {
                out.println(line);
            }
            out.close();
        }
    }

    /**
     * Throws an exception if the given pathname doesn't denote an existing directory.
     *
     * @throws IOException if file doesn't exist
     */
    public static void assertDirectoryExists(@NotNull File file) throws IOException {
        if (!file.exists()) {
            throw new IOException(String.format("file '%s' doesn't exist", file));
        }
        if (!file.isDirectory()) {
            throw new IOException(String.format("file '%s' isn't a directory", file));
        }
    }

    /**
     * Throws an exception if a file (directory) with the given pathname exists.
     *
     * @throws IOException if file exist
     */
    public static void assertNotExist(@NotNull File file) throws IOException {
        if (file.exists()) {
            throw new IOException(String.format("file '%s' exists", file));
        }
    }

    public static void replaceFile(File src, File dest) throws IOException {
        delete(dest);
        renameTo(src, dest);
    }

    /**
     * Calculates the checksum file's pathname for a hadoop SequenceFile denoted by the given pathname.
     */
    public static File getSequenceFileCrcFile(@NotNull File file) {
        return new File(file.getParentFile(), "." + file.getName() + ".crc");

    }

    public static void copyFileIfExists(File from, File to, int bufferSize) throws IOException {
        if (from.exists()) {
            copyFile(from, to, bufferSize);
        }
    }

    public static void linkOrCopyFile(File from, File to, byte[] buffer) throws IOException {
        try {
            Files.createLink(to.toPath(), from.toPath());
        } catch (IOException ex) {
            copyFile(from, to, buffer);
        }
    }

    public static void linkOrCopyFile(File from, File to, int bufferSize) throws IOException {
        try {
            Files.createLink(to.toPath(), from.toPath());
        } catch (IOException ex) {
            copyFile(from, to, bufferSize);
        }
    }

    public static void copyFile(@NotNull File from, @NotNull File to, int bufferSize) throws IOException {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize <= 0");
        }
        assertFileExists(from);
        assertNotExist(to);
        copyFile(from, to, new byte[bufferSize]);
    }

    private static void copyFile(File from, File to, byte[] buffer) throws IOException {
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            input = new FileInputStream(from);
            output = new FileOutputStream(to);
            IOUtils.copy(input, output, buffer);
        } finally {
            IOUtils.closeAll(input, output);
        }
    }

    public static void linkOrCopyRecursively(File from, File to, int bufferSize) throws IOException {
        assertExists(from);
        assertNotExist(to);
        linkOrCopyRecursively(from, to, new byte[bufferSize]);
    }

    private static void linkOrCopyRecursively(File from, File to, byte[] buffer) throws IOException {
        if (from.isDirectory()) {
            FileUtils.mkdirIfNotExist(to);
            File[] childFiles = from.listFiles();
            if (childFiles != null) {
                for (File childFile : childFiles) {
                    linkOrCopyRecursively(childFile, new File(to, childFile.getName()), buffer);
                }
            }
        } else {
            linkOrCopyFile(from, to, buffer);
        }
    }

    public static void copyRecursively(@NotNull File from, @NotNull File to, int bufferSize) throws IOException {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize <= 0");
        }
        assertExists(from);
        assertNotExist(to);
        copyRecursively(from, to, new byte[bufferSize]);
    }

    /**
     * Copies fromDir/* to toDir/ recursively. Overwrites existing files.
     *
     * @throws IOException
     */
    public static void copyDirectoryContents(File fromDir, File toDir, int bufferSize) throws IOException {
        if (fromDir == null) {
            throw new IllegalArgumentException("fromDir is null");
        }
        if (toDir == null) {
            throw new IllegalArgumentException("toDir is null");
        }
        if (!fromDir.equals(toDir)) {
            if (bufferSize <= 0) {
                throw new IllegalArgumentException("bufferSize <= 0");
            }
            assertDirectoryExists(fromDir);
            mkdirs(toDir);
            copyRecursively(fromDir, toDir, new byte[bufferSize]);
        }
    }

    private static void copyRecursively(File from, File to, byte[] buffer) throws IOException {
        if (from.isDirectory()) {
            FileUtils.mkdirIfNotExist(to);
            File[] childFiles = from.listFiles();
            if (childFiles != null) {
                for (File childFile : childFiles) {
                    copyRecursively(childFile, new File(to, childFile.getName()), buffer);
                }
            }
        } else {
            copyFile(from, to, buffer);
        }
    }

    private FileUtils() {
    }

    @NotNull
    public static File removeSuffix(@NotNull File file, @NotNull String suffix) throws IOException {
        return replaceSuffix(file, suffix, "");
    }

    @NotNull
    public static File replaceSuffix(@NotNull File file, @NotNull String oldSuffix, @NotNull String newSuffix)
            throws IOException {
        if (oldSuffix.isEmpty()) {
            throw new IllegalArgumentException("Suffix to remove is empty. File: " + file.getCanonicalPath());
        }
        String path = file.getCanonicalPath();
        if (!path.endsWith(oldSuffix)) {
            throw new IllegalArgumentException(
                    "File doesn't ends with " + oldSuffix + ". File: " + file.getCanonicalPath());
        }

        if (oldSuffix.equals(newSuffix)) {
            return file;
        }

        path = path.substring(0, path.length() - oldSuffix.length());
        File renamed = new File(path + newSuffix);
        FileUtils.renameTo(file, renamed);
        return renamed;
    }

    @NotNull
    public static File addSuffix(@NotNull File file, @NotNull String suffix) throws IOException {
        if (suffix.isEmpty()) {
            throw new IllegalArgumentException("Suffix to remove is empty. File: " + file.getCanonicalPath());
        }
        String path = file.getCanonicalPath();
        if (path.endsWith(suffix)) {
            throw new IllegalArgumentException("File ends with " + suffix
                    + " already. File: " + file.getCanonicalPath());
        }
        File renamed = new File(path + suffix);
        FileUtils.renameTo(file, renamed);
        return renamed;
    }

    public static boolean writeTo(@NotNull BufferedWriter br, boolean first, @NotNull String line)
            throws IOException {
        if (first) {
            first = false;
        } else {
            br.newLine();
        }
        br.write(line);

        return first;
    }

    public static void assertNotInFolder(@NotNull String prefix, @NotNull File... folders)
            throws IOException {
        for (File folder : folders) {
            if (!FileUtils.getFilesWithPrefix(folder, prefix).isEmpty()) {
                throw new IllegalStateException("Folder " + folder + " contains files with prohibited prefix " + prefix);
            }
        }
    }

    public static void assertNotInFolder(@NotNull FilenameFilter filterToProhibit, @NotNull File... folders)
            throws IOException {
        for (File folder : folders) {
            String[] names = folder.list(filterToProhibit);
            if (names == null) {
                throw new IOException(folder + " is not a directory");
            } else {
                if (names.length > 0) {
                    throw new IOException(folder + " contains prohibited files");
                }
            }
        }
    }

    public static class PermissionUpdater extends SimpleFileVisitor<Path> {

        protected final Set<PosixFilePermission> permissions;

        public PermissionUpdater(@NotNull Set<PosixFilePermission> permissions) {
            this.permissions = permissions;
        }

        @NotNull
        public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
            Files.setPosixFilePermissions(file, permissions);
            return FileVisitResult.CONTINUE;
        }

        @NotNull
        public FileVisitResult postVisitDirectory(@NotNull Path dir, @NotNull IOException exc) throws IOException {
            Files.setPosixFilePermissions(dir, permissions);
            return FileVisitResult.CONTINUE;
        }
    }

    public static void copyIfSourceExists(@NotNull Path source, @NotNull Path destination) throws IOException {
        if (Files.exists(source)) {
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    @NotNull
    public static Path getDestination(@NotNull Path symlink) throws IOException {
        if (Files.exists(symlink)) {
            if (Files.isSymbolicLink(symlink)) {
                return Files.readSymbolicLink(symlink);
            } else {
                // For debug only
                //throw new IOException(symlink.toString() + " should be symbolic link.");
                LOG.warn("Need to debug : -- " + symlink + " is not SymbolicLink");
                return symlink;
            }
        } else {
            throw new IOException(symlink.toString() + " doesn't exists.");
        }
    }

    public static class GroupOwnerUpdater extends SimpleFileVisitor<Path> {

        private final GroupPrincipal groupOwner;

        public GroupOwnerUpdater(@NotNull String groupName) throws IOException {
            this.groupOwner = Paths.get(".").getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByGroupName(groupName);
        }

        @NotNull
        public FileVisitResult visitFile(@NotNull Path filePath, @NotNull BasicFileAttributes attrs)
                throws IOException {
            Files.getFileAttributeView(filePath, PosixFileAttributeView.class).setGroup(groupOwner);
            return FileVisitResult.CONTINUE;
        }

        @NotNull
        public FileVisitResult postVisitDirectory(@NotNull Path directoryPath, @NotNull IOException exc)
                throws IOException {
            Files.getFileAttributeView(directoryPath, PosixFileAttributeView.class).setGroup(groupOwner);
            return FileVisitResult.CONTINUE;
        }
    }

    public static boolean writeln(@NotNull BufferedWriter br, @NotNull String line, boolean firstValue)
            throws IOException {
        if (!firstValue) {
            br.newLine();
        }
        br.write(line);
        return false;
    }

    public static void writeObject2File(File file, Object o, boolean gz) throws IOException {
        OutputStream os = new FileOutputStream(file);
        if (gz) {
            os = new GZIPOutputStream(os, BUFFER_SIZE);
        }
        try (ObjectOutputStream oos = new ObjectOutputStream(os)) {
            oos.writeObject(o);
        }
    }
    private static final int BUFFER_SIZE = 10 * 1024 * 1024;

    public static Object readObjectFromFile(File file, boolean gz) throws FileNotFoundException, IOException, ClassNotFoundException {
        InputStream is = new FileInputStream(file);
        if (gz) {
            is = new GZIPInputStream(is, BUFFER_SIZE);
        }
        Object o;
        try (ObjectInputStream ois = new ObjectInputStream(is)) {
            o = ois.readObject();
        }
        return o;
    }

    public static void createGZFile(File input, File output, boolean deleteAfterCreated) throws FileNotFoundException, IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(input)));
        PrintWriter out = new PrintWriter(new GZIPOutputStream(new FileOutputStream(output)));
        for (String line = reader.readLine(); line != null; line = reader.readLine()) {
            out.println(line);
        }
        out.close();
        reader.close();
        if (deleteAfterCreated) {
            input.delete();
        }
    }

    public static PrintWriter createGzOutputStreamFromFile(File output) throws IOException {
        return new PrintWriter(new GZIPOutputStream(new FileOutputStream(output), BUFFER_SIZE));
    }

    public static BufferedReader createGzInputStreamFromFile(File input) throws IOException {
        return new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(input), BUFFER_SIZE)));
    }
}
