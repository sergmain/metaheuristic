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
package ai.metaheuristic.apps.gen_keys;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link KekUtils}. No Spring context, real {@link SecureRandom}.
 *
 * @author Sergio Lissner
 * Date: 10/2/2026
 */
@Execution(ExecutionMode.CONCURRENT)
public class KekUtilsTest {

    @Test
    public void test_kekLen_isAes256() {
        assertEquals(32, KekUtils.KEK_LEN, "the Dispatcher accepts only a 32-byte KEK");
    }

    @Test
    public void test_generateKek_decodesToExactlyKekLenBytes() {
        final byte[] decoded = Base64.getDecoder().decode(KekUtils.generateKek(new SecureRandom()));
        assertEquals(KekUtils.KEK_LEN, decoded.length);
    }

    @Test
    public void test_generateKek_isASingleLineWithoutWhitespace() {
        final String kek = KekUtils.generateKek(new SecureRandom());
        assertEquals(kek.strip(), kek);
        assertFalse(kek.chars().anyMatch(Character::isWhitespace), "must be pasteable into an environment variable as is: " + kek);
    }

    @Test
    public void test_generateKek_isFreshPerCall() {
        final SecureRandom random = new SecureRandom();
        assertNotEquals(KekUtils.generateKek(random), KekUtils.generateKek(random));
    }

    @Test
    public void test_isKekCommand_recognizesPlainAndDashedForm() {
        assertTrue(KekUtils.isKekCommand("kek"));
        assertTrue(KekUtils.isKekCommand("--kek"));
        assertTrue(KekUtils.isKekCommand("--spring.main.banner-mode=off", "kek"));
    }

    @Test
    public void test_isKekCommand_keepsTheRsaDefault() {
        assertFalse(KekUtils.isKekCommand());
        assertFalse(KekUtils.isKekCommand("KEK"));
        assertFalse(KekUtils.isKekCommand("rsa"));
    }
}
