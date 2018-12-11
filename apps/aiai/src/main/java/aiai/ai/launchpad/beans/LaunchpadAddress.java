package aiai.ai.launchpad.beans;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "AIAI_LP_LAUNCHPAD_ADDRESS")
@Data
public class LaunchpadAddress implements Serializable {

    private static final long serialVersionUID = 1604044122889826271L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    private Integer version;

    @Column(name = "URL")
    public String address;

    @Column(name = "DESCRIPTION")
    public String description;

    @Column(name = "CHECKSUM")
    public String checksum;


}
