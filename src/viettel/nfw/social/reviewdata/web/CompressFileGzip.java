package viettel.nfw.social.reviewdata.web;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author duongth5
 */
public class CompressFileGzip {

    private static final Logger LOG = LoggerFactory.getLogger(CompressFileGzip.class);

    public static void main(String[] args) {

//        String source_filepath = "test.txt";
//        String gzip_filepath = "test.txt.gz";
//        String decopressed_filepath = "test2.txt";
//
//        CompressFileGzip gZipFile = new CompressFileGzip();
//        gZipFile.gzipFile(source_filepath, gzip_filepath);
//        gZipFile.unGunzipFile(gzip_filepath, decopressed_filepath);
//        String ret = gZipFile.unGunzipFile(gzip_filepath);
//        LOG.info(ret);
        String filename = "D:\\git\\review-data\\sample\\fromThienDN2\\-852455374_1429869748098.html.gz";
        CompressFileGzip gZipFile = new CompressFileGzip();
        // gZipFile.unGunzipFile(filename, "152014945_1429869737242.html");
        String ret = gZipFile.unGunzipFile(filename);
        Document doc = Jsoup.parse(ret);
        LOG.info(doc.title());
        Element el = doc.getElementById("newsFeedHeading");
        LOG.info(el.text());
        Elements els = doc.getElementsByAttributeValueStarting("id", "tl_unit_");
        for (Element el1 : els) {
            LOG.info("@#@#@#@#@#@#");
            // find status
            Elements userContents = el1.select(".userContent > p");
            if (!userContents.isEmpty()) {
                LOG.info("status - {}", userContents.get(0).text());
            }

            // find share links
            // find comments
            Elements comments = el1.getElementsByClass("UFICommentBody");
            if (!comments.isEmpty()) {
                for (Element comment : comments) {
                    LOG.info("comment - {}", comment.text());
                }
            }
        }
    }

    public void gzipFile(String source_filepath, String destinaton_zip_filepath) {

        byte[] buffer = new byte[1024];

        try {

            FileOutputStream fileOutputStream = new FileOutputStream(destinaton_zip_filepath);

            try (GZIPOutputStream gzipOuputStream = new GZIPOutputStream(fileOutputStream)) {
                try (FileInputStream fileInput = new FileInputStream(source_filepath)) {
                    int bytes_read;

                    while ((bytes_read = fileInput.read(buffer)) > 0) {
                        gzipOuputStream.write(buffer, 0, bytes_read);
                    }
                }
                gzipOuputStream.finish();
            }

            System.out.println("The file was compressed successfully!");

        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    public void unGunzipFile(String compressedFile, String decompressedFile) {

        byte[] buffer = new byte[1024];

        try {

            FileInputStream fileIn = new FileInputStream(compressedFile);

            FileOutputStream fileOutputStream;
            try (GZIPInputStream gZIPInputStream = new GZIPInputStream(fileIn)) {
                fileOutputStream = new FileOutputStream(decompressedFile);
                int bytes_read;
                while ((bytes_read = gZIPInputStream.read(buffer)) > 0) {

                    fileOutputStream.write(buffer, 0, bytes_read);
                }
            }
            fileOutputStream.close();

            System.out.println("The file was decompressed successfully!");

        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }
    }

    public String unGunzipFile(String compressedFile) {
        String ret = "";

        try {
            FileInputStream fileIn = new FileInputStream(compressedFile);
            StringBuilder sb;
            try (GZIPInputStream gZIPInputStream = new GZIPInputStream(fileIn)) {
                InputStreamReader inSR = new InputStreamReader(gZIPInputStream, "UTF-8");
                BufferedReader br = new BufferedReader(inSR);
                sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }

            ret = sb.toString();
            System.out.println("The file was decompressed successfully!");

        } catch (IOException ex) {
            LOG.error(ex.getMessage(), ex);
        }

        return ret;
    }
}
