package vn.viettel.social.inmemdb;

/**
 *
 * @author duongth5
 */
public interface InMemHash {

    public void add(String field, String value);
    
    public String get(String field);
    
    public void remove(String field);
    
    public void remove(String[] fields);

    public boolean contains(String field);

    public void destroy();
    
    public long getSize();
}
