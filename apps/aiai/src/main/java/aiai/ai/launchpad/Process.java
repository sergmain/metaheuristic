package aiai.ai.launchpad;

import aiai.ai.Enums;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Process {
    public String name;
    public String code;
    public Enums.ProcessType type;
    public boolean collectResources = false;
    public List<String> snippetCodes;
    public boolean parallelExec;

    public String inputType;
    public String outputType;
}
