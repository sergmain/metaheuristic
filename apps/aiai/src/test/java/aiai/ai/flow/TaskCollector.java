package aiai.ai.flow;

import aiai.ai.launchpad.beans.FlowInstance;
import aiai.ai.launchpad.repositories.TaskRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Profile("launchpad")
public class TaskCollector {

    private final TaskRepository taskRepository;

    public TaskCollector(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public List<Object[]> getTasks(FlowInstance flowInstance) {
        try (Stream<Object[]> stream = taskRepository.findByFlowInstanceId(flowInstance.getId()) ) {
            return stream.collect(Collectors.toList());
        }
    }
}
