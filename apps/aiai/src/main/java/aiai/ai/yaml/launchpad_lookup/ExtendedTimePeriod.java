package aiai.ai.yaml.launchpad_lookup;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ExtendedTimePeriod {
    public String workingDay;
    public String weekend;
    public String dayMask;
    public String holiday;
    public String exceptionWorkingDay;
}
