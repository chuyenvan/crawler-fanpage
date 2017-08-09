package vn.viettel.social.inmemdb;

/**
 *
 * @author thiendn2
 */
public interface InMemList {

    public void add(String element);

    public void addAll(String[] elements);

    public String get(int index);

    public String pop();

    public int getSize();

    public void clear();
}
