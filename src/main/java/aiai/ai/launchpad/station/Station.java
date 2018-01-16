package aiai.ai.launchpad.station;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 25.06.2017
 * Time: 15:56
 */
@Entity
@Table(name = "AIAI_LP_STATION")
@TableGenerator(
        name = "TABLE_AIAI_STATION",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_STATION",
        allocationSize = 1,
        initialValue = 1
)
@Data
public class Station implements Serializable {
    private static final long serialVersionUID = -6094247705164836600L;

    public Station() {}

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_STATION")
    private Long id;

    @Version
    @Column(name = "VERSION")
    public Integer version;

    @Column(name = "IP")
    private String ip;

    @Column(name = "DESCRIPTION")
    private String description;


}

