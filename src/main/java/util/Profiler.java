/*
MIT License

Copyright (c) 2020 DBCobra

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/
package util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.commons.lang3.tuple.Pair;

public class Profiler {

    // global vars
    private static final HashMap<Long, Profiler> profilers = new HashMap<Long, Profiler>();

    // local vars
    private final HashMap<String, Long> start_time = new HashMap<String, Long>();
    private final HashMap<String, Long> total_time = new HashMap<String, Long>();
    private final HashMap<String, Integer> counter = new HashMap<String, Integer>();
    private final List<String> tags = new ArrayList<>();
    private final HashMap<String, Long> memory = new HashMap<String, Long>();

    private static final AtomicLong max_memory = new AtomicLong();

    static {
        new Thread(() -> {
            while (true) {
                updateMemory();
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        });
    }

    private static void updateMemory() {
        var runtime = Runtime.getRuntime();
        var currentMax = runtime.totalMemory() - runtime.freeMemory();
        max_memory.updateAndGet(oldMax -> Long.max(oldMax, currentMax));
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
        start_time.clear();
        total_time.clear();
        counter.clear();
    }

    public synchronized void startTick(String tag) {
        if (!counter.containsKey(tag)) {
            tags.add(tag);
            counter.put(tag, 0);
            total_time.put(tag, 0L);
        }

        // if we haven't stop this tick, stop it!!!
        if (!start_time.containsKey(tag)) {
            endTick(tag);
        }

        // start the tick!
        start_time.put(tag, System.currentTimeMillis());
        updateMemory();
    }

    public synchronized void endTick(String tag) {
        if (start_time.containsKey(tag)) {
            long cur_time = System.currentTimeMillis();
            long duration = cur_time - start_time.get(tag);
            long cur_memory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // update the counter, total_time and used memory
            total_time.put(tag, (total_time.get(tag) + duration));
            counter.put(tag, (counter.get(tag) + 1));
            memory.put(tag, cur_memory);

            // rm the tick
            start_time.remove(tag);
        } else {
            // FIXME: shouldn't be here
            // but do nothing for now.
        }
        updateMemory();
    }

    public synchronized long getTime(String tag) {
        if (total_time.containsKey(tag)) {
            return total_time.get(tag);
        } else {
            return 0;
        }
    }

    public synchronized int getCounter(String tag) {
        if (counter.containsKey(tag)) {
            return counter.get(tag);
        } else {
            return 0;
        }
    }

    public long getMaxMemory() {
        return max_memory.get();
    }

    public synchronized Collection<Pair<String, Long>> getDurations() {
        return tags.stream().map(tag -> Pair.of(tag, total_time.get(tag))).collect(Collectors.toList());
    }

    public synchronized Collection<Pair<String, Long>> getMemories() {
        return tags.stream().map(tag -> Pair.of(tag, memory.get(tag))).collect(Collectors.toList());
    }
}
