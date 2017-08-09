package viettel.nfw.social.utils;

import java.io.File;
import java.io.FilenameFilter;

public class PrefixFilenameFilter implements FilenameFilter {

    private final String prefix;

    public PrefixFilenameFilter(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public boolean accept(File dir, String name) {
        return name.startsWith(prefix);
    }
}
