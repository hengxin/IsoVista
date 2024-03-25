package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class utils {

    public static Long getProcessMemoryUsage(Process process) throws IOException {
        int pid = Math.toIntExact(process.pid());
        ProcessBuilder memoryQueryProcessBuilder = new ProcessBuilder("ps", "-p", Integer.toString(pid), "-o", "rss=");
        Process memoryQueryProcess = memoryQueryProcessBuilder.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(memoryQueryProcess.getInputStream()));
        String line = reader.readLine();
        if (line != null) {
            return Long.valueOf(line.trim());
        }
        return 0L;
    }
}
