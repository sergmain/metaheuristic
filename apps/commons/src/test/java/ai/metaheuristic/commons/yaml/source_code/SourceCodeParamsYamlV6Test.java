/*
 * Metaheuristic, Copyright (C) 2017-2025, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml.source_code;

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV5;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYamlV6;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SourceCodeParamsYaml version 6 and upgrade from V5 to V6.
 */
public class SourceCodeParamsYamlV6Test {

    @Test
    public void testV6_withCondition() {
        // Build a V6 YAML with a process that has a ConditionV6
        SourceCodeParamsYamlV6 v6 = new SourceCodeParamsYamlV6();
        v6.source.uid = "test-source-code-v6";

        SourceCodeParamsYamlV6.ProcessV6 process = new SourceCodeParamsYamlV6.ProcessV6();
        process.name = "test-process";
        process.code = "mh.nop";
        process.function = new SourceCodeParamsYamlV6.FunctionDefForSourceCodeV6("mh.nop", null, EnumsApi.FunctionExecContext.internal, EnumsApi.FunctionRefType.code);
        process.condition = new SourceCodeParamsYamlV6.ConditionV6("some-var > 0");
        v6.source.processes.add(process);

        // Serialize V6 to YAML
        @SuppressWarnings("unchecked")
        String yaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(6).toString(v6);
        assertNotNull(yaml);
        assertTrue(yaml.contains("version: 6"));

        // Deserialize through the full chain → should produce current SourceCodeParamsYaml
        SourceCodeParamsYaml current = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        assertNotNull(current);
        assertEquals(6, current.version);
        assertEquals("test-source-code-v6", current.source.uid);
        assertNotNull(current.source.processes);
        assertEquals(1, current.source.processes.size());

        SourceCodeParamsYaml.Process p = current.source.processes.get(0);
        assertEquals("mh.nop", p.code);

        // condition should have been mapped from ConditionV6 to Condition with default skipPolicy
        assertNotNull(p.condition);
        assertEquals("some-var > 0", p.condition.conditions);
        assertEquals(EnumsApi.SkipPolicy.normal, p.condition.skipPolicy);
    }

    @Test
    public void testV6_withoutCondition() {
        SourceCodeParamsYamlV6 v6 = new SourceCodeParamsYamlV6();
        v6.source.uid = "test-no-condition";

        SourceCodeParamsYamlV6.ProcessV6 process = new SourceCodeParamsYamlV6.ProcessV6();
        process.name = "simple-process";
        process.code = "some-func";
        process.function = new SourceCodeParamsYamlV6.FunctionDefForSourceCodeV6("some-func");
        // no condition set
        v6.source.processes.add(process);

        @SuppressWarnings("unchecked")
        String yaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(6).toString(v6);
        SourceCodeParamsYaml current = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertNotNull(current);
        assertEquals(1, current.source.processes.size());
        assertNull(current.source.processes.get(0).condition);
    }

    @Test
    public void testUpgradeV5toV6_withCondition() {
        // Build a V5 YAML with a condition (String field)
        SourceCodeParamsYamlV5 v5 = new SourceCodeParamsYamlV5();
        v5.source.uid = "test-v5-to-v6";

        SourceCodeParamsYamlV5.ProcessV5 process = new SourceCodeParamsYamlV5.ProcessV5();
        process.name = "conditional-process";
        process.code = "mh.nop";
        process.function = new SourceCodeParamsYamlV5.FunctionDefForSourceCodeV5("mh.nop", null, EnumsApi.FunctionExecContext.internal, EnumsApi.FunctionRefType.code);
        process.condition = "has-objectives";
        process.triesAfterError = 3;
        v5.source.processes.add(process);

        // Serialize V5 to YAML
        @SuppressWarnings("unchecked")
        String yaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(5).toString(v5);
        assertNotNull(yaml);
        assertTrue(yaml.contains("version: 5"));

        // Parse through the full upgrade chain: V5 → V6 → current
        SourceCodeParamsYaml current = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        assertNotNull(current);
        assertEquals(6, current.version);
        assertEquals("test-v5-to-v6", current.source.uid);
        assertEquals(1, current.source.processes.size());

        SourceCodeParamsYaml.Process p = current.source.processes.get(0);
        assertEquals("mh.nop", p.code);
        assertEquals(3, p.triesAfterError);

        // V5 String condition should be converted to Condition with default skipPolicy=normal
        assertNotNull(p.condition);
        assertEquals("has-objectives", p.condition.conditions);
        assertEquals(EnumsApi.SkipPolicy.normal, p.condition.skipPolicy);
    }

    @Test
    public void testUpgradeV5toV6_withoutCondition() {
        SourceCodeParamsYamlV5 v5 = new SourceCodeParamsYamlV5();
        v5.source.uid = "test-v5-no-condition";

        SourceCodeParamsYamlV5.ProcessV5 process = new SourceCodeParamsYamlV5.ProcessV5();
        process.name = "simple";
        process.code = "some-func";
        process.function = new SourceCodeParamsYamlV5.FunctionDefForSourceCodeV5("some-func");
        v5.source.processes.add(process);

        @SuppressWarnings("unchecked")
        String yaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(5).toString(v5);
        SourceCodeParamsYaml current = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertNotNull(current);
        assertEquals(1, current.source.processes.size());
        assertNull(current.source.processes.get(0).condition);
    }

