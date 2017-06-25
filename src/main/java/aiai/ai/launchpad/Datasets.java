package aiai.ai.launchpad;

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
public class Datasets implements Serializable {
    private static final long serialVersionUID = -1972306380977162458L;

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_DATASET")
    @Getter @Setter
    public long id;

    @Column(name = "DESCRIPTION")
    @Getter @Setter
    public String desc;

}
