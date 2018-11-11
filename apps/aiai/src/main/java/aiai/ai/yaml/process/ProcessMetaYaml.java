package aiai.ai.yaml.process;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProcessMetaYaml {
    List<ProcessMeta> metas = new ArrayList<>();

    @Data
    public static class ProcessMeta {
        String type;
        List<String> resources;
    }
}
