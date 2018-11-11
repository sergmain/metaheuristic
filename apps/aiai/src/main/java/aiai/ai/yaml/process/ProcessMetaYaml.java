package aiai.ai.yaml.process;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
public class ProcessMetaYaml {
    public List<ProcessMeta> metas = new ArrayList<>();

    public ProcessMeta get(String key) {
        for (ProcessMeta meta : metas) {
            if (meta.key.equals(key)) {
                return meta;
            }
        }
        return null;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProcessMeta {
        String key;
        String value;
        String ext;
    }
}
