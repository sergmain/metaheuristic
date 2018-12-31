package aiai.ai.launchpad.server;

import aiai.ai.Enums;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResult {
    public Enums.UploadResourceStatus status;
    public String error;
}
