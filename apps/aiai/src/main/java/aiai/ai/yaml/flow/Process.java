package aiai.ai.yaml.flow;

import aiai.ai.Enums;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Process {
    public long id;
    public String name;
    public String code;
    public Enums.ProcessType type;
    public boolean collectResources = false;
    public List<String> snippetCodes = new ArrayList<>();
    public boolean parallelExec;

    public String resourcePoolCode;
    public long refId;
    public String outputType;
}
