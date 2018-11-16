package aiai.ai.launchpad.task;

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.experiment.SimpleTaskExecResult;
import aiai.ai.launchpad.repositories.FlowInstanceRepository;
import aiai.ai.launchpad.repositories.TaskRepository;
import aiai.ai.yaml.console.SnippetExec;
import aiai.ai.yaml.console.SnippetExecUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public List<Long> storeAllResults(List<SimpleTaskExecResult> results) {
        List<Task> list = new ArrayList<>();
        List<Long> ids = new ArrayList<>();
        for (SimpleTaskExecResult result : results) {
            ids.add(result.taskId);
            SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(result.getResult());
            Task t = prepareTask(result, snippetExec.exec.isOk ? Enums.TaskExecState.OK : Enums.TaskExecState.ERROR);
            if (t!=null) {
                list.add(t);
            }
        }
        taskRepository.saveAll(list);
        return ids;
    }

    public void markAsCompleted(SimpleTaskExecResult result) {
        SnippetExec snippetExec = SnippetExecUtils.toSnippetExec(result.getResult());
        Task t = prepareTask(result, snippetExec.exec.isOk ? Enums.TaskExecState.OK : Enums.TaskExecState.ERROR);
        if (t!=null) {
            taskRepository.save(t);
        }
    }

    private Task prepareTask(SimpleTaskExecResult result, Enums.TaskExecState state) {
        Task task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("Can't find Task for Id: {}", result.taskId);
            return null;
        }
        task.setExecState(state.value);
        task.setSnippetExecResults(result.getResult());
        task.setMetrics(result.getMetrics());
        task.setCompleted(true);
        task.setCompletedOn(System.currentTimeMillis());
        return task;
    }
}
