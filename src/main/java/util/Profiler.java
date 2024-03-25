package util;

import checker.Checker;
import checker.IsolationLevel;
import config.Config;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Slf4j
public class Profiler {

    // global vars
    private static final HashMap<Long, Profiler> profilers = new HashMap<>();

    // local vars
    private final HashMap<String, Long> startTime = new HashMap<>();
    private final HashMap<String, Long> totalTime = new HashMap<>();
    private final HashMap<String, Integer> counter = new HashMap<>();
    private final List<String> tags = new ArrayList<>();
    private final HashMap<String, Long> memory = new HashMap<>();

    private static final AtomicLong MaxMemory = new AtomicLong();
    private static final String timeUnit = "ms";
    private static final String memoryUnit = "KB";

    static {
        var t = new Thread(() -> {
            while (true) {
                updateMemory();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private static void updateMemory() {
        var runtime = Runtime.getRuntime();
        var currentMax = runtime.totalMemory() - runtime.freeMemory();
        MaxMemory.updateAndGet(oldMax -> Long.max(oldMax, currentMax));
    }

    public static void updateMemory(long memory) {
        var runtime = Runtime.getRuntime();
        var currentMax = runtime.totalMemory() - runtime.freeMemory();
        MaxMemory.updateAndGet(oldMax -> Long.max(oldMax, currentMax + memory));
    }

    public synchronized static Profiler getInstance() {
        long tid = Thread.currentThread().getId();
        if (!profilers.containsKey(tid)) {
            profilers.put(tid, new Profiler());
        }
        return profilers.get(tid);
    }

    private Profiler() {
    }

    public synchronized void clear() {
        startTime.clear();
        totalTime.clear();
        counter.clear();
    }

    public synchronized void removeTag(String tag) {
        tags.remove(tag);
        startTime.remove(tag);
        totalTime.remove(tag);
        counter.remove(tag);
        memory.remove(tag);
    }

    public synchronized void startTick(String tag) {
        if (!counter.containsKey(tag)) {
            tags.add(tag);
            counter.put(tag, 0);
            totalTime.put(tag, 0L);
        }

        // if we haven't stopped this tick, stop it!!!
        if (!startTime.containsKey(tag)) {
            endTick(tag);
        }

        // start the tick!
        startTime.put(tag, System.currentTimeMillis());
        log.debug("Tag {} stat tick.", tag);
        updateMemory();
    }

    public synchronized long endTick(String tag) {
        if (startTime.containsKey(tag)) {
            long cur_time = System.currentTimeMillis();
            long duration = cur_time - startTime.get(tag);

            // update the counter, total_time and used memory
            totalTime.put(tag, (totalTime.get(tag) + duration));
            counter.put(tag, (counter.get(tag) + 1));
            memory.put(tag, getMaxMemory());
            MaxMemory.set(0);

            // rm the tick
            startTime.remove(tag);
            log.debug("Tag {} end tick after {} ms.", tag, duration);

            updateMemory();
            return duration;
        } else {
            // FIXME: shouldn't be here
            // but do nothing for now.
        }
        return 0;
    }

    public synchronized long getTotalTime(String tag) {
        if (totalTime.containsKey(tag)) {
            return totalTime.get(tag);
        } else {
            return 0;
        }
    }

    public synchronized long getAvgTime(String tag) {
        if (!counter.containsKey(tag) || counter.get(tag) == 0) {
            return 0;
        }
        return getTotalTime(tag) / counter.get(tag);
    }

    public synchronized int getCounter(String tag) {
        return counter.getOrDefault(tag, 0);
    }

    public synchronized long getMemory(String tag) {
        return memory.getOrDefault(tag, 0L);
    }

    public synchronized List<String> getTags() {
        return tags;
    }

    public long getMaxMemory() {
        return MaxMemory.get();
    }

    public synchronized Collection<Pair<String, Long>> getDurations() {
        return tags.stream().map(tag -> Pair.of(tag, totalTime.get(tag))).collect(Collectors.toList());
    }

    public synchronized Collection<Pair<String, Long>> getMemories() {
        return tags.stream().map(tag -> Pair.of(tag, memory.get(tag))).collect(Collectors.toList());
    }

    static String formatTime(Long timeMilliseconds, String unit) {
        double[] scales = { 1, 1000, 1000 * 60, 1000 * 60 * 60 };
        String[] units = { "ms", "s", "min", "hour" };

        for (int i = 0; i < units.length; ++i) {
            if (units[i].equals(unit)) {
                return String.format("%.2f", timeMilliseconds / scales[i]);
            }
        }
        throw new Error("Unknown unit.");
    }

    static String formatMemory(Long memoryBytes, String unit) {
        double[] scales = { 1, 1024, 1024 * 1024, 1024 * 1024 * 1024 };
        String[] units = { "B", "KB", "MB", "GB" };

        for (int i = 0; i < units.length; ++i) {
            if (units[i].equals(unit)) {
                return String.format("%.2f", memoryBytes / scales[i]);
            }
        }
        throw new Error("Unknown unit.");
    }

    @SneakyThrows
    public static void createCSV(String variable, List<Pair<Class<? extends Checker>, IsolationLevel>> checkerIsolationList, List<String> stages) {
        for (var pair : checkerIsolationList) {
            String csvPath = Paths.get(Config.DEFAULT_CURRENT_PATH, pair.getLeft().getName() + "-" + pair.getRight() + "-profile.csv").toString();
            File file = new File(csvPath);
            if (file.exists()) {
                continue;
            }
            FileWriter csvWriter = new FileWriter(file, true);
            String stagesString = String.join(",", stages);
            csvWriter.write(String.format("%s,time(%s),memory(%s),%s\n", variable, timeUnit, memoryUnit, stagesString));
            csvWriter.close();
        }
    }

    @SneakyThrows
    public static void appendToCSV(String value, long time, long memory, Pair<Class<? extends Checker>, IsolationLevel> pair, List<Long> stageTimeList) {
        // append a line to a csv file
        String csvPath = Paths.get(Config.DEFAULT_CURRENT_PATH, pair.getLeft().getName() + "-" + pair.getRight() + "-profile.csv").toString();
        File file = new File(csvPath);
        FileWriter csvWriter = new FileWriter(file, true);
        String stageTimeString = stageTimeList.stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(","));
        csvWriter.write(String.format("%s,%s,%s,%s\n", value, formatTime(time, timeUnit), formatMemory(memory, memoryUnit), stageTimeString));
        csvWriter.close();
    }
}
