package aiai.ai.yaml.flow;

import aiai.ai.Enums;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Process {
    public long id;
    public String name;
    public Enums.ProcessType type;
    public boolean returnAllResources;
    public List<String> snippetCodes = new ArrayList<>();

    public String resourcePoolCode;
}
