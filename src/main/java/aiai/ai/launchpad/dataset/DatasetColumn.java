package aiai.ai.launchpad.dataset;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 21:27
 */
@Entity
@Table(name = "AIAI_DATASET_COLUMN")
@TableGenerator(
        name = "TABLE_AIAI_DATASET_COLUMN",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_DATASET_COLUMN",
        allocationSize = 1,
        initialValue = 1
)
@Data
@EqualsAndHashCode(exclude={"dataset", "datasetGroup"})
public class DatasetColumn implements Serializable {
    private static final long serialVersionUID = -1823497750685166069L;

    public DatasetColumn() {
    }

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_DATASET_COLUMN")
    private long id;

    @Version
    @Column(name = "VERSION")
    private int version = 0;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "NAME")
    private String name;

    @Column(name = "IS_SKIP")
    private boolean isSkip;

    @ManyToOne
    @JoinColumn(name = "DATASET_ID")
    private Dataset dataset;

    @ManyToOne
    @JoinColumn(name = "DATASET_GROUP_ID")
    private DatasetGroup datasetGroup;


    /**
     * last column in definition. is used in UI
     */
    @Transient
    private boolean isLastColumn;
}
