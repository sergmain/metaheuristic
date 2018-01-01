package aiai.ai.comm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_EMPTY )
@JsonIgnoreProperties(value = { "sysParams" })
public class Command {

    public enum Type { Nop, Ok, ReportStation, RequestDatasets, AssignStationId, RegisterInvite, RegisterInviteResult}

    private Type type;

    private Map<String, String> params = new HashMap<>();

    private Map<String, String> sysParams;

    private Map<String, String> response = new HashMap<>();

}
