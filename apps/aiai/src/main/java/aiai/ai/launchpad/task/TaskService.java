package aiai.ai.launchpad.task;

import aiai.ai.launchpad.beans.Task;
import aiai.ai.launchpad.experiment.SimpleTaskExecResult;
import aiai.ai.launchpad.repositories.TaskRepository;
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
            Task t = prepareTask(result);
            if (t!=null) {
                list.add(t);
            }
        }
        taskRepository.saveAll(list);
        return ids;
    }

    public void markAsCompleted(SimpleTaskExecResult result) {
        Task t = prepareTask(result);
        if (t!=null) {
            taskRepository.save(t);
        }
    }

    private Task prepareTask(SimpleTaskExecResult result) {
        Task task = taskRepository.findById(result.taskId).orElse(null);
        if (task==null) {
            log.warn("Can't find Task for Id: {}", result.taskId);
            return null;
        }
        task.setSnippetExecResults(result.getResult());
        task.setMetrics(result.getMetrics());
        task.setCompleted(true);
        task.setCompletedOn(System.currentTimeMillis());
        return task;
    }

}
