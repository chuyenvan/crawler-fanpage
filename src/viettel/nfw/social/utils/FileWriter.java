package viettel.nfw.social.utils;

/**
 *
 * @author duongth5
 */
public interface FileWriter {

    FileWriter append(CharSequence seq);

    FileWriter indent(int indent);

    void close();
}
