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

package ai.metaheuristic.commons.json;

import ai.metaheuristic.commons.json.license.LicenseArtifactParamsJsonUtils;
import ai.metaheuristic.commons.json.license.LicenseClaimsUtils;
import ai.metaheuristic.commons.json.license.LicenseInstallationParamsJsonUtils;
import ai.metaheuristic.commons.json.meta_storage.MetaStorageRegistryParamsUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A registry of a JSON multi-versioning chain exposes exactly one thing: its BaseJsonUtils field.
 * Every read goes through BASE_JSON_UTILS.to(json), which sniffs the version from the JSON and runs
 * the whole upgrade chain; every write goes through BASE_JSON_UTILS.toString(params). A second public
 * entry point on a registry is a second chain driver, and nothing keeps it in step with the engine.
 *
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class JsonParamsRegistryShapeTest {

    private static final List<String> ONLY_BASE_JSON_UTILS = List.of("BASE_JSON_UTILS:BaseJsonUtils");

    private static List<String> publicMethods(Class<?> registry) {
        return Arrays.stream(registry.getDeclaredMethods())
                .filter(m -> !m.isSynthetic() && Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .sorted()
                .toList();
    }

    private static List<String> publicFields(Class<?> registry) {
        return Arrays.stream(registry.getDeclaredFields())
                .filter(f -> !f.isSynthetic() && Modifier.isPublic(f.getModifiers()))
                .map(f -> f.getName() + ":" + f.getType().getSimpleName())
                .sorted()
                .toList();
    }

    @Test
    public void test_licenseClaimsUtils_exposesOnlyBaseJsonUtils() {
        assertEquals(ONLY_BASE_JSON_UTILS, publicFields(LicenseClaimsUtils.class));
        assertEquals(List.of(), publicMethods(LicenseClaimsUtils.class),
                "LicenseClaimsUtils must expose no public method - read with BASE_JSON_UTILS.to(json)");
    }

    @Test
    public void test_licenseArtifactParamsJsonUtils_exposesOnlyBaseJsonUtils() {
        assertEquals(ONLY_BASE_JSON_UTILS, publicFields(LicenseArtifactParamsJsonUtils.class));
        assertEquals(List.of(), publicMethods(LicenseArtifactParamsJsonUtils.class));
    }

    @Test
    public void test_licenseInstallationParamsJsonUtils_exposesOnlyBaseJsonUtils() {
        assertEquals(ONLY_BASE_JSON_UTILS, publicFields(LicenseInstallationParamsJsonUtils.class));
        assertEquals(List.of(), publicMethods(LicenseInstallationParamsJsonUtils.class));
    }

    @Test
    public void test_metaStorageRegistryParamsUtils_exposesOnlyBaseJsonUtils() {
        assertEquals(ONLY_BASE_JSON_UTILS, publicFields(MetaStorageRegistryParamsUtils.class));
        assertEquals(List.of(), publicMethods(MetaStorageRegistryParamsUtils.class));
    }
}
