package vn.viettel.social.inmemdb;

import java.util.Collection;
import java.util.List;
import org.nigma.engine.web.Pair;

/**
 *
 *
 * In Mem Queue Getter
 *
 * @author thiendn2
 */
public interface InMemSortedSet {

    public Collection<String> getTop(int numUrl, boolean remove);

    public Collection<String> getTop(int numUrl);

    public void addToQueue(List<Pair<String, Double>> keyAndScores);

    public void addToQueue(String key, Double score);

    public boolean contains(String key);

    public long remove(String... keys);

    public Collection<String> poll(int numUrl, double deltaScore);

    public void destroy();
}
