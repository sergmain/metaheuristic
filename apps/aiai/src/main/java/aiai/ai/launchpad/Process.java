package aiai.ai.launchpad;

import aiai.ai.Enums;
import lombok.Data;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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

    public Properties getMetaAsProp() {
        try(InputStream is = new ByteArrayInputStream(meta.getBytes())) {
            Properties p = new Properties();
            p.load(is);
            return p;
        } catch (IOException e) {
            log.error("Error", e);
            throw new IllegalStateException("Error loading properties from meta", e);
        }
    }
}
