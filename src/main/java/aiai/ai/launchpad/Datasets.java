package aiai.ai.launchpad;

import javax.persistence.Entity;
import javax.persistence.Id;

/**
 * User: Serg
 * Date: 15.06.2017
 * Time: 19:54
 */
@Entity
public class Datasets {

    @Id
    private String id;

    @Id
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
