package viettel.nfw.social.facebook.pgcrawler.database;

import com.viettel.fix.Pair;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author duongth5
 */
public interface InMemSortedSet {

	public Collection<String> poll(int numUrl, boolean remove);

	public Collection<String> poll(int numUrl);

	public void addToQueue(List<Pair<String, Double>> keyAndScores);

	public void addToQueue(String key, Double score);

	public boolean contains(String key);

	public long remove(String... keys);

	public Collection<String> poll(int numUrl, double deltaScore);
}
