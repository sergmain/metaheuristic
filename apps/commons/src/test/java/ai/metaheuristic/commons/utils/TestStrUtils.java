/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

public class TestStrUtils {

    public static final Supplier<String> CODE_FUNC = () -> { throw new IllegalStateException(); };

    @Test
    public void test_getCode() {
        assertEquals("aaa", StrUtils.getCode("aaa", CODE_FUNC));
        assertEquals("aaa_bbb", StrUtils.getCode("aaa_bbb", CODE_FUNC));
        assertEquals("aaa_bbb", StrUtils.getCode("aaa bbb", CODE_FUNC));
    }

    @Test
    public void test_getVariableName() {
        assertEquals("aaa", StrUtils.getVariableName("aaa", CODE_FUNC));
        assertEquals("aaa_bbb", StrUtils.getVariableName("aaa_bbb", CODE_FUNC));
        assertEquals("aaa_bbb", StrUtils.getVariableName("aaa bbb", CODE_FUNC));
        assertEquals("aaa_bbb", StrUtils.getVariableName("aaa-bbb", CODE_FUNC));
    }

    @Test
    public void testMatching() {
        assertTrue(StrUtils.isCodeOk("1234567890-abc_xyz:1.0"));

        assertTrue(StrUtils.isCodeOk("aaa.txt"));
        assertTrue(StrUtils.isCodeOk("aaa."));

        assertFalse(StrUtils.isCodeOk("1234567890-?#$%abc_xyz:1.0"));
        assertFalse(StrUtils.isCodeOk("aaa bbb.txt"));
        assertFalse(StrUtils.isCodeOk("aaa,bbb.txt"));
        assertFalse(StrUtils.isCodeOk("aaaäöü.txt"));
        assertFalse(StrUtils.isCodeOk("aaa\\aaaäöü.txt"));
        assertFalse(StrUtils.isCodeOk("aaa/aaaäöü.txt"));
    }

    @Test
    public void testVariableNames() {

        assertTrue(StrUtils.isVarNameOk("aa1234567890"));
        assertTrue(StrUtils.isVarNameOk("aa"));
        assertTrue(StrUtils.isVarNameOk("aa_123"));
        assertTrue(StrUtils.isVarNameOk("aa_123_"));
        assertTrue(StrUtils.isVarNameOk("_aa_123_"));
        assertTrue(StrUtils.isVarNameOk("AA_123_"));
        assertTrue(StrUtils.isVarNameOk("AA_BB"));

        assertFalse(StrUtils.isVarNameOk("1234567890"));
        assertFalse(StrUtils.isVarNameOk("1234567890aaa"));
        assertFalse(StrUtils.isVarNameOk("1234567890-abc_xyz:1.0"));
        assertFalse(StrUtils.isVarNameOk("aaa.txt"));
        assertFalse(StrUtils.isVarNameOk("aaa."));

        assertFalse(StrUtils.isVarNameOk("1234567890-?#$%abc_xyz:1.0"));
        assertFalse(StrUtils.isVarNameOk("aaa bbb.txt"));
        assertFalse(StrUtils.isVarNameOk("aaa,bbb.txt"));
        assertFalse(StrUtils.isVarNameOk("aaaäöü.txt"));
        assertFalse(StrUtils.isVarNameOk("aaa\\aaaäöü.txt"));
        assertFalse(StrUtils.isVarNameOk("aaa/aaaäöü.txt"));
    }
}
