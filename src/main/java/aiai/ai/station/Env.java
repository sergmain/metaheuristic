package aiai.ai.station;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 17:31
 */
@Entity
@Table(name = "AIAI_S_ENV")
@TableGenerator(
        name = "TABLE_AIAI_ENV",
        table = "AIAI_IDS",
        pkColumnName = "sequence_name",
        valueColumnName = "sequence_next_value",
        pkColumnValue = "AIAI_ENV",
        allocationSize = 1,
        initialValue = 1
)
@Data
public class Env implements Serializable {
    private static final long serialVersionUID = -1968238169998898753L;

    @Id
    @Column(name = "ID")
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "TABLE_AIAI_ENV")
    private Long id;

    @Version
    @Column(name = "VERSION")
    private Integer version;

    @Column(name = "NAME")
    private String name;

    @Column(name = "DESCRIPTION")
    private String description;

}
