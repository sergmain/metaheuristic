package aiai.ai.station.tasks;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;

@Data
@AllArgsConstructor
@EqualsAndHashCode(of="taskId")
public class UploadResourceTask implements StationRestTask {
    public long taskId;
    public File   file;
}
