/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.communication;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.communication.keep_alive.*;
import ai.metaheuristic.api.EnumsApi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 1/4/2021
 * Time: 4:34 AM
 */
public class TestKeepAliveRequestParamYamlUtils {

    @Test
    public void test() {
        KeepAliveRequestParamYamlV1 karv1 = getKeepAliveRequestParamYamlV1();
        KeepAliveRequestParamYamlV2 karv2 = new KeepAliveRequestParamYamlUtilsV1().upgradeTo(karv1);

        System.out.println(KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.toString(karv2));

        testAssertsV2(karv2);

        assertEquals(1, karv2.requests.size());
        KeepAliveRequestParamYamlV2.ProcessorRequestV2 processorRequestV2 = karv2.requests.get(0);
        processorRequestV2.processor.env.quotas.limit = 13;
        processorRequestV2.processor.env.quotas.values.addAll(List.of(new KeepAliveRequestParamYamlV2.QuotaV2("tag1", 15, false), new KeepAliveRequestParamYamlV2.QuotaV2("tag2", 25, false)));
        KeepAliveRequestParamYaml kar = new KeepAliveRequestParamYamlUtilsV2().upgradeTo(karv2);

        testAsserts(kar);
    }

    private static KeepAliveRequestParamYamlV1 getKeepAliveRequestParamYamlV1() {
        KeepAliveRequestParamYamlV1 req = new KeepAliveRequestParamYamlV1();
        KeepAliveRequestParamYamlV1.ProcessorRequestV1 r = new KeepAliveRequestParamYamlV1.ProcessorRequestV1();
        req.requests.add(r);
        r.processor = new KeepAliveRequestParamYamlV1.ReportProcessorV1();

        r.processor.env = new KeepAliveRequestParamYamlV1.EnvV1("tag1");
        r.processor.env.envs.putAll(Map.of("env-key1", "env-value1", "env-key2", "env-value2"));
        r.processor.env.mirrors.putAll(Map.of("mirror-key1", "mirror-value1", "mirror-key2", "mirror-value2"));
        r.processor.env.disk.addAll(List.of(new KeepAliveRequestParamYamlV1.DiskStorageV1("code1", "path1"), new KeepAliveRequestParamYamlV1.DiskStorageV1("code2", "path2")));

        r.processor.gitStatusInfo = new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown, "ver1", "no-error");
        r.processor.schedule = "schedule1";
        r.processor.sessionId = "sessionId-42";
        r.processor.sessionCreatedOn = 13;
        r.processor.ip = "192.168.0.17";
        r.processor.host = "host-17";
        r.processor.errors = List.of("error-1", "error-2");
        r.processor.logDownloadable = true;
        r.processor.taskParamsVersion = 2;
        r.processor.os = EnumsApi.OS.any;
        r.processor.currDir = "/home";

        req.functions.statuses.addAll(List.of(new KeepAliveRequestParamYamlV1.FunctionDownloadStatusesV1.Status("code1", Enums.FunctionState.none),
                new KeepAliveRequestParamYamlV1.FunctionDownloadStatusesV1.Status("code2", Enums.FunctionState.checksum_wrong)));

        r.requestProcessorId = new KeepAliveRequestParamYamlV1.RequestProcessorIdV1();

        r.processorCommContext = new KeepAliveRequestParamYamlV1.ProcessorCommContextV1(11L, "session-11");

