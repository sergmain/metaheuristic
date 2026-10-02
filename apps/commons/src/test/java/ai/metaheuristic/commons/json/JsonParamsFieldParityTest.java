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

import ai.metaheuristic.api.data.exec_context.ExecContextParams;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsV1;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParams;
import ai.metaheuristic.api.data.experiment_result.ExperimentResultParamsV2;
import ai.metaheuristic.api.data.license.LicenseArtifactParams;
import ai.metaheuristic.api.data.license.LicenseArtifactParamsV1;
import ai.metaheuristic.api.data.license.LicenseClaims;
import ai.metaheuristic.api.data.license.LicenseClaimsV1;
import ai.metaheuristic.api.data.license.LicenseInstallationParams;
import ai.metaheuristic.api.data.license.LicenseInstallationParamsV1;
import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParams;
import ai.metaheuristic.api.data.meta_storage.MetaStorageRegistryParamsV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * multi-versioning-mechanic.md, CRITICAL RULE: the version-less class and the highest-numbered
 * versioned class MUST HAVE THE SAME SET OF FIELDS. Serialization goes through the version-less class
 * and deserialization through the versioned one, and the shared mapper runs with
 * FAIL_ON_UNKNOWN_PROPERTIES=false - so a field on one side only is not an error, it is a value that
 * silently does not survive a round trip.
 *
 * <p>The schema of a DTO is every instance field of the root class and of every nested class reachable
 * from it through field types or their type arguments. Nested classes are compared by name with the
 * {@code V<N>} suffix removed, so {@code ProcessV1.inputs} and {@code Process.inputs} are the same entry.
 *
 * @author Serge
 */
@Execution(ExecutionMode.CONCURRENT)
public class JsonParamsFieldParityTest {

    private static List<String> diff(Class<?> versionLess, Class<?> head, Set<String> notStored) {
        final SortedSet<String> current = schema(versionLess);
        current.removeAll(notStored);
        final SortedSet<String> latest = schema(head);
        final List<String> r = new ArrayList<>();
        current.stream().filter(o -> !latest.contains(o)).forEach(o -> r.add("version-less only: " + o));
        latest.stream().filter(o -> !current.contains(o)).forEach(o -> r.add("head only: " + o));
        return r;
    }

    private static SortedSet<String> schema(Class<?> root) {
        final SortedSet<String> out = new TreeSet<>();
        walk(root, root, out, new HashSet<>());
        return out;
    }

    private static void walk(Class<?> root, Class<?> c, SortedSet<String> out, Set<Class<?>> seen) {
        if (!seen.add(c)) {
            return;
        }
        final String owner = c==root ? "" : nestedName(root, c) + ".";
        for (Field f : c.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) {
                continue;
            }
            out.add(owner + f.getName());
            referencedTypes(f.getGenericType()).stream()
                    .filter(t -> t!=root && outermost(t)==root)
                    .forEach(t -> walk(root, t, out, seen));
        }
    }

    private static String nestedName(Class<?> root, Class<?> c) {
        return Arrays.stream(c.getName().substring(root.getName().length() + 1).split("\\$"))
                .map(s -> s.replaceFirst("V\\d+$", ""))
                .collect(Collectors.joining("."));
    }

    private static Class<?> outermost(Class<?> c) {
        Class<?> r = c;
        while (r.getEnclosingClass()!=null) {
            r = r.getEnclosingClass();
        }
        return r;
    }

    private static List<Class<?>> referencedTypes(Type t) {
        if (t instanceof Class<?> c) {
            return List.of(c);
        }
        if (t instanceof ParameterizedType p) {
            final List<Class<?>> r = new ArrayList<>(referencedTypes(p.getRawType()));
            Arrays.stream(p.getActualTypeArguments()).map(JsonParamsFieldParityTest::referencedTypes).forEach(r::addAll);
            return r;
        }
        return List.of();
    }

    @Test
    public void test_execContextParams_matchesHeadV1() {
        // processMap is a private lookup cache; its getter is @JsonIgnore, so it is never written
        assertEquals(List.of("head only: Process.postFunctions", "head only: Process.preFunctions"),
                diff(ExecContextParams.class, ExecContextParamsV1.class, Set.of("processMap")));
    }

    @Test
    public void test_experimentResultParams_matchesHeadV2() {
        assertEquals(List.of(), diff(ExperimentResultParams.class, ExperimentResultParamsV2.class, Set.of()));
    }

    @Test
    public void test_licenseClaims_matchesHeadV1() {
        assertEquals(List.of(), diff(LicenseClaims.class, LicenseClaimsV1.class, Set.of()));
    }

    @Test
    public void test_licenseArtifactParams_matchesHeadV1() {
        assertEquals(List.of(), diff(LicenseArtifactParams.class, LicenseArtifactParamsV1.class, Set.of()));
    }

    @Test
    public void test_licenseInstallationParams_matchesHeadV1() {
        assertEquals(List.of(), diff(LicenseInstallationParams.class, LicenseInstallationParamsV1.class, Set.of()));
    }

    @Test
    public void test_metaStorageRegistryParams_matchesHeadV1() {
        assertEquals(List.of(), diff(MetaStorageRegistryParams.class, MetaStorageRegistryParamsV1.class, Set.of()));
    }
}
