package aiai.ai.launchpad.dataset;

import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 21:22
 */
@Entity
@Table(name = "AIAI_LP_DATASET_GROUP")
@TableGenerator(
        name = "TABLE_AIAI_DATASET_GROUP",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_DATASET_GROUP",
        allocationSize = 1,
        initialValue = 1
)
@Data
@EqualsAndHashCode(exclude={"dataset", "datasetColumns"})
public class DatasetGroup  implements Serializable {
    private static final long serialVersionUID = -3161178396332333392L;

    public DatasetGroup() {
    }

    public DatasetGroup(int groupNumber) {
        this.groupNumber = groupNumber;
    }

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_DATASET_GROUP")
    private Long id;

    @Version
    @Column(name = "VERSION")
    private Integer version;

    @Column(name = "GROUP_NUMBER")
    private int groupNumber;

    @Column(name = "DESCRIPTION")
    private String description;

    @Column(name = "IS_SKIP")
    private boolean isSkip;

    @Column(name = "IS_LABEL")
    private boolean isLabel;

    @ManyToOne(cascade = {CascadeType.PERSIST, CascadeType.DETACH, CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "DATASET_ID")
    private Dataset dataset;

    @OneToMany(mappedBy = "datasetGroup", cascade = CascadeType.ALL)
    private List<DatasetColumn> datasetColumns;

    /**
     * used only for UI
     */
    @Transient
    private boolean isAddColumn;

}
