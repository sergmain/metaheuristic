package aiai.ai.launchpad;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 15.06.2017
 * Time: 19:54
 */
@Entity
@Table(name = "AIAI_DATASET")
@TableGenerator(
        name = "TABLE_AIAI_DATASET",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_DATASET",
        allocationSize = 1,
        initialValue = 1
)
@Data
public class Dataset implements Serializable {
    private static final long serialVersionUID = -1972306380977162458L;

    public Dataset() {
    }

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_DATASET")
    private long id;

    @Version
    @Column(name = "VERSION")
    private int version = 0;

    @Column(name = "DESCRIPTION")
    private String description;

}
