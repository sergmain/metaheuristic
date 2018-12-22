package aiai.ai.launchpad.beans;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskSimple implements Serializable {
    private static final long serialVersionUID = 8143805222407343487L;

    public long id;
    public long flowInstanceId;
}
