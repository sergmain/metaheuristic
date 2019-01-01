package aiai.ai.station.tasks;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;

@Data
@AllArgsConstructor
@EqualsAndHashCode(of="taskId", callSuper = false)
public class UploadResourceTask extends StationRestTask {
    public long taskId;
    public File file;
}
