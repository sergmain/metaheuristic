package aiai.ai.yaml.flow;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Flow {
    public Long id;
    public List<Process> processes = new ArrayList<>();
}
