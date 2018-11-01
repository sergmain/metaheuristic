package aiai.ai.yaml.flow;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FlowYaml {
    public List<Process> processes = new ArrayList<>();
}
