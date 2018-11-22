package aiai.ai.launchpad.resource;

import aiai.ai.Enums;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimpleResource {
    private Long id;
    private Integer version;
    private String code;
    private String poolCode;
    private int dataType;
    private java.util.Date uploadTs;
    public String checksum;
    public boolean valid;
    public boolean manual;
    public String filename;

    public String getDataTypeAsStr() {
        return Enums.BinaryDataType.from(dataType).toString();
    }
}