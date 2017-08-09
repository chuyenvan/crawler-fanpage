package viettel.nfw.gov;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.SortedSet;
import java.util.concurrent.atomic.AtomicInteger;
import org.nigma.engine.util.Funcs;
import org.redisson.Config;
import org.redisson.Redisson;
import org.redisson.core.RSortedSet;

/**
 *
 * @author duongth5
 */
public class RedissonBMTest {
    public static void main(String[] args) {
        Config config = new Config();
        config.useClusterServers().setScanInterval(2000)
            .addNodeAddress("203.113.152.15:4000")
            .addNodeAddress("203.113.152.15:4001")
            .addNodeAddress("203.113.152.15:4002")
            .addNodeAddress("203.113.152.15:4003")
            .addNodeAddress("203.113.152.15:4004")
            .addNodeAddress("203.113.152.15:4005");
//        config.useSingleServer().setAddress("203.113.152.15:4000");
        
        Redisson redisson = Redisson.create(config);
        //RBatch createBatch = redisson.createBatch();
        RSortedSet<String> sortedSet = redisson.getSortedSet("sortedSet");
        int writeThread = args.length > 0 ? Integer.parseInt(args[0]) : 5;
        int readThread = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        for (int i = 0; i < writeThread; i++) {
            new WriteThread(sortedSet).start();
        }
        for (int i = 0; i < readThread; i++) {
            new ReadThread(sortedSet).start();;
        }

        while (true) {
            System.out.println("Read count = " + readCount.get());
            System.out.println("Write count = " + writeCount.get());
            Funcs.sleep(1000);
        }
    }

    private static final AtomicInteger readCount = new AtomicInteger(0);
    private static final AtomicInteger writeCount = new AtomicInteger(0);
    private static final int BATCH_WRITE_SIZE = 50;

    private static final class WriteThread extends Thread {

        private RSortedSet<String> sortedSet;

        public WriteThread(RSortedSet<String> sortedSet) {
            this.sortedSet = sortedSet;
        }

        @Override
        public void run() {
            Random r = new Random();

            while (true) {
                List<String> batchKeys = new ArrayList<>();
                for (int i = 0; i < BATCH_WRITE_SIZE; i++) {
                    String key = r.nextInt(1000) + "_" + r.nextInt(1000);
                    batchKeys.add(key);
                }
                sortedSet.addAll(batchKeys);
                writeCount.addAndGet(batchKeys.size());
            }
        }
    }

    public static final class ReadThread extends Thread {

        private RSortedSet<String> sortedSet;

        public ReadThread(RSortedSet<String> sortedSet) {
            this.sortedSet = sortedSet;
        }

        @Override
        public void run() {
            while (true) {

                String first = sortedSet.first();
                int score = Integer.parseInt(first.substring(0, first.indexOf("_")));
                SortedSet<String> subSet = sortedSet.subSet(first, (score - 1) + "");
                for (String key : subSet) {
                    sortedSet.removeAsync(key);
                }
                readCount.addAndGet(subSet.size());
            }
        }
    }
}

