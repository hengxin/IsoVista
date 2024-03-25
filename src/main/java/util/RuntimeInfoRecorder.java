package util;

import com.sun.management.OperatingSystemMXBean;
import config.Config;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class RuntimeInfoRecorder {
    static final Lock lock = new ReentrantLock();
    static final String ExportCSVPath = Paths.get(Config.DEFAULT_CURRENT_PATH, "runtime_info.csv").toString();
    static ArrayList<Long> pidList = new ArrayList<>();
    static ArrayList<Long> timestampList = new ArrayList<>();
    static ArrayList<Double> cpuUsageList = new ArrayList<>();
    static ArrayList<Long> memoryUsageList = new ArrayList<>();
    static Thread recordThread;
    static Thread flushThread;
    static FileWriter csvWriter;
    static AtomicInteger atomicInt = new AtomicInteger(0);

    @SneakyThrows
    public static void start() {
        log.info("Create csv file: {}", ExportCSVPath);
        File file = new File(ExportCSVPath);
        file.createNewFile();
        csvWriter = new FileWriter(file, true);
        csvWriter.write("timestamp,cpu(%),memory(byte)\n");

        recordThread = new Thread(() -> {
            while (true) {
                lock.lock();
                timestampList.add(System.currentTimeMillis());
                updateCPU();
                updateMemory();
                lock.unlock();
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                }
            }
        });
        log.info("Start record thread");
        recordThread.setDaemon(true);
        recordThread.start();

        flushThread = new Thread(() -> {
            while (true) {
                writeToCSV();
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    writeToCSV();
                }
            }
        });
        log.info("Start flush thread");
        flushThread.setDaemon(true);
        flushThread.start();
    }

    @SneakyThrows
    public static void stop() {
        recordThread.interrupt();
        recordThread = null;
        flushThread.interrupt();
        flushThread = null;
        csvWriter.close();
    }

    static void writeToCSV() {
        lock.lock();
        for (int i = atomicInt.get(); i < timestampList.size(); i++) {
            long timestamp = timestampList.get(i);
            double cpuUsage = cpuUsageList.get(i);
            long memoryUsage = memoryUsageList.get(i);
            try {
                csvWriter.write(String.format("%d,%.2f,%d\n", timestamp, cpuUsage, memoryUsage));
                csvWriter.flush();
            } catch (IOException e) {
            }
        }
        atomicInt.set(timestampList.size());
        lock.unlock();
    }

    public static void addPid(long pid) {
        lock.lock();
        pidList.add(pid);
        lock.unlock();
    }

    public static void removePid(long pid) {
        lock.lock();
        pidList.remove(pid);
        lock.unlock();
    }


    static void updateCPU() {
        OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
        double processCpuUsage = osBean.getProcessCpuLoad() * 100;
        for (var pid : pidList) {
            processCpuUsage += utils.getProcessCPUUsage(pid);
        }
        cpuUsageList.add(processCpuUsage);
    }

    static void updateMemory() {
        var runtime = Runtime.getRuntime();
        var currentMemory = runtime.totalMemory() - runtime.freeMemory();
        for (var pid : pidList) {
            currentMemory += utils.getProcessMemoryUsage(pid) * 1024;
        }
        memoryUsageList.add(currentMemory);
    }
}