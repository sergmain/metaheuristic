package aiai.ai.launchpad.flow;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;
import aiai.ai.launchpad.beans.Flow;

public interface ProcessValidator {
    Enums.FlowValidateStatus validate(Flow flow, Process process);
}
