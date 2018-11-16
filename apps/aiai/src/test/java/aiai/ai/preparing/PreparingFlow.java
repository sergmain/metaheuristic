package aiai.ai.preparing;

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.yaml.flow.FlowYaml;
import aiai.ai.yaml.flow.FlowYamlUtils;
import aiai.apps.commons.yaml.snippet.SnippetVersion;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.ByteArrayInputStream;

@Slf4j
public abstract class PreparingFlow extends PreparingExperiment {

    @Autowired
    public FlowRepository flowRepository;

    @Autowired
    public FlowInstanceRepository flowInstanceRepository;

    @Autowired
    public FlowService flowService;

    @Autowired
    public SnippetCache snippetCache;

    @Autowired
    public FlowYamlUtils flowYamlUtils;

    public Flow flow = null;
    public FlowYaml flowYaml = null;
    public Snippet s1 = null;
    public Snippet s2 = null;
    public Snippet s3 = null;
    public Snippet s4 = null;
    public Snippet s5 = null;
    public FlowInstance flowInstance = null;

    public abstract String getFlowParamsAsYaml();

    @Autowired
    private BinaryDataService binaryDataService;

    public static final String INPUT_POOL_CODE = "test-input-pool-code";
    public static final String INPUT_RESOURCE_CODE = "test-input-resource-code-";

    @Before
    public void beforePreparingFlow() {
        // snippet-01:1.1
        // snippet-02:1.1
        // snippet-03:1.1
        // snippet-04:1.1
        // snippet-05:1.1

        s1 = createSnippet("snippet-01:1.1");
        s2 = createSnippet("snippet-02:1.1");
        s3 = createSnippet("snippet-03:1.1");
        s4 = createSnippet("snippet-04:1.1");
        s5 = createSnippet("snippet-05:1.1");

        flow = new Flow();
        flow.setCode("test-flow-code");

        String params = getFlowParamsAsYaml();
        flow.setParams(params);

        flowRepository.save(flow);

        byte[] bytes = "A resource for input pool".getBytes();

        binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length,
                Enums.BinaryDataType.DATA,INPUT_RESOURCE_CODE+1, INPUT_POOL_CODE,
                true, "file-01.txt"
        );
        binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length,
                Enums.BinaryDataType.DATA,INPUT_RESOURCE_CODE+1, INPUT_POOL_CODE,
                true, "file-02.txt"
        );
        binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length,
                Enums.BinaryDataType.DATA,INPUT_RESOURCE_CODE+1, INPUT_POOL_CODE,
                true, "file-03.txt"
        );

    }

    private Snippet createSnippet(String snippetCode) {
        SnippetVersion sv = SnippetVersion.from(snippetCode);
        Snippet s = new Snippet();

/*
        ID          SERIAL PRIMARY KEY,
        VERSION     NUMERIC(5, 0)  NOT NULL,
        NAME      VARCHAR(50) not null,
        SNIPPET_TYPE      VARCHAR(50) not null,
        SNIPPET_VERSION   VARCHAR(20) not null,
        FILENAME  VARCHAR(250) not null,
        CHECKSUM    VARCHAR(2048),
        IS_SIGNED   BOOLEAN not null default false,
        IS_REPORT_METRICS   BOOLEAN not null default false,
        ENV         VARCHAR(50) not null,
        PARAMS         VARCHAR(1000),
        CODE_LENGTH integer not null,
        IS_FILE_PROVIDED   BOOLEAN not null default false

*/
        Snippet sn = snippetCache.findByNameAndSnippetVersion(sv.name, sv.version);
        if (sn!=null) {
            snippetCache.delete(sn);
        }
        s.setName(sv.name);
        s.setSnippetVersion(sv.version);
        s.setType(snippetCode + "-type");
        s.setFilename(null);
        s.setFileProvided(true);
        s.setChecksum(null);
        s.setSigned(false);
        s.setReportMetrics(false);
        s.setEnv("env-"+sv.name);
        s.setParams(null);
        s.setLength(1000);

        snippetCache.save(s);
        return s;
    }

    @After
    public void afterPreparingFlow() {
        if (flow!=null) {
            try {
                flowRepository.delete(flow);
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        deleteSnippet(s1);
        deleteSnippet(s2);
        deleteSnippet(s3);
        deleteSnippet(s4);
        deleteSnippet(s5);
        if (flowInstance!=null) {
            try {
                flowInstanceRepository.deleteById(flowInstance.getId());
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        try {
            binaryDataService.deleteByPoolCodeAndDataType(INPUT_POOL_CODE, Enums.BinaryDataType.DATA);
        } catch (Throwable th) {
            th.printStackTrace();
        }
    }

    private void deleteSnippet(Snippet s) {
        if (s!=null) {
            try {
                snippetCache.delete(s);
            } catch (Throwable th) {
                log.error("Error", th);
            }
        }
    }
}
