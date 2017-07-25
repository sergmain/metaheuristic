package aiai.ai.launchpad.dataset;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;

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
@EqualsAndHashCode(exclude={"datasetGroups"})
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

    @Column(name = "IS_HEADER")
    private boolean isHeader;


    @OneToMany(mappedBy = "dataset", cascade = CascadeType.ALL)
    private List<DatasetGroup> datasetGroups;
}
