package aiai.ai.launchpad;

import aiai.ai.Enums;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Data
@ToString
@Slf4j
public class Process {
    public String name;
    public String code;
    public Enums.ProcessType type;
    public boolean collectResources = false;
    public List<String> snippetCodes;
    public boolean parallelExec;

    public String inputType;
    public String inputResourceCode;
    public String outputType;
    public String outputResourceCode;
    public String meta;
}
