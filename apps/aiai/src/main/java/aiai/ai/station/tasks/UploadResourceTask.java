package aiai.ai.station.tasks;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.File;

@Data
@AllArgsConstructor
@EqualsAndHashCode
public class UploadResourceTask implements StationRestTask {
    public File   file;
    public long taskId;

}
