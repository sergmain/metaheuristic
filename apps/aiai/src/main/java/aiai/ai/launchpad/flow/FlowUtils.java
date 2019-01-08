package aiai.ai.launchpad.flow;

public class FlowUtils {

    public static String getResourcePoolCode(String flowCode, long flowId, String processCode, int idx) {
        return String.format("%s-%d-%d-%s", flowCode, flowId, idx, processCode);
    }

    public static String getResourceCode(long flowId, long flowInstanceId, String processCode, String snippetName, int idx) {
        return String.format("%d-%d-%d-%s-%s", flowId, flowInstanceId, idx, snippetName, processCode);
    }
}
