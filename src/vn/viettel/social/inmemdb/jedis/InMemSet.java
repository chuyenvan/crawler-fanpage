package vn.viettel.social.inmemdb;

import java.util.Collection;

/**
 *
 * @author Duong
 */
public interface InMemSet {

    public void add(String element);

    public void addAll(String[] elements);

    public boolean contains(String element);

    public void remove(String element);

    public void remove(String[] elements);

    public Collection<String> getAll();

    public long getSize();
    
    public void destroy();
}
