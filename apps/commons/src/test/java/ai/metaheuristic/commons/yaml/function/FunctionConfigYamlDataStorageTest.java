/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.commons.yaml.function;

import ai.metaheuristic.api.ConstsApi;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.sourcing.DiskInfo;
import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.CommonConsts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * DataStorage moved out of FunctionData.params into FunctionConfigYaml, which is Function.params.
 * Pins the three things that move can break: the round-trip through the V3 schema, the upgrade of a
 * descriptor written before the field existed, and the packager's promise that a bundled function.yaml
 * comes out carrying nothing the author didn't put in.
 */
@Execution(CONCURRENT)
public class FunctionConfigYamlDataStorageTest {

    private static FunctionConfigYaml minimalConfig() {
        FunctionConfigYaml fc = new FunctionConfigYaml();
        fc.function.code = "fn:1.0";
        fc.function.type = "type";
        fc.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        Objects.requireNonNull(fc.function.targets)
                .put(CommonConsts.MH_DEFAULT_OS_KEY, new FunctionConfigYaml.Target("src", "fn.jar"));
        Objects.requireNonNull(fc.function.metas).add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));
        return fc;
    }

    @Test
    public void test_dataStorageDefaultsToNull() {
        assertNull(new FunctionConfigYaml().dataStorage);
        assertNull(new FunctionConfigYamlV3().dataStorage);
    }

    @Test
    public void test_fullDataStorage_roundTrips() {
        FunctionConfigYaml fc = minimalConfig();
        FunctionConfigYaml.DataStorage ds = new FunctionConfigYaml.DataStorage();
        ds.name = "fn:1.0";
        ds.sourcing = EnumsApi.DataSourcing.dispatcher;
        ds.git = new GitInfo("repo", "branch", "commit", "path");
        ds.disk = new DiskInfo("mask", "code", "path");
        ds.type = EnumsApi.VariableType.binary;
        ds.size = 42L;
        ds.checksumMap = new HashMap<>(Map.of(EnumsApi.HashAlgo.SHA256, "qwe321"));
        fc.dataStorage = ds;

        String yaml = FunctionConfigYamlUtils.UTILS.toString(fc);
        FunctionConfigYaml loaded = FunctionConfigYamlUtils.UTILS.to(yaml);

        assertNotNull(loaded.dataStorage, "dataStorage must survive the dump/load through the V3 schema");
        assertEquals("fn:1.0", loaded.dataStorage.name);
        assertEquals(EnumsApi.DataSourcing.dispatcher, loaded.dataStorage.sourcing);
        assertNotNull(loaded.dataStorage.git);
        assertEquals("repo", loaded.dataStorage.git.repo);
        assertEquals("commit", loaded.dataStorage.git.commit);
        assertNotNull(loaded.dataStorage.disk);
        assertEquals("mask", loaded.dataStorage.disk.mask);
        assertEquals(EnumsApi.VariableType.binary, loaded.dataStorage.type);
        assertEquals(42L, loaded.dataStorage.size);
        assertNotNull(loaded.dataStorage.checksumMap);
        assertEquals("qwe321", loaded.dataStorage.checksumMap.get(EnumsApi.HashAlgo.SHA256));
    }

    @Test
    public void test_sourcingAndName_roundTrip() {
        FunctionConfigYaml fc = minimalConfig();
        fc.dataStorage = new FunctionConfigYaml.DataStorage(EnumsApi.DataSourcing.dispatcher, "fn:1.0");

        FunctionConfigYaml loaded = FunctionConfigYamlUtils.UTILS.to(FunctionConfigYamlUtils.UTILS.toString(fc));

        assertNotNull(loaded.dataStorage);
        assertEquals(EnumsApi.DataSourcing.dispatcher, loaded.dataStorage.sourcing);
        assertEquals("fn:1.0", loaded.dataStorage.name);
        assertNull(loaded.dataStorage.git);
        assertNull(loaded.dataStorage.disk);
        assertNull(loaded.dataStorage.size);
    }

    /**
     * The packager's round-trip: BundleUtils reads function.yaml, may touch checksums/archive, and
     * writes it back. A descriptor with no dataStorage must come out with no dataStorage.
     */
    @Test
    public void test_descriptorWithoutDataStorage_gainsNothingOnRoundTrip() {
        FunctionConfigYaml fc = minimalConfig();
        assertNull(fc.dataStorage);

        String yaml = FunctionConfigYamlUtils.UTILS.toString(fc);
        assertFalse(yaml.contains("dataStorage"), "packaged function.yaml must not gain a dataStorage key, actual:\n" + yaml);

        FunctionConfigYaml loaded = FunctionConfigYamlUtils.UTILS.to(yaml);
        assertNull(loaded.dataStorage);
        assertEquals(yaml, FunctionConfigYamlUtils.UTILS.toString(loaded), "a second packaging pass must be a no-op");
    }

    @Test
    public void test_v3DocumentWithoutDataStorage_upgradesToNull() {
        FunctionConfigYamlV3 v3 = new FunctionConfigYamlV3();
        v3.function.code = "fn:1.0";
        v3.function.type = "type";
        v3.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        Objects.requireNonNull(v3.function.targets)
                .put(CommonConsts.MH_DEFAULT_OS_KEY, new FunctionConfigYamlV3.TargetV3("src", "fn.jar"));
        Objects.requireNonNull(v3.function.metas).add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        String yamlV3 = new FunctionConfigYamlUtilsV3().toString(v3);
        FunctionConfigYaml upgraded = FunctionConfigYamlUtils.UTILS.to(yamlV3);

        assertEquals(3, upgraded.version);
        assertNull(upgraded.dataStorage, "a descriptor stored before dataStorage existed must upgrade to null, not to an empty DataStorage");
    }

    @Test
    public void test_legacyV2Yaml_upgradesToNullDataStorage() {
        FunctionConfigYamlV2 v2 = new FunctionConfigYamlV2();
        v2.function.code = "fn:1.0";
        v2.function.type = "type";
        v2.function.file = "fn.jar";
        v2.function.src = "src";
        v2.function.sourcing = EnumsApi.FunctionSourcing.dispatcher;
        Objects.requireNonNull(v2.function.metas).add(Map.of(ConstsApi.META_MH_TASK_PARAMS_VERSION, "1"));

        FunctionConfigYaml upgraded = FunctionConfigYamlUtils.UTILS.to(new FunctionConfigYamlUtilsV2().toString(v2));

        assertNull(upgraded.dataStorage);
    }

    @Test
    public void test_clone_copiesDataStorageDeeply() {
        FunctionConfigYaml fc = minimalConfig();
        fc.dataStorage = new FunctionConfigYaml.DataStorage(EnumsApi.DataSourcing.dispatcher, "fn:1.0");
        fc.dataStorage.checksumMap = new HashMap<>(Map.of(EnumsApi.HashAlgo.SHA256, "qwe321"));

        FunctionConfigYaml clone = fc.clone();

        // mutate the original after cloning - the clone must not see it
        fc.dataStorage.name = "changed";
        fc.dataStorage.checksumMap.put(EnumsApi.HashAlgo.MD5, "112233");

        assertNotNull(clone.dataStorage);
        assertEquals("fn:1.0", clone.dataStorage.name);
        assertNotNull(clone.dataStorage.checksumMap);
        assertEquals(1, clone.dataStorage.checksumMap.size());
        assertNull(clone.dataStorage.checksumMap.get(EnumsApi.HashAlgo.MD5));
    }

    @Test
    public void test_clone_keepsNullDataStorageNull() {
        FunctionConfigYaml fc = minimalConfig();
        assertNull(fc.clone().dataStorage);
    }
}
