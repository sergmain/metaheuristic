package aiai.ai.launchpad.experiment;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 15:38
 */
@Entity
@Table(name = "AIAI_LP_EXPERIMENT")
@TableGenerator(
        name = "TABLE_AIAI_EXPERIMENT",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_EXPERIMENT",
        allocationSize = 1,
        initialValue = 1
)
@Data
public class Experiment implements Serializable {
    private static final long serialVersionUID = 4139058273459149904L;

    public Experiment() {
    }

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_EXPERIMENT")
    private Long id;

    @Version
    @Column(name = "VERSION")
    private Integer version;

    @Column(name = "NAME")
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

}
