package util;

import lombok.SneakyThrows;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class utils {

    @SneakyThrows
    public static long getProcessMemoryUsage(long pid) {
        ProcessBuilder memoryQueryProcessBuilder = new ProcessBuilder("ps", "-p", Long.toString(pid), "-o", "rss=");
        Process memoryQueryProcess = memoryQueryProcessBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(memoryQueryProcess.getInputStream()));
        String line = reader.readLine();
        if (line != null) {
            return Long.parseLong(line.trim());
        }
        return 0L;
    }

    @SneakyThrows
    public static double getProcessCPUUsage(long pid) {
        ProcessBuilder cpuQueryProcessBuilder = new ProcessBuilder("ps", "-p", Long.toString(pid), "-o", "%cpu");
        Process cpuQueryProcess = cpuQueryProcessBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(cpuQueryProcess.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.contains("%CPU")) continue; // skip first line
            return Double.parseDouble(line.trim()) / Runtime.getRuntime().availableProcessors();
        }
        return 0;
    }
}
