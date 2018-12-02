package aiai.ai.launchpad.experiment.task;

import aiai.ai.Enums;
import aiai.ai.launchpad.beans.Task;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TaskWIthType {
    public Task task;
    public int type;

    public TaskWIthType(Task task, int type) {
        this.task = task;
        this.type = type;
    }

    public String typeAsString() {
        return Enums.ExperimentTaskType.from(type).toString();
    }

}
