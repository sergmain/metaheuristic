/*
 * Metaheuristic, Copyright (C) 2017-2020, Innovation platforms, LLC
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
import ai.metaheuristic.ai.dispatcher.processor.ProcessorTransactionService;
import ai.metaheuristic.ai.processor.sourcing.git.GitSourcingService;
import ai.metaheuristic.ai.yaml.communication.keep_alive.KeepAliveRequestParamYaml;
import ai.metaheuristic.ai.yaml.processor_status.ProcessorStatusYaml;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.yaml.env.DiskStorage;
import ai.metaheuristic.commons.yaml.env.EnvYaml;
import org.junit.jupiter.api.Test;

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

    @Test
    public void test() {
        String sessionId = "sessionId";
        ProcessorStatusYaml psy = new ProcessorStatusYaml();
        psy.downloadStatuses= List.of();
//        psy.env=null;
        GitSourcingService.GitStatusInfo gitStatusInfoAsNull = new GitSourcingService.GitStatusInfo(Enums.GitStatus.unknown, null, null);

        psy.gitStatusInfo= gitStatusInfoAsNull;
        psy.schedule="0:00 - 23:59";
        psy.sessionId="612aeeb0-7a4f-4bd8-95c6-2b4f138a9cc5-182dedd9-3d98-47ae-9688-7082e424f74c";
        psy.sessionCreatedOn=1606910626645L;
        psy.ip="[unknown]";
        psy.host="[unknown]";
//        psy.errors=null;
        psy.logDownloadable=true;
        psy.taskParamsVersion=1;
        psy.os= EnumsApi.OS.unknown;
        psy.currDir="/users/xxx";

        final KeepAliveRequestParamYaml.ReportProcessor ss = new KeepAliveRequestParamYaml.ReportProcessor (
                new EnvYaml(),
                new GitSourcingService.GitStatusInfo(Enums.GitStatus.installed, "Git 1.0.0", null),
                "0:00 - 23:59",
                sessionId,
                System.currentTimeMillis(),
                "[unknown]", "[unknown]", null, true,
                1, EnumsApi.OS.unknown, "/users/yyy");

//        ProcessorStatusYaml ss1 = ProcessorStatusYamlUtils.BASE_YAML_UTILS.to(s.status);
        assertTrue(ProcessorTransactionService.isProcessorStatusDifferent(psy, ss), S.f("ss1:\n%s\n\nss:\n%s", psy, ss));

        psy.gitStatusInfo=gitStatusInfoAsNull;
        ss.currDir = "/users/xxx";
        assertTrue(ProcessorTransactionService.isProcessorStatusDifferent(psy, ss), S.f("ss1:\n%s\n\nss:\n%s", psy, ss));

        psy.gitStatusInfo=new GitSourcingService.GitStatusInfo(Enums.GitStatus.installed, "Git 1.0.0", null);
        assertFalse(ProcessorTransactionService.isProcessorStatusDifferent(psy, ss), S.f("ss1:\n%s\n\nss:\n%s", psy, ss));


        assertFalse(ProcessorTransactionService.envNotEquals(null, null));
        assertFalse(ProcessorTransactionService.envNotEquals(null, new EnvYaml()));
        assertFalse(ProcessorTransactionService.envNotEquals(new EnvYaml(), null));
        assertFalse(ProcessorTransactionService.envNotEquals(new EnvYaml(), new EnvYaml()));
        assertFalse(ProcessorTransactionService.envNotEquals(new EnvYaml(""), new EnvYaml()));
        assertFalse(ProcessorTransactionService.envNotEquals(new EnvYaml(), new EnvYaml("")));

        assertFalse(ProcessorTransactionService.envNotEquals(new EnvYaml(""), new EnvYaml("")));
        assertFalse(ProcessorTransactionService.envNotEquals(new EnvYaml("aaa"), new EnvYaml("aaa")));
        assertFalse(ProcessorTransactionService.envNotEquals(
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(), "aaa"),
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(), "aaa")));

        assertFalse(ProcessorTransactionService.envNotEquals(
                new EnvYaml(Map.of(), Map.of(), List.of(new DiskStorage("c", "p")), "aaa"),
                new EnvYaml(Map.of(), Map.of(), List.of(new DiskStorage("c", "p")), "aaa")));

        assertTrue(ProcessorTransactionService.envNotEquals(
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c", "p")), "aaa"),
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c", "p")), "bbb")));

        assertTrue(ProcessorTransactionService.envNotEquals(
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c", "p")), "aaa"),
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c", "p")), "bbb")));

        assertTrue(ProcessorTransactionService.envNotEquals(
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c", "p")), "aaa"),
                new EnvYaml(Map.of("q","11"), Map.of("w", "2"), List.of(new DiskStorage("c", "p")), "aaa")));


        assertTrue(ProcessorTransactionService.envNotEquals(
                new EnvYaml(Map.of("q","11"), Map.of("w", "2"), List.of(new DiskStorage("c", "p")), "aaa"),
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c", "p")), "aaa")));

        assertTrue(ProcessorTransactionService.envNotEquals(
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c", "p")), "aaa"),
                new EnvYaml(Map.of("q","1"), Map.of("w", "22"), List.of(new DiskStorage("c", "p")), "aaa")));

        assertTrue(ProcessorTransactionService.envNotEquals(
                new EnvYaml(Map.of("q","1"), Map.of("w", "22"), List.of(new DiskStorage("c", "p")), "aaa"),
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c", "p")), "aaa")));

        assertTrue(ProcessorTransactionService.envNotEquals(
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c1", "p")), "aaa"),
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c2", "p")), "aaa")));

        assertTrue(ProcessorTransactionService.envNotEquals(
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c", "p1")), "aaa"),
                new EnvYaml(Map.of("q","1"), Map.of("w", "2"), List.of(new DiskStorage("c", "p2")), "aaa")));


    }
}
