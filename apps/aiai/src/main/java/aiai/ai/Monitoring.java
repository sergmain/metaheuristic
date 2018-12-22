package aiai.ai;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Monitoring {

    public static void log(String tag, Enums.Monitor ... monitors) {
        if (monitors==null) {
            throw new IllegalStateException("monitors is null");
        }
        if (isMemory(monitors)) {
            log.debug("{} mem free: {}, total: {}, max: {}", tag, Runtime.getRuntime().freeMemory(), Runtime.getRuntime().maxMemory(), Runtime.getRuntime().totalMemory());
        }
    }

    private static boolean isMemory(Enums.Monitor ... monitors) {
        for (Enums.Monitor monitor : monitors) {
            if (monitor== Enums.Monitor.MEMORY) {
                return true;
            }
        }
        return false;
    }
}
