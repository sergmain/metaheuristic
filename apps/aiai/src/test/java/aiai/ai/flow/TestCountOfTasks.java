package aiai.ai.flow;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.flow.FlowService;
import aiai.ai.launchpad.task.TaskPersistencer;
import aiai.ai.launchpad.task.TaskService;
import aiai.ai.preparing.PreparingExperiment;
import aiai.ai.preparing.PreparingFlow;
import aiai.ai.yaml.flow.FlowYaml;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

@RunWith(SpringRunner.class)
@SpringBootTest
@ActiveProfiles("launchpad")
public class TestCountOfTasks extends PreparingFlow {

    @SuppressWarnings("Duplicates")
    @Override
    public String getFlowParamsAsYaml() {
        flowYaml = new FlowYaml();
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "assembly raw file";
            p.code = "assembly-raw-file";

            p.inputType = "raw-part-data";
            p.snippetCodes = Collections.singletonList("snippet-01:1.1");
            p.collectResources = false;
            p.outputType = "assembled-raw";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "dataset processing";
            p.code = "dataset-processing";

            p.snippetCodes = Collections.singletonList("snippet-02:1.1");
            p.collectResources = true;
            p.outputType = "dataset-processing";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.FILE_PROCESSING;
            p.name = "feature processing";
            p.code = "feature-processing";

            p.snippetCodes = Arrays.asList("snippet-03:1.1", "snippet-04:1.1", "snippet-05:1.1");
            p.parallelExec = true;
            p.collectResources = false;
            p.outputType = "feature";

            flowYaml.processes.add(p);
        }
        {
            Process p = new Process();
            p.type = Enums.ProcessType.EXPERIMENT;
            p.name = "experiment";
            p.code = PreparingExperiment.TEST_EXPERIMENT_CODE_01;

            p.metas.addAll(
                    Arrays.asList(
                            new Process.Meta("assembled-raw", "assembled-raw", null),
                            new Process.Meta("dataset", "dataset-processing", null),
                            new Process.Meta("feature", "feature", null)
                    )
            );

            flowYaml.processes.add(p);
        }

        String yaml = flowYamlUtils.toString(flowYaml);
        System.out.println(yaml);
        return yaml;
    }

    @Autowired
    public TaskService taskService;
    @Autowired
    public TaskPersistencer taskPersistencer;
    @Autowired
    public TaskCollector taskCollector;

    @Test
    public void testCreateTasks() {
        assertFalse(flowYaml.processes.isEmpty());
        assertEquals(Enums.ProcessType.EXPERIMENT, flowYaml.processes.get(flowYaml.processes.size()-1).type);

        Enums.FlowValidateStatus status = flowService.validate(flow);
        assertEquals(Enums.FlowValidateStatus.OK, status);

        FlowService.TaskProducingResult result = flowService.createFlowInstance(flow, PreparingFlow.INPUT_POOL_CODE);
        flowInstance = result.flowInstance;
        assertEquals(Enums.FlowProducingStatus.OK, result.flowProducingStatus);
        assertNotNull(flowInstance);
        assertEquals(Enums.FlowInstanceExecState.NONE.code, flowInstance.execState);


        Enums.FlowProducingStatus producingStatus = flowService.toProducing(flowInstance);
        assertEquals(Enums.FlowProducingStatus.OK, producingStatus);
        assertEquals(Enums.FlowInstanceExecState.PRODUCING.code, flowInstance.execState);

        List<Object[]> tasks01 = taskCollector.getTasks(result.flowInstance);
        assertTrue(tasks01.isEmpty());

        long mills = System.currentTimeMillis();
        result = flowService.produceAllTasks(false, flow, flowInstance);
        System.out.println("Number of tasks was counted for " + (System.currentTimeMillis() - mills ));

        assertEquals(Enums.FlowProducingStatus.OK, result.flowProducingStatus);
        int numberOfTasks = result.numberOfTasks;

        List<Object[]> tasks02 = taskCollector.getTasks(result.flowInstance);
        assertTrue(tasks02.isEmpty());

        mills = System.currentTimeMillis();
        result = flowService.produceAllTasks(true, flow, flowInstance);
        System.out.println("All tasks were produced for " + (System.currentTimeMillis() - mills ));

        flowInstance = result.flowInstance;
        assertEquals(Enums.FlowProducingStatus.OK, result.flowProducingStatus);
        assertEquals(Enums.FlowInstanceExecState.PRODUCED.code, flowInstance.execState);

        experiment = experimentCache.findById(experiment.getId());

        List<Object[]> tasks = taskCollector.getTasks(result.flowInstance);

        assertNotNull(result);
        assertNotNull(result.flowInstance);
        assertNotNull(tasks);
        assertFalse(tasks.isEmpty());
        assertEquals(numberOfTasks, result.numberOfTasks);

        result = flowService.produceAllTasks(false, flow, flowInstance);
        List<Object[]> tasks03 = taskCollector.getTasks(flowInstance);
        assertFalse(tasks03.isEmpty());
        assertEquals(numberOfTasks, result.numberOfTasks);

        int taskNumber = 0;
        for (Process process : flowYaml.processes) {
            if (process.type== Enums.ProcessType.EXPERIMENT) {
                continue;
            }
            taskNumber += process.snippetCodes.size();
        }

        assertEquals( 1+1+3+ 2*12*7, taskNumber +  experiment.getNumberOfTask());

    }

}
