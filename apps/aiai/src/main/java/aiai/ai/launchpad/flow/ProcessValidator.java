package aiai.ai.launchpad.flow;

import aiai.ai.Enums;
import aiai.ai.launchpad.Process;

public interface ProcessValidator {
    Enums.FlowValidateStatus validate(Process process);
}
