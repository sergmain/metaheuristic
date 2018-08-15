/*
 * AiAi, Copyright (C) 2017-2018  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.rest;

import aiai.ai.AiApplication;
import aiai.ai.comm.Command;
import aiai.ai.comm.ExchangeData;
import aiai.ai.comm.Protocol;
import aiai.ai.core.JsonUtils;
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
import java.util.LinkedHashMap;

public class TestJson {

    @Test
    public void testDontSerializeSysParams() throws IOException {
        ExchangeData ed = new ExchangeData();
        final Protocol.Nop nop = new Protocol.Nop();
        nop.setSysParams( new LinkedHashMap<>());
        nop.getSysParams().put("test", "42");
        nop.getParams().put("key", "13");
        ed.setSuccess(true);
        ed.setCommand(nop);
        String asJson = JsonUtils.toJson(ed);

        Assert.assertTrue(asJson.contains("13"));
        Assert.assertFalse(asJson.contains("42"));
    }

    @Test
    public void testJsonExchangeData() throws IOException {
        System.out.println("testJsonExchangeData()");
        String json = "{\"nop\":{\"type\":\"Nop\",\"params\":{\"key\":\"13\"},\"sysParams\":{\"key42\":\"42\"}},\"success\":true}";

        ExchangeData data = JsonUtils.getExchangeData(json);

        Assert.assertNotNull(data.getCommands());
        Assert.assertEquals(1, data.getCommands().size());
        Command command = data.getCommands().get(0);
        Assert.assertEquals(Command.Type.Nop, command.getType());
        Assert.assertNull(command.getSysParams());
        Assert.assertNotNull(command.getParams());
        Assert.assertEquals(1, command.getParams().size());
        Assert.assertEquals("13", command.getParams().get("key"));
    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void testJsonFromEmpty_1() throws IOException {
        System.out.println("testJsonFromEmpty_1()");
        String json = "";

        thrown.expect(MismatchedInputException.class);
        JsonUtils.getMapper().readValue(json, NewMessage.class);
    }
}
