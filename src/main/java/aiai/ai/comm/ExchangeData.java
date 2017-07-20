package aiai.ai.comm;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 18:58
 */
public class ExchangeData {
    public List<Command> commands = new ArrayList<>();

    public ExchangeData() {
    }

    public ExchangeData(Command command) {
        addCommand(command);
    }

    public void addCommand(Command command) {
        commands.add(command);
    }

    @Override
    public String toString() {
        return "ExchangeData{" +
                "commands=" + commands +
                '}';
    }
}
