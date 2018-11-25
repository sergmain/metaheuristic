package aiai.ai.launchpad;

import aiai.ai.Enums;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
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
    public List<Meta> metas = new ArrayList<>();
    public int order;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Meta {
        public String key;
        public String value;
        public String ext;
    }

    public Meta getMeta(String key) {
        if (metas==null) {
            return null;
        }
        for (Meta meta : metas) {
            if (meta.key.equals(key)) {
                return meta;
            }
        }
        return null;
    }
}
