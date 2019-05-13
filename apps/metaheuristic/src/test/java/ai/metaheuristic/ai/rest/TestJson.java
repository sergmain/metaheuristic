/*
 * Metaheuristic, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ai.rest;

import ai.metaheuristic.ai.comm.Command;
import ai.metaheuristic.ai.comm.ExchangeData;
import ai.metaheuristic.ai.comm.Protocol;
import ai.metaheuristic.ai.core.JsonUtils;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;

public class TestJson {

    @Test
    public void testStationId() throws IOException {
        ExchangeData ed = new ExchangeData();
        final Protocol.AssignedStationId cmd = new Protocol.AssignedStationId();
        cmd.setAssignedStationId("42");
        ed.setSuccess(true);
        ed.setCommand(cmd);
        String asJson = JsonUtils.toJson(ed);
        Assert.assertTrue(asJson.contains("42"));
    }

    @Test
    public void testJsonExchangeData() throws IOException {
        System.out.println("testJsonExchangeData()");
        String json = "{\"nop\":{\"type\":\"Nop\",\"params\":{\"key\":\"13\"}},\"success\":true}";

        ExchangeData data = JsonUtils.getExchangeData(json);

        Assert.assertNotNull(data.getCommands());
        Assert.assertEquals(1, data.getCommands().size());
        Command command = data.getCommands().get(0);
        Assert.assertEquals(Command.Type.Nop, command.getType());
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
