/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYamlUtils;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYamlUtilsV3;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYamlV3;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYamlV3.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 4/29/2022
 * Time: 11:28 AM
 */
public class TestKeepAliveRequestParamYamlUtils_v3 {

    @Test
    public void testEquals() {
        assertEquals(new QuotaV3("tag1", 15, false), new QuotaV3("tag1", 15, false));
        assertNotEquals(new QuotaV3("tag2", 15, false), new QuotaV3("tag1", 15, false));
        assertNotEquals(new QuotaV3("tag1", 16, false), new QuotaV3("tag1", 15, false));
        assertNotEquals(new QuotaV3("tag1", 15, true), new QuotaV3("tag1", 15, false));
    }

    @Test
    public void test() {
        KeepAliveRequestParamYamlV3 karv3 = getKeepAliveRequestParamYamlV3();

        // V3
        testAssertsV3(karv3);
        karv3 = v3ToV3(karv3);
        testAssertsV3(karv3);
        initV3Specific(karv3);
        testAssertsV3Specific(karv3);

        KeepAliveRequestParamYaml kar = new KeepAliveRequestParamYamlUtilsV3().upgradeTo(karv3);
        testAsserts(kar);
    }

    private static void testAssertsV3Specific(KeepAliveRequestParamYamlV3 karv3) {
    }

    private static void initV3Specific(KeepAliveRequestParamYamlV3 karv3) {
    }

    private static KeepAliveRequestParamYamlV3 v3ToV3(KeepAliveRequestParamYamlV3 v3) {
        String yamlv3 = KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.toString(v3);
        //noinspection rawtypes
        final AbstractParamsYamlUtils forVersion3 = KeepAliveRequestParamYamlUtils.BASE_YAML_UTILS.getForVersion(3);
        assertNotNull(forVersion3);
        KeepAliveRequestParamYamlV3 karv3 = (KeepAliveRequestParamYamlV3) forVersion3.to(yamlv3);
        return karv3;
    }

    private static KeepAliveRequestParamYamlV3 getKeepAliveRequestParamYamlV3() {
        KeepAliveRequestParamYamlV3 req = new KeepAliveRequestParamYamlV3();
        ProcessorV3 r = req.processor;
        r.status = new ProcessorStatusV3();

        r.status.env = new EnvV3();
        r.status.env.envs.putAll(Map.of("env-key1", "env-value1", "env-key2", "env-value2"));
        r.status.env.mirrors.putAll(Map.of("mirror-key1", "mirror-value1", "mirror-key2", "mirror-value2"));
        r.status.env.disk.addAll(List.of(new DiskStorageV3("code1", "path1"), new DiskStorageV3("code2", "path2")));
        r.status.env.quotas.init(new QuotasV3(
                List.of(new QuotaV3("tag1", 15, true),
                        new QuotaV3("tag2", 25, false)),
                23, 4, true));

        r.status.gitStatusInfo = new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown, "ver1", "no-error");
        r.status.schedule = "schedule1";

        r.status.ip = "192.168.0.17";
        r.status.host = "host-17";
        r.status.errors = List.of("error-1", "error-2");
        r.status.logDownloadable = true;
        r.status.taskParamsVersion = 2;
        r.status.os = EnumsApi.OS.any;
        r.status.currDir = "/home";

        req.functions.statuses.put(EnumsApi.FunctionState.none, "code1");
        req.functions.statuses.put(EnumsApi.FunctionState.checksum_wrong, "code2");

        r.processorCommContext = new ProcessorCommContextV3(11L, "session-11");

        CoreV3 core1 = new CoreV3("/dir-core1", 21L, "core-code1", "core-tags1");
        CoreV3 core2 = new CoreV3("/dir-core2", 22L, "core-code2", "core-tags2");

        req.cores.add(core1);
        req.cores.add(core2);

