package viettel.nfw.social.facebook.pgcrawler.entities;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.hadoop.io.Writable;

/**
 *
 * @author duongth5
 */
public class ProfileStat implements Writable {

	public String id;
	public int visitedToday;
	public int visitedFailToday;
	public int discoveredPostsToday;
	public int discoveredCommentsToday;
	public long lastVisitedTimeToday;
	public long lastDiffVisitedTimeToday;
	public List<Long> listDiffVisitedTimeToday;

	public ProfileStat(String id) {
		this.id = id;
		this.visitedToday = 0;
		this.visitedFailToday = 0;
		this.discoveredPostsToday = 0;
		this.discoveredCommentsToday = 0;
		this.lastVisitedTimeToday = -1;
		this.lastDiffVisitedTimeToday = -1;
		this.listDiffVisitedTimeToday = new ArrayList<>();
	}

	public ProfileStat(ProfileStat other) {
		this.id = other.id;
		this.visitedToday = other.visitedToday;
		this.visitedFailToday = other.visitedFailToday;
		this.discoveredPostsToday = other.discoveredPostsToday;
		this.discoveredCommentsToday = other.discoveredCommentsToday;
		this.lastVisitedTimeToday = other.lastVisitedTimeToday;
		this.lastDiffVisitedTimeToday = other.lastDiffVisitedTimeToday;
		this.listDiffVisitedTimeToday = other.listDiffVisitedTimeToday;
	}

	@Override
	public void write(DataOutput d) throws IOException {
		d.writeUTF(id);
		d.writeInt(visitedToday);
		d.writeInt(visitedFailToday);
		d.writeInt(discoveredPostsToday);
		d.writeInt(discoveredCommentsToday);
		d.writeLong(lastVisitedTimeToday);
		d.writeLong(lastDiffVisitedTimeToday);

		int listDiffSize = listDiffVisitedTimeToday == null ? 0 : listDiffVisitedTimeToday.size();
		d.writeInt(listDiffSize);
		for (int i = 0; i < listDiffSize; i++) {
			d.writeLong(listDiffVisitedTimeToday.get(i));
		}
	}

	@Override
	public void readFields(DataInput di) throws IOException {
		this.id = di.readUTF();
		this.visitedToday = di.readInt();
		this.visitedFailToday = di.readInt();
		this.discoveredPostsToday = di.readInt();
		this.discoveredCommentsToday = di.readInt();
		this.lastVisitedTimeToday = di.readLong();
		this.lastDiffVisitedTimeToday = di.readLong();

		int listDiffSize = di.readInt();
		listDiffVisitedTimeToday = new ArrayList<>();
		for (int i = 0; i < listDiffSize; i++) {
			listDiffVisitedTimeToday.add(di.readLong());
		}
	}
}
