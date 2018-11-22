package aiai.ai.station;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class Interpreter {

    public final String interpreter;
    public final String[] list;

    public Interpreter(String interpreter) {
        if (StringUtils.isBlank(interpreter)) {
            this.interpreter = null;
            this.list = null;
            return;
        }
        this.interpreter = interpreter;
        this.list = StringUtils.split(interpreter, " ");
    }

}
