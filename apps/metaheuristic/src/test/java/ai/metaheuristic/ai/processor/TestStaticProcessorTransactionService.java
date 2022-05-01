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

package ai.metaheuristic.ai.processor;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.dispatcher.processor.ProcessorUtils;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Serge
 * Date: 12/2/2020
 * Time: 5:07 AM
 */
public class TestStaticProcessorTransactionService {

    private static ProcessorStatusYaml.Env createProcessorStatusYamlEnvYaml(Map<String, String> mirrors, Map<String, String> envs, List<ProcessorStatusYaml.DiskStorage> disk, @Nullable String tags) {
        return createProcessorStatusYamlEnvYaml(mirrors, envs, disk, tags, null);
    }

    private static ProcessorStatusYaml.Env createProcessorStatusYamlEnvYaml(
            Map<String, String> mirrors, Map<String, String> envs, List<ProcessorStatusYaml.DiskStorage> disk, @Nullable String tags, @Nullable ProcessorStatusYaml.Quotas quotas) {
        ProcessorStatusYaml.Env envYaml = new ProcessorStatusYaml.Env();
        envYaml.mirrors.putAll(mirrors);
        envYaml.envs.putAll(envs);
        envYaml.disk.addAll(disk);
        if (quotas!=null) {
            envYaml.quotas.limit = quotas.limit;
            envYaml.quotas.values.addAll(quotas.values);
        }
        return envYaml;
    }

    private static KeepAliveRequestParamYaml.Env createEnvYaml(Map<String, String> mirrors, Map<String, String> envs, List<KeepAliveRequestParamYaml.DiskStorage> disk, @Nullable String tags) {
        return createEnvYaml(mirrors, envs, disk, tags, null);
    }

    private static KeepAliveRequestParamYaml.Env createEnvYaml(
            Map<String, String> mirrors, Map<String, String> envs, List<KeepAliveRequestParamYaml.DiskStorage> disk, @Nullable String tags, @Nullable KeepAliveRequestParamYaml.Quotas quotas) {
        KeepAliveRequestParamYaml.Env envYaml = new KeepAliveRequestParamYaml.Env();
        envYaml.mirrors.putAll(mirrors);
        envYaml.envs.putAll(envs);
        envYaml.disk.addAll(disk);
        if (quotas!=null) {
            envYaml.quotas.limit = quotas.limit;
            envYaml.quotas.defaultValue = quotas.defaultValue;
            envYaml.quotas.disabled = quotas.disabled;
            envYaml.quotas.values.addAll(quotas.values);
        }
        return envYaml;
    }

    @Test
    public void test() {
        ProcessorStatusYaml psy = new ProcessorStatusYaml();

        GitSourcingService.GitStatusInfo gitStatusInfoAsNull = new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown, null, null);

        psy.gitStatusInfo= gitStatusInfoAsNull;
        psy.schedule="0:00 - 23:59";
        psy.sessionId="612aeeb0-7a4f-4bd8-95c6-2b4f138a9cc5-182dedd9-3d98-47ae-9688-7082e424f74c";
        psy.sessionCreatedOn=1606910626645L;
        psy.ip="[unknown]";
        psy.host="[unknown]";
        psy.logDownloadable=true;
        psy.taskParamsVersion=1;
        psy.os= EnumsApi.OS.unknown;
        psy.currDir="/users/xxx";

        final KeepAliveRequestParamYaml.ProcessorStatus ss = new KeepAliveRequestParamYaml.ProcessorStatus (
                new KeepAliveRequestParamYaml.Env(),
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.installed, "Git 1.0.0", null),
                "0:00 - 23:59",
                "[unknown]", "[unknown]",  true,
                1, EnumsApi.OS.unknown, "/users/yyy", null);

