/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.preparing;

import aiai.ai.Consts;
import aiai.ai.Enums;
import aiai.ai.flow.TaskCollector;
import aiai.api.v1.launchpad.Process;
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
import aiai.ai.yaml.input_resource_param.InputResourceParam;
import aiai.api.v1.EnumsApi;
import aiai.apps.commons.yaml.snippet.SnippetConfig;
import aiai.apps.commons.yaml.snippet.SnippetConfigUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.io.ByteArrayInputStream;
import java.util.*;

import static org.junit.Assert.assertTrue;

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

    public InputResourceParam inputResourceParam;


    public abstract String getFlowParamsAsYaml();

    public String getFlowParamsAsYaml_Simple() {
        flowYaml = new FlowYaml();
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.snippetCodes = Collections.singletonList("snippet-01:1.1");
            p.collectResources = false;
            p.outputType = "assembled-raw-output";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippetCodes = Collections.singletonList("snippet-02:1.1");
            p.collectResources = true;
            p.outputType = "dataset-processing-output";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.FILE_PROCESSING;
            p.name = "feature processing";
            p.code = "feature-processing";

            p.snippetCodes = Arrays.asList("snippet-03:1.1", "snippet-04:1.1", "snippet-05:1.1");
            p.parallelExec = true;
            p.collectResources = true;
            p.outputType = "feature-output";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = EnumsApi.ProcessType.EXPERIMENT;
            p.name = "experiment";
            p.code = PreparingExperiment.TEST_EXPERIMENT_CODE_01;

            p.metas.addAll(
                    Arrays.asList(
                            new Process.Meta("assembled-raw", "assembled-raw-output", null),
                            new Process.Meta("dataset", "dataset-processing-output", null),
                            new Process.Meta("feature", "feature-output", null)
                    )
            );

            flowYaml.processes.add(p);
        }

        String yaml = flowYamlUtils.toString(flowYaml);
        System.out.println(yaml);
        return yaml;
    }

    @Autowired
    private BinaryDataService binaryDataService;

    public static final String INPUT_POOL_CODE = "test-input-pool-code";
    public static final String INPUT_RESOURCE_CODE = "test-input-resource-code-";

    @Before
    public void beforePreparingFlow() {
        assertTrue(globals.isUnitTesting);

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
                Enums.BinaryDataType.DATA,INPUT_RESOURCE_CODE+2, INPUT_POOL_CODE,
                true, "file-02.txt",
                null);
        binaryDataService.save(new ByteArrayInputStream(bytes), bytes.length,
                Enums.BinaryDataType.DATA,INPUT_RESOURCE_CODE+3, INPUT_POOL_CODE,
                true, "file-03.txt",
                null);

        inputResourceParam = new InputResourceParam();
        inputResourceParam.poolCodes = new HashMap<>();
        inputResourceParam.poolCodes.computeIfAbsent(Consts.FLOW_INSTANCE_INPUT_TYPE, o-> new ArrayList<>()).add(INPUT_POOL_CODE);
    }

    private Snippet createSnippet(String snippetCode) {
        SnippetConfig sc = new SnippetConfig();
        sc.code = snippetCode;
        sc.type = snippetCode + "-type";
        sc.file = null;
        sc.setEnv("env-"+snippetCode);
        sc.sourcing = EnumsApi.SnippetSourcing.station;;
        sc.metrics = false;

        sc.info.setSigned(false);
        sc.info.setLength(1000);

        Snippet s = new Snippet();
        Snippet sn = snippetRepository.findByCode(snippetCode);
        if (sn!=null) {
            snippetCache.delete(sn);
        }
        s.setCode(snippetCode);
        s.setType(sc.type);
        s.setParams(SnippetConfigUtils.toString(sc));

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
            } catch (ObjectOptimisticLockingFailureException th) {
                //
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
