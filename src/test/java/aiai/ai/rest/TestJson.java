package aiai.ai.rest;

import aiai.ai.AiApplication;
import aiai.ai.beans.NewMessage;
import aiai.ai.comm.Command;
import aiai.ai.comm.ExchangeData;
import aiai.ai.core.JsonService;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.io.IOException;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = AiApplication.class )
@WebAppConfiguration
public class TestJson {

    @Autowired
    private JsonService jsonService;

    @Test
    public void testJsonExchangeData() throws IOException {
        System.out.println("testJsonExchangeData()");
        String json = "{\"commands\":[{\"type\":\"Ok\",\"sysParams\":{\"test\":\"42\"}}],\"success\":true}";

        ExchangeData data = jsonService.getMapper().readValue(json, ExchangeData.class);

        Assert.assertNotNull(data.getCommands());
        Assert.assertEquals(1, data.getCommands().size());
        Command command = data.getCommands().get(0);
        Assert.assertEquals(Command.Type.Ok, command.getType());
        Assert.assertNull(command.getSysParams());
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testJsonFromEmpty_1() throws IOException {
        System.out.println("testJsonFromEmpty_1()");
        String json = "";

        thrown.expect(MismatchedInputException.class);
        jsonService.getMapper().readValue(json, NewMessage.class);
    }
}
