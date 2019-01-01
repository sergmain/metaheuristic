package aiai.ai.preparing;

import aiai.ai.Enums;
import aiai.ai.flow.TaskCollector;
import aiai.ai.launchpad.beans.Flow;
import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.beans.Snippet;
import aiai.ai.launchpad.binary_data.BinaryDataService;
import aiai.ai.launchpad.flow.FlowCache;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.FlowRepository;
import aiai.ai.launchpad.snippet.SnippetCache;
import aiai.ai.launchpad.task.TaskPersistencer;
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
    public FlowCache flowCache;

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

    @Autowired
    public TaskCollector taskCollector;

    @Autowired
    public TaskPersistencer taskPersistencer;

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
        flow.setCreatedOn(System.currentTimeMillis());


        Flow tempFlow = flowRepository.findByCode(flow.getCode());
        if (tempFlow!=null) {
            flowCache.deleteById(tempFlow.getId());
        }
        flowCache.save(flow);

        byte[] bytes = "A resource for input pool".getBytes();

        binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length,
                Enums.BinaryDataType.DATA,INPUT_RESOURCE_CODE+1, INPUT_POOL_CODE,
                true, "file-01.txt",
                null);
        binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length,
                Enums.BinaryDataType.DATA,INPUT_RESOURCE_CODE+1, INPUT_POOL_CODE,
                true, "file-02.txt",
                null);
        binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length,
                Enums.BinaryDataType.DATA,INPUT_RESOURCE_CODE+1, INPUT_POOL_CODE,
                true, "file-03.txt",
                null);

    }

    private Snippet createSnippet(String snippetCode) {
        SnippetVersion sv = SnippetVersion.from(snippetCode);
        if (sv==null) {
            throw new IllegalStateException("wrong format of snippet code " + snippetCode);
        }
        Snippet s = new Snippet();
        Snippet sn = snippetRepository.findByNameAndSnippetVersion(sv.name, sv.version);
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
                flowCache.deleteById(flow.getId());
            } catch (Throwable th) {
                log.error("Error while flowCache.deleteById()", th);
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
                log.error("Error while flowInstanceRepository.deleteById()", th);
            }
            try {
                taskRepository.deleteByFlowInstanceId(flowInstance.getId());
            } catch (Throwable th) {
                log.error("Error while taskRepository.deleteByFlowInstanceId()", th);
            }
        }
        try {
            binaryDataService.deleteByPoolCodeAndDataType(INPUT_POOL_CODE, Enums.BinaryDataType.DATA);
        } catch (Throwable th) {
            log.error("error", th);
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
