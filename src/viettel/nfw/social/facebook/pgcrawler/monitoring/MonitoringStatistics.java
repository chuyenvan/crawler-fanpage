package viettel.nfw.social.facebook.pgcrawler.monitoring;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import net.jcip.annotations.ThreadSafe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.io.Writable;

@ThreadSafe
public class MonitoringStatistics implements Writable {

	private HashMap<String, Object> props = new HashMap<>();

	public synchronized void setProperty(String propName, Object obj) {
		props.put(propName, obj);
	}

	public Object getProperty(String propName) {
		return props.get(propName);
	}

	public synchronized String toString() {
		StringBuilder sb = new StringBuilder();
		List<String> strings = new ArrayList<>();
		for (String key : props.keySet()) {
			strings.add(key + "=" + props.get(key) + "\n");
		}
		Collections.sort(strings);
		for (String str : strings) {
			sb.append(str);
		}
		return sb.toString();
	}

	@Override
	public void write(DataOutput output) throws IOException {
		output.writeInt(props.size());
		for (Map.Entry<String, Object> entrySet : props.entrySet()) {
			String key = entrySet.getKey();
			String value = entrySet.getValue().toString();
			output.writeUTF(key);
			output.writeUTF(value);
		}
	}

	@Override
	public void readFields(DataInput input) throws IOException {
		int size = input.readInt();
		props = new HashMap<>();
		for (int i = 0; i < size; i++) {
			String key = input.readUTF();
			String value = input.readUTF();
			props.put(key, value);
		}
	}
}