        return req;
    }

    private static void testAsserts(KeepAliveRequestParamYaml kar) {
        assertNotNull(kar.processor);

        assertNotNull(kar.processor.status);
        assertNotNull(kar.processor.status.env);

        assertEquals(2, kar.processor.status.env.envs.size());
        assertEquals("env-value1", kar.processor.status.env.envs.get("env-key1"));
        assertEquals("env-value2", kar.processor.status.env.envs.get("env-key2"));
        assertEquals(2, kar.processor.status.env.mirrors.size());
        assertEquals("mirror-value1", kar.processor.status.env.mirrors.get("mirror-key1"));
        assertEquals("mirror-value2", kar.processor.status.env.mirrors.get("mirror-key2"));
        assertEquals(2, kar.processor.status.env.disk.size());
        assertEquals("code1", kar.processor.status.env.disk.get(0).code);
        assertEquals("path1", kar.processor.status.env.disk.get(0).path);
        assertEquals("code2", kar.processor.status.env.disk.get(1).code);
        assertEquals("path2", kar.processor.status.env.disk.get(1).path);

        assertEquals(2, kar.processor.status.env.quotas.values.size());
        assertEquals(new KeepAliveRequestParamYaml.Quota("tag1", 15, true), kar.processor.status.env.quotas.values.get(0));
        assertEquals(new KeepAliveRequestParamYaml.Quota("tag2", 25, false), kar.processor.status.env.quotas.values.get(1));
        assertEquals(23, kar.processor.status.env.quotas.limit);
        assertEquals(4, kar.processor.status.env.quotas.defaultValue);
        assertTrue(kar.processor.status.env.quotas.disabled);

        assertNotNull(kar.processor.status.gitStatusInfo);

        assertEquals(Enums.GitStatus.unknown, kar.processor.status.gitStatusInfo.status);
        assertEquals("ver1", kar.processor.status.gitStatusInfo.version);
        assertEquals("no-error", kar.processor.status.gitStatusInfo.error);

        assertEquals("schedule1", kar.processor.status.schedule);
        assertEquals("192.168.0.17", kar.processor.status.ip);
        assertEquals("host-17", kar.processor.status.host);
        assertNotNull(kar.processor.status.errors);
        assertEquals(2, kar.processor.status.errors.size());
        assertEquals("error-1", kar.processor.status.errors.get(0));
        assertEquals("error-2", kar.processor.status.errors.get(1));
        assertTrue(kar.processor.status.logDownloadable);
        assertEquals(2, kar.processor.status.taskParamsVersion);
        assertEquals(EnumsApi.OS.any, kar.processor.status.os);
        assertEquals("/home", kar.processor.status.currDir);

        assertNotNull(kar.functions.statuses);
        assertEquals(2, kar.functions.statuses.size());
        assertTrue(kar.functions.statuses.containsKey(EnumsApi.FunctionState.none));
        assertEquals("code1", kar.functions.statuses.get(EnumsApi.FunctionState.none));
        assertTrue(kar.functions.statuses.containsKey(EnumsApi.FunctionState.checksum_wrong));
        assertEquals("code2", kar.functions.statuses.get(EnumsApi.FunctionState.checksum_wrong));

        assertNotNull(kar.processor.processorCommContext);
        assertEquals(11L, kar.processor.processorCommContext.processorId);
        assertEquals("session-11", kar.processor.processorCommContext.sessionId);


        assertEquals(2, kar.cores.size());

        assertEquals("/dir-core1", kar.cores.get(0).coreDir);
        assertEquals(21L, kar.cores.get(0).coreId);
        assertEquals("core-code1", kar.cores.get(0).coreCode);
        assertEquals("core-tags1", kar.cores.get(0).tags);

        assertEquals("/dir-core2", kar.cores.get(1).coreDir);
        assertEquals(22L, kar.cores.get(1).coreId);
        assertEquals("core-code2", kar.cores.get(1).coreCode);
        assertEquals("core-tags2", kar.cores.get(1).tags);

    }

    private static void testAssertsV3(KeepAliveRequestParamYamlV3 karV3) {
        assertNotNull(karV3.processor);

        assertNotNull(karV3.processor.status);
        assertNotNull(karV3.processor.status.env);

        assertEquals(2, karV3.processor.status.env.envs.size());
        assertEquals("env-value1", karV3.processor.status.env.envs.get("env-key1"));
        assertEquals("env-value2", karV3.processor.status.env.envs.get("env-key2"));
        assertEquals(2, karV3.processor.status.env.mirrors.size());
        assertEquals("mirror-value1", karV3.processor.status.env.mirrors.get("mirror-key1"));
        assertEquals("mirror-value2", karV3.processor.status.env.mirrors.get("mirror-key2"));
        assertEquals(2, karV3.processor.status.env.disk.size());
        assertEquals("code1", karV3.processor.status.env.disk.get(0).code);
        assertEquals("path1", karV3.processor.status.env.disk.get(0).path);
        assertEquals("code2", karV3.processor.status.env.disk.get(1).code);
        assertEquals("path2", karV3.processor.status.env.disk.get(1).path);

        assertEquals(2, karV3.processor.status.env.quotas.values.size());
        assertEquals(new KeepAliveRequestParamYamlV3.QuotaV3("tag1", 15, true), karV3.processor.status.env.quotas.values.get(0));
        assertEquals(new KeepAliveRequestParamYamlV3.QuotaV3("tag2", 25, false), karV3.processor.status.env.quotas.values.get(1));
        assertEquals(23, karV3.processor.status.env.quotas.limit);
        assertEquals(4, karV3.processor.status.env.quotas.defaultValue);
        assertTrue(karV3.processor.status.env.quotas.disabled);

        assertNotNull(karV3.processor.status.gitStatusInfo);

        assertEquals(Enums.GitStatus.unknown, karV3.processor.status.gitStatusInfo.status);
        assertEquals("ver1", karV3.processor.status.gitStatusInfo.version);
        assertEquals("no-error", karV3.processor.status.gitStatusInfo.error);

        assertEquals("schedule1", karV3.processor.status.schedule);
        assertEquals("192.168.0.17", karV3.processor.status.ip);
        assertEquals("host-17", karV3.processor.status.host);
        assertEquals("host-17", karV3.processor.status.host);
        assertNotNull(karV3.processor.status.errors);
        assertEquals(2, karV3.processor.status.errors.size());
        assertEquals("error-1", karV3.processor.status.errors.get(0));
        assertEquals("error-2", karV3.processor.status.errors.get(1));
        assertTrue(karV3.processor.status.logDownloadable);
        assertEquals(2, karV3.processor.status.taskParamsVersion);
        assertEquals(EnumsApi.OS.any, karV3.processor.status.os);
        assertEquals("/home", karV3.processor.status.currDir);

        assertNotNull(karV3.functions.statuses);
        assertEquals(2, karV3.functions.statuses.size());
        assertTrue(karV3.functions.statuses.containsKey(EnumsApi.FunctionState.none));
        assertEquals("code1", karV3.functions.statuses.get(EnumsApi.FunctionState.none));
        assertTrue(karV3.functions.statuses.containsKey(EnumsApi.FunctionState.checksum_wrong));
        assertEquals("code2", karV3.functions.statuses.get(EnumsApi.FunctionState.checksum_wrong));

        assertNotNull(karV3.processor.processorCommContext);
        assertEquals(11L, karV3.processor.processorCommContext.processorId);
        assertEquals("session-11", karV3.processor.processorCommContext.sessionId);

        assertEquals(2, karV3.cores.size());

        assertEquals("/dir-core1", karV3.cores.get(0).coreDir);
        assertEquals(21L, karV3.cores.get(0).coreId);
        assertEquals("core-code1", karV3.cores.get(0).coreCode);
        assertEquals("core-tags1", karV3.cores.get(0).tags);

        assertEquals("/dir-core2", karV3.cores.get(1).coreDir);
        assertEquals(22L, karV3.cores.get(1).coreId);
        assertEquals("core-code2", karV3.cores.get(1).coreCode);
        assertEquals("core-tags2", karV3.cores.get(1).tags);

    }

}
