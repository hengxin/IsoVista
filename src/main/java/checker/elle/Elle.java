package checker.elle;

import checker.Checker;
import checker.IsolationLevel;
import config.Config;
import exceptions.NotImplementedException;
import history.History;
import history.loader.ElleHistoryLoader;
import history.serializer.ElleHistorySerializer;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import util.RuntimeInfoRecorder;
import util.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static util.Profiler.updateMemory;

@Slf4j
public class Elle<VarType, ValType> implements Checker<VarType, ValType> {
    public static final String NAME = "ELLE_TCC";
    public static IsolationLevel ISOLATION_LEVEL;
    private static final String ELLE_CLI_PATH = "./resource/elle-cli-0.1.7-standalone.jar";
    private static final String MODEL = "list-append";
    private static final String CONSISTENCY_MODEL = "causal-cerone";
    private static final String TMP_ELLE_HIST_EDN = "./tmp_elle_hist.edn";
    private final Properties config;
    private Map<String, Long> stageTime;

    public Elle(Properties config) {
        this.config = config;
    }


    @SneakyThrows
    private boolean runJar(String path) {
        // java -jar target/elle-cli-0.1.7-standalone.jar --model list-append --consistency-models snapshot-isolation histories/elle/list-append-gh-30.edn
        ProcessBuilder pb = new ProcessBuilder("java", "-jar", ELLE_CLI_PATH, "--model", MODEL, "--consistency-models", CONSISTENCY_MODEL, "--cycle-search-timeout", "10000000", "/Users/draco/IdeaProjects/Plume/History/figure/fig_9/sess50/history0.edn");

        Process process = pb.start();

        RuntimeInfoRecorder.addPid(process.pid());

        AtomicBoolean done = new AtomicBoolean(false);
        var t = new Thread(() -> {
            log.info("Start a thread to update java memory usage");
            while (!done.get()) {
                long mem = utils.getProcessMemoryUsage(process.pid());
                updateMemory(mem * 1024);
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
            log.info("Java memory monitor thread stopped");
        });
        t.setDaemon(true);
        t.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            log.info(line);
            output.append(line);
        }

        String regex = "Stage ([\\w-]+) Time: (\\d+) ms";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(output);

        stageTime = new HashMap<>();

        while (matcher.find()) {
            String stageName = matcher.group(1);
            long time = Long.parseLong(matcher.group(2));
            stageTime.put(stageName, time);
        }
        stageTime.forEach((key, value) -> log.info(key + ": " + value + " ms"));

        int exitCode = process.waitFor();
        done.set(true);
        RuntimeInfoRecorder.removePid(process.pid());

        log.info("Elle-cli jar exited with code: " + exitCode);
        return true;
    }

    @Override
    public boolean verify(History<VarType, ValType> history) {
        boolean result;
        if (config.getProperty(Config.HISTORY_TYPE, Config.DEFAULT_HISTORY_TYPE).equals("elle") && config.getProperty(Config.WORKLOAD_SKIP_GENERATION).equals("true")) {
            new ElleHistorySerializer().serializeHistory((History<Integer, ElleHistoryLoader.ElleValue>) history, TMP_ELLE_HIST_EDN);
            result = runJar(config.getProperty(Config.HISTORY_PATH));
        } else {
            new ElleHistorySerializer().serializeHistory((History<Integer, ElleHistoryLoader.ElleValue>) history, TMP_ELLE_HIST_EDN);
            result = runJar(TMP_ELLE_HIST_EDN);
        }
        return result;
    }

    @Override
    public void outputDotFile(String path) {
        throw new NotImplementedException();
    }

    @Override
    public Map<String, Long> getProfileInfo() {
        return new HashMap<>() {{
            put("Construction", stageTime.get("construction"));
            put("Traversal", stageTime.get("scc") + stageTime.get("cycle-search"));
        }};
    }
}
