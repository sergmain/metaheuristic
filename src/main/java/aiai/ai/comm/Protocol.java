package aiai.ai.comm;

/**
 * User: Serg
 * Date: 13.07.2017
 * Time: 22:20
 */
public class Protocol {

    public static class Nop extends Command {
        public Nop() {
            this.setType(Type.Nop);
        }
    }

    public static class Ok extends Command {
        public Ok() {
            this.setType(Type.Ok);
        }
    }
}