        return req;
    }

    private static void testAsserts(KeepAliveRequestParamYaml kar) {
        assertEquals(1, kar.requests.size());

        assertNotNull(kar.requests.get(0).processor);
        assertNotNull(kar.requests.get(0).processor.env);

        assertEquals("tag1", kar.requests.get(0).processor.env.tags);
        assertEquals(2, kar.requests.get(0).processor.env.envs.size());
        assertEquals("env-value1", kar.requests.get(0).processor.env.envs.get("env-key1"));
        assertEquals("env-value2", kar.requests.get(0).processor.env.envs.get("env-key2"));
        assertEquals(2, kar.requests.get(0).processor.env.mirrors.size());
        assertEquals("mirror-value1", kar.requests.get(0).processor.env.mirrors.get("mirror-key1"));
        assertEquals("mirror-value2", kar.requests.get(0).processor.env.mirrors.get("mirror-key2"));
        assertEquals(2, kar.requests.get(0).processor.env.disk.size());
        assertEquals("code1", kar.requests.get(0).processor.env.disk.get(0).code);
        assertEquals("path1", kar.requests.get(0).processor.env.disk.get(0).path);
        assertEquals("code2", kar.requests.get(0).processor.env.disk.get(1).code);
        assertEquals("path2", kar.requests.get(0).processor.env.disk.get(1).path);

        assertNotNull(kar.requests.get(0).processor.gitStatusInfo);

        assertEquals(Enums.GitStatus.unknown, kar.requests.get(0).processor.gitStatusInfo.status);
        assertEquals("ver1", kar.requests.get(0).processor.gitStatusInfo.version);
        assertEquals("no-error", kar.requests.get(0).processor.gitStatusInfo.error);

        assertEquals("schedule1", kar.requests.get(0).processor.schedule);
        assertEquals("sessionId-42", kar.requests.get(0).processor.sessionId);
        assertEquals(13, kar.requests.get(0).processor.sessionCreatedOn);
        assertEquals("192.168.0.17", kar.requests.get(0).processor.ip);
        assertEquals("host-17", kar.requests.get(0).processor.host);
        assertEquals("host-17", kar.requests.get(0).processor.host);
        assertEquals(2, kar.requests.get(0).processor.errors.size());
        assertEquals("error-1", kar.requests.get(0).processor.errors.get(0));
        assertEquals("error-2", kar.requests.get(0).processor.errors.get(1));
        assertTrue(kar.requests.get(0).processor.logDownloadable);
        assertEquals(2, kar.requests.get(0).processor.taskParamsVersion);
        assertEquals(EnumsApi.OS.any, kar.requests.get(0).processor.os);
        assertEquals("/home", kar.requests.get(0).processor.currDir);

        assertNotNull(kar.functions.statuses);
        assertEquals(2, kar.functions.statuses.size());
        assertEquals("code1", kar.functions.statuses.get(0).code);
        assertEquals(Enums.FunctionState.none, kar.functions.statuses.get(0).state);
        assertEquals("code2", kar.functions.statuses.get(1).code);
        assertEquals(Enums.FunctionState.checksum_wrong, kar.functions.statuses.get(1).state);

        assertNotNull(kar.requests.get(0).requestProcessorId);

        assertNotNull(kar.requests.get(0).processorCommContext);
        assertEquals(11L, kar.requests.get(0).processorCommContext.processorId);
        assertEquals("session-11", kar.requests.get(0).processorCommContext.sessionId);

        assertEquals(1, kar.requests.size());
        KeepAliveRequestParamYaml.ProcessorRequest processorRequest = kar.requests.get(0);
        assertEquals(13, processorRequest.processor.env.quotas.limit);
        assertEquals(2, processorRequest.processor.env.quotas.values.size());

        KeepAliveRequestParamYaml.Quota q1 = processorRequest.processor.env.quotas.values.get(0);
        assertEquals("tag1", q1.tag);
        assertEquals(15, q1.amount);

        KeepAliveRequestParamYaml.Quota q2 = processorRequest.processor.env.quotas.values.get(1);
        assertEquals("tag2", q2.tag);
        assertEquals(25, q2.amount);
    }

    private static void testAssertsV2(KeepAliveRequestParamYamlV2 kar2) {
        assertEquals(1, kar2.requests.size());

        assertNotNull(kar2.requests.get(0).processor);
        assertNotNull(kar2.requests.get(0).processor.env);

        assertEquals("tag1", kar2.requests.get(0).processor.env.tags);
        assertEquals(2, kar2.requests.get(0).processor.env.envs.size());
        assertEquals("env-value1", kar2.requests.get(0).processor.env.envs.get("env-key1"));
        assertEquals("env-value2", kar2.requests.get(0).processor.env.envs.get("env-key2"));
        assertEquals(2, kar2.requests.get(0).processor.env.mirrors.size());
        assertEquals("mirror-value1", kar2.requests.get(0).processor.env.mirrors.get("mirror-key1"));
        assertEquals("mirror-value2", kar2.requests.get(0).processor.env.mirrors.get("mirror-key2"));
        assertEquals(2, kar2.requests.get(0).processor.env.disk.size());
        assertEquals("code1", kar2.requests.get(0).processor.env.disk.get(0).code);
        assertEquals("path1", kar2.requests.get(0).processor.env.disk.get(0).path);
        assertEquals("code2", kar2.requests.get(0).processor.env.disk.get(1).code);
        assertEquals("path2", kar2.requests.get(0).processor.env.disk.get(1).path);

        assertNotNull(kar2.requests.get(0).processor.gitStatusInfo);

        assertEquals(Enums.GitStatus.unknown, kar2.requests.get(0).processor.gitStatusInfo.status);
        assertEquals("ver1", kar2.requests.get(0).processor.gitStatusInfo.version);
        assertEquals("no-error", kar2.requests.get(0).processor.gitStatusInfo.error);

        assertEquals("schedule1", kar2.requests.get(0).processor.schedule);
        assertEquals("sessionId-42", kar2.requests.get(0).processor.sessionId);
        assertEquals(13, kar2.requests.get(0).processor.sessionCreatedOn);
        assertEquals("192.168.0.17", kar2.requests.get(0).processor.ip);
        assertEquals("host-17", kar2.requests.get(0).processor.host);
        assertEquals("host-17", kar2.requests.get(0).processor.host);
        assertEquals(2, kar2.requests.get(0).processor.errors.size());
        assertEquals("error-1", kar2.requests.get(0).processor.errors.get(0));
        assertEquals("error-2", kar2.requests.get(0).processor.errors.get(1));
        assertTrue(kar2.requests.get(0).processor.logDownloadable);
        assertEquals(2, kar2.requests.get(0).processor.taskParamsVersion);
        assertEquals(EnumsApi.OS.any, kar2.requests.get(0).processor.os);
        assertEquals("/home", kar2.requests.get(0).processor.currDir);

        assertNotNull(kar2.functions.statuses);
        assertEquals(2, kar2.functions.statuses.size());
        assertEquals("code1", kar2.functions.statuses.get(0).code);
        assertEquals(Enums.FunctionState.none, kar2.functions.statuses.get(0).state);
        assertEquals("code2", kar2.functions.statuses.get(1).code);
        assertEquals(Enums.FunctionState.checksum_wrong, kar2.functions.statuses.get(1).state);

        assertNotNull(kar2.requests.get(0).requestProcessorId);

        assertNotNull(kar2.requests.get(0).processorCommContext);
        assertEquals(11L, kar2.requests.get(0).processorCommContext.processorId);
        assertEquals("session-11", kar2.requests.get(0).processorCommContext.sessionId);

    }
}
