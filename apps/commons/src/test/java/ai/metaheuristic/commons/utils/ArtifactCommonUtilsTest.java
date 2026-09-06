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

package ai.metaheuristic.commons.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Sergio Lissner
 * Date: 10/20/2023
 * Time: 12:32 AM
 */
@Execution(ExecutionMode.CONCURRENT)
public class ArtifactCommonUtilsTest {

    @Test
    public void test_normalizeCode() {
        assertEquals("aaa", ArtifactCommonUtils.normalizeCode("aaa"));
        assertEquals("aaa", ArtifactCommonUtils.normalizeCode("aaa."));
        assertEquals("aaa", ArtifactCommonUtils.normalizeCode("aaa.."));
        assertEquals("a_a_a", ArtifactCommonUtils.normalizeCode("a:a:a.."));
        assertEquals("a_a_a..b", ArtifactCommonUtils.normalizeCode("a:a:a..b"));


        assertThrows(IllegalStateException.class, ()->ArtifactCommonUtils.normalizeCode(".."));
    }

    /**
     * ❗ normalizeCode names things on disk - a Function's directory under the Processor's resources, the
     * zip it is delivered in. Two distinct Function codes that normalize to one string therefore share a
     * directory, and whichever is unpacked second wins.
     *
     * <p>Mapping ':' to '_' without first escaping '_' does exactly that: 'a:b' and 'a_b' are different
     * codes and both come out as 'a_b'.
     */
    @Test
    public void test_normalizeCodeDistinguishesAColonFromAnUnderscore() {
        assertNotEquals(
                ArtifactCommonUtils.normalizeCode("a:b"),
                ArtifactCommonUtils.normalizeCode("a_b"),
                "two different Function codes must not normalize onto the same path");
    }

    /** The escape itself, doubled so the mapping can be read back unambiguously. */
    @Test
    public void test_normalizeCodeDoublesAnUnderscore() {
        assertEquals("a__b", ArtifactCommonUtils.normalizeCode("a_b"));
        assertEquals("a_b", ArtifactCommonUtils.normalizeCode("a:b"));
        assertEquals("mh-verify.hello-dispatcher__1.2",
                ArtifactCommonUtils.normalizeCode("mh-verify.hello-dispatcher_1.2"));
        assertEquals("mh.multiply_1.2", ArtifactCommonUtils.normalizeCode("mh.multiply:1.2"));
    }

    /** ❗ Escaping runs FIRST, so an underscore produced from a colon is never escaped in turn. */
    @Test
    public void test_underscoresIntroducedByAColonAreNotEscapedAgain() {
        assertEquals("a_b__c", ArtifactCommonUtils.normalizeCode("a:b_c"));
        assertEquals("a__b_c", ArtifactCommonUtils.normalizeCode("a_b:c"));
    }
}
