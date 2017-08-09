package viettel.nfw.social.facebook.test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class ProcessOldData {

    private static final Logger LOG = LoggerFactory.getLogger(ProcessOldData.class);

    public static void main(String[] args) {

        List<File> allFiles = new ArrayList<>();
        String dir = "data";
        listFile(dir, allFiles);
        LOG.info("size {}", allFiles.size());
        for (File allFile : allFiles) {
            LOG.info(allFile.getAbsolutePath());
        }
    }

    public static void listFile(String directoryName, List<File> files) {
        File directory = new File(directoryName);
        // get all the files from a directory
        File[] fList = directory.listFiles();
        for (File file : fList) {
            if (file.isFile()) {
                files.add(file);
            } else if (file.isDirectory()) {
                listFile(file.getAbsolutePath(), files);
            }
        }
    }
}