//        ProcessorStatusYaml ss1 = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(s.status);
        assertTrue(ProcessorUtils.isProcessorStatusDifferent(psy, ss), S.f("ss1:\n%s\n\nss:\n%s", psy, ss));

        psy.gitStatusInfo=gitStatusInfoAsNull;
        ss.currDir = "/users/xxx";
        assertTrue(ProcessorUtils.isProcessorStatusDifferent(psy, ss), S.f("ss1:\n%s\n\nss:\n%s", psy, ss));

        psy.gitStatusInfo=new GitSourcingService.GitStatusInfo(Enums.GitStatus.installed, "Git 1.0.0", null);
        assertFalse(ProcessorUtils.isProcessorStatusDifferent(psy, ss), S.f("ss1:\n%s\n\nss:\n%s", psy, ss));


        assertFalse(ProcessorUtils.envNotEquals(null, null));
        assertFalse(ProcessorUtils.envNotEquals(null, new KeepAliveRequestParamYaml.Env()));
        assertFalse(ProcessorUtils.envNotEquals(new ProcessorStatusYaml.Env(), null));
        assertFalse(ProcessorUtils.envNotEquals(new ProcessorStatusYaml.Env(), new KeepAliveRequestParamYaml.Env()));
        assertFalse(ProcessorUtils.envNotEquals(new ProcessorStatusYaml.Env(), new KeepAliveRequestParamYaml.Env()));
        assertFalse(ProcessorUtils.envNotEquals(new ProcessorStatusYaml.Env(), new KeepAliveRequestParamYaml.Env()));

        assertFalse(ProcessorUtils.envNotEquals(new ProcessorStatusYaml.Env(), new KeepAliveRequestParamYaml.Env()));
        assertFalse(ProcessorUtils.envNotEquals(new ProcessorStatusYaml.Env(), new KeepAliveRequestParamYaml.Env()));
        assertFalse(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(), "aaa"),
                createEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(), "aaa")));
        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of("q1","1"), Map.of("w", "2"), List.of(), "aaa"),
                createEnvYaml(Map.of("q2","1"), Map.of("w", "2"), List.of(), "aaa")));

        assertFalse(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of(), Map.of(), List.of(new ProcessorStatusYaml.DiskStorage("c", "p")), "aaa"),
                createEnvYaml(Map.of(), Map.of(), List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p")), "aaa")));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of(), Map.of(), List.of(new ProcessorStatusYaml.DiskStorage("c", "p")), "aaa1"),
                createEnvYaml(Map.of(), Map.of(), List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p")), "aaa")));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new ProcessorStatusYaml.DiskStorage("c", "p")), "aaa"),
                createEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p")), "bbb")));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new ProcessorStatusYaml.DiskStorage("c", "p")), "aaa"),
                createEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p")), "bbb")));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new ProcessorStatusYaml.DiskStorage("c", "p")), "aaa"),
                createEnvYaml(Map.of("q","11"), Map.of("w", "2"), List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p")), "aaa")));


        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of("q","11"), Map.of("w", "2"), List.of(new ProcessorStatusYaml.DiskStorage("c", "p")), "aaa"),
                createEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p")), "aaa")));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new ProcessorStatusYaml.DiskStorage("c", "p")), "aaa"),
                createEnvYaml(Map.of("q","1"), Map.of("w", "22"), List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p")), "aaa")));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of("q","1"), Map.of("w", "22"), List.of(new ProcessorStatusYaml.DiskStorage("c", "p")), "aaa"),
                createEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p")), "aaa")));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new ProcessorStatusYaml.DiskStorage("c1", "p")), "aaa"),
                createEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new KeepAliveRequestParamYaml.DiskStorage("c2", "p")), "aaa")));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new ProcessorStatusYaml.DiskStorage("c", "p1")), "aaa"),
                createEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p2")), "aaa")));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(
                        Map.of("q","1"), Map.of("w", "2"),
                        List.of(new ProcessorStatusYaml.DiskStorage("c", "p1")), "aaa",
                        new ProcessorStatusYaml.Quotas(List.of(new ProcessorStatusYaml.Quota("t1", 11, false), new ProcessorStatusYaml.Quota("t2", 12, false)), 42, 3, false)),
                createEnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p1")), "aaa")));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(
                        Map.of("q","1"), Map.of("w", "2"),
                        List.of(new ProcessorStatusYaml.DiskStorage("c", "p1")), "aaa",
                        null),
                createEnvYaml(
                        Map.of("q","1"), Map.of("w", "2"),
                        List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p1")), "aaa",
                        new KeepAliveRequestParamYaml.Quotas(List.of(new KeepAliveRequestParamYaml.Quota("t1", 11, false), new KeepAliveRequestParamYaml.Quota("t2", 12, false)), 42, 3, false))));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(
                        Map.of("q","1"), Map.of("w", "2"),
                        List.of(new ProcessorStatusYaml.DiskStorage("c", "p1")), "aaa",
                        new ProcessorStatusYaml.Quotas(List.of(new ProcessorStatusYaml.Quota("t1", 11, false), new ProcessorStatusYaml.Quota("t2", 12, false)), 42, 3, false)),
                createEnvYaml(
                        Map.of("q","1"), Map.of("w", "2"),
                        List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p1")), "aaa",
                        new KeepAliveRequestParamYaml.Quotas(List.of(new KeepAliveRequestParamYaml.Quota("t1", 12, false), new KeepAliveRequestParamYaml.Quota("t2", 12, false)), 42, 3, false))));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(
                        Map.of("q","1"), Map.of("w", "2"),
                        List.of(new ProcessorStatusYaml.DiskStorage("c", "p1")), "aaa",
                        new ProcessorStatusYaml.Quotas(List.of(new ProcessorStatusYaml.Quota("t1", 11, false), new ProcessorStatusYaml.Quota("t2", 12, false)), 42, 3, false)),
                createEnvYaml(
                        Map.of("q","1"), Map.of("w", "2"),
                        List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p1")), "aaa",
                        new KeepAliveRequestParamYaml.Quotas(List.of(new KeepAliveRequestParamYaml.Quota("t1", 11, false), new KeepAliveRequestParamYaml.Quota("t2", 14, false)), 42, 3, false))));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(
                        Map.of("q","1"), Map.of("w", "2"),
                        List.of(new ProcessorStatusYaml.DiskStorage("c", "p1")), "aaa",
                        new ProcessorStatusYaml.Quotas(List.of(new ProcessorStatusYaml.Quota("t1", 11, false), new ProcessorStatusYaml.Quota("t2", 12, false)), 42, 3, false)),
                createEnvYaml(
                        Map.of("q","1"), Map.of("w", "2"),
                        List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p1")), "aaa",
                        new KeepAliveRequestParamYaml.Quotas(List.of(new KeepAliveRequestParamYaml.Quota("t1", 11, false), new KeepAliveRequestParamYaml.Quota("t2", 12, false)), 44, 3, false))));

        assertTrue(ProcessorUtils.envNotEquals(
                createProcessorStatusYamlEnvYaml(
                        Map.of("q","1"), Map.of("w", "2"),
                        List.of(new ProcessorStatusYaml.DiskStorage("c", "p1")), "aaa",
                        new ProcessorStatusYaml.Quotas(List.of(new ProcessorStatusYaml.Quota("t1", 11, false), new ProcessorStatusYaml.Quota("t2", 12, false)), 42, 3, false)),
                createEnvYaml(
                        Map.of("q","1"), Map.of("w", "2"),
                        List.of(new KeepAliveRequestParamYaml.DiskStorage("c", "p1")), "aaa",
                        new KeepAliveRequestParamYaml.Quotas(List.of(new KeepAliveRequestParamYaml.Quota("t1", 11, false)), 44, 3, false))));


    }
}
