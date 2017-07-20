package aiai.ai.comm;

import lombok.Data;

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 19:07
 */
@Data
public class Command {

    public static enum Type { Nop, Ok}

    private Type type;
}
