package aiai.ai.comm;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 19:07
 */
@Data
public class Command {

    public enum Type { Nop, Ok, ReportStation, RequestDatasets, AssignStationId }

    private Type type;

    private Map<String, String> params = new HashMap<>();

    private Map<String, String> sysParams = null;

    private final Map<String, String> response = new HashMap<>();

}