    @Test
    public void testCurrentVersionWithConditionObject() {
        // Build the current (version-less) SourceCodeParamsYaml with the new Condition class directly
        SourceCodeParamsYaml sc = new SourceCodeParamsYaml();
        sc.source.uid = "test-current-condition";

        SourceCodeParamsYaml.Process process = new SourceCodeParamsYaml.Process();
        process.name = "with-skip-policy";
        process.code = "mh.nop";
        process.function = new SourceCodeParamsYaml.FunctionDefForSourceCode("mh.nop", null, EnumsApi.FunctionExecContext.internal, EnumsApi.FunctionRefType.code);
        process.condition = new SourceCodeParamsYaml.Condition("flag-var == true", EnumsApi.SkipPolicy.children);
        sc.source.processes.add(process);

        // Serialize and deserialize the current version
        String yaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(sc);
        assertNotNull(yaml);
        assertTrue(yaml.contains("version: 6"));

        SourceCodeParamsYaml restored = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);
        assertNotNull(restored);
        assertEquals(6, restored.version);
        assertEquals(1, restored.source.processes.size());

        SourceCodeParamsYaml.Process p = restored.source.processes.get(0);
        assertNotNull(p.condition);
        assertEquals("flag-var == true", p.condition.conditions);
        assertEquals(EnumsApi.SkipPolicy.children, p.condition.skipPolicy);
    }

    @Test
    public void testCurrentVersionWithConditionDefaultSkipPolicy() {
        SourceCodeParamsYaml sc = new SourceCodeParamsYaml();
        sc.source.uid = "test-default-skip-policy";

        SourceCodeParamsYaml.Process process = new SourceCodeParamsYaml.Process();
        process.name = "default-policy";
        process.code = "mh.nop";
        process.function = new SourceCodeParamsYaml.FunctionDefForSourceCode("mh.nop", null, EnumsApi.FunctionExecContext.internal, EnumsApi.FunctionRefType.code);
        process.condition = new SourceCodeParamsYaml.Condition("some-condition");
        sc.source.processes.add(process);

        String yaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.toString(sc);
        SourceCodeParamsYaml restored = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertNotNull(restored.source.processes.get(0).condition);
        assertEquals("some-condition", restored.source.processes.get(0).condition.conditions);
        assertEquals(EnumsApi.SkipPolicy.normal, restored.source.processes.get(0).condition.skipPolicy);
    }

    @Test
    public void testV5toCurrentWithSubProcesses() {
        // V5 with subprocesses that also have conditions
        SourceCodeParamsYamlV5 v5 = new SourceCodeParamsYamlV5();
        v5.source.uid = "test-v5-subprocesses";

        SourceCodeParamsYamlV5.ProcessV5 childProcess = new SourceCodeParamsYamlV5.ProcessV5();
        childProcess.name = "child";
        childProcess.code = "child-func";
        childProcess.function = new SourceCodeParamsYamlV5.FunctionDefForSourceCodeV5("child-func");
        childProcess.condition = "child-condition > 0";

        SourceCodeParamsYamlV5.ProcessV5 parentProcess = new SourceCodeParamsYamlV5.ProcessV5();
        parentProcess.name = "parent";
        parentProcess.code = "parent-func";
        parentProcess.function = new SourceCodeParamsYamlV5.FunctionDefForSourceCodeV5("parent-func");
        parentProcess.condition = "parent-condition";
        parentProcess.subProcesses = new SourceCodeParamsYamlV5.SubProcessesV5(
                EnumsApi.SourceCodeSubProcessLogic.sequential,
                java.util.List.of(childProcess)
        );

        v5.source.processes.add(parentProcess);

        @SuppressWarnings("unchecked")
        String yaml = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.getForVersion(5).toString(v5);
        SourceCodeParamsYaml current = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(yaml);

        assertNotNull(current);
        assertEquals(1, current.source.processes.size());

        SourceCodeParamsYaml.Process parent = current.source.processes.get(0);
        assertNotNull(parent.condition);
        assertEquals("parent-condition", parent.condition.conditions);
        assertEquals(EnumsApi.SkipPolicy.normal, parent.condition.skipPolicy);

        assertNotNull(parent.subProcesses);
        assertNotNull(parent.subProcesses.processes);
        assertEquals(1, parent.subProcesses.processes.size());

        SourceCodeParamsYaml.Process child = parent.subProcesses.processes.get(0);
        assertNotNull(child.condition);
        assertEquals("child-condition > 0", child.condition.conditions);
        assertEquals(EnumsApi.SkipPolicy.normal, child.condition.skipPolicy);
    }
}
