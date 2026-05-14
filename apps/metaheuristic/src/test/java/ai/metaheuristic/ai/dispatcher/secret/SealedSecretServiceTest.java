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

package ai.metaheuristic.ai.dispatcher.secret;

import ai.metaheuristic.ai.dispatcher.processor.security.ProcessorKeyResolver;
import ai.metaheuristic.ai.dispatcher.vault.VaultService;
import ai.metaheuristic.commons.security.AsymmetricEncryptor;
import ai.metaheuristic.commons.security.CreateKeys;
import ai.metaheuristic.commons.security.SealedSecret;
import ai.metaheuristic.commons.security.SealedSecretCodec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SealedSecretService}.
 *
 * @author Sergio Lissner
 */
@Execution(CONCURRENT)
class SealedSecretServiceTest {

    @Test
    void test_sealFor_happyPath_returnsOkAndEncryptedPayload() throws Exception {
        CreateKeys ck = new CreateKeys(2048);
        PublicKey pub = ck.getPublicKey();
        PrivateKey priv = ck.getPrivateKey();

        VaultService vault = mock(VaultService.class);
        ProcessorKeyResolver resolver = mock(ProcessorKeyResolver.class);
        byte[] plaintext = "sk-test-1234".getBytes(StandardCharsets.UTF_8);
        when(vault.getKeyBytes(7L, "openai_api_key")).thenReturn(Optional.of(plaintext.clone()));
        when(resolver.publicKeyFor(42L)).thenReturn(Optional.of(pub));

        SealedSecretService svc = new SealedSecretService(vault, resolver);
        SealedSecretService.Outcome outcome = svc.sealFor(42L, 7L, "openai_api_key");

        assertEquals(SealedSecretService.Outcome.Reason.OK, outcome.reason());
        SealedSecretService.SealedPayload p = outcome.payload();
        assertNotNull(p);
        assertNotNull(p.sealed());
        assertNotNull(p.fingerprint());
        assertTrue(p.notAfter() > p.issuedOn());

        // Round-trip: decode Base64 -> SealedSecretCodec -> AsymmetricEncryptor.decrypt
        // must recover the exact plaintext.
        byte[] wire = Base64.getDecoder().decode(p.sealed());
        SealedSecret sealed = SealedSecretCodec.fromBytes(wire);
        byte[] recovered = AsymmetricEncryptor.decrypt(sealed, priv);
        assertArrayEquals(plaintext, recovered);
    }

    @Test
    void test_sealFor_processorNotEnrolled_returnsReason() {
        VaultService vault = mock(VaultService.class);
        ProcessorKeyResolver resolver = mock(ProcessorKeyResolver.class);
        when(resolver.publicKeyFor(42L)).thenReturn(Optional.empty());

        SealedSecretService svc = new SealedSecretService(vault, resolver);
        SealedSecretService.Outcome outcome = svc.sealFor(42L, 7L, "k");

        assertEquals(SealedSecretService.Outcome.Reason.PROCESSOR_NOT_ENROLLED, outcome.reason());
        assertNull(outcome.payload());
    }

    @Test
    void test_sealFor_vaultEntryMissing_returnsReason() throws Exception {
        CreateKeys ck = new CreateKeys(2048);
        VaultService vault = mock(VaultService.class);
        ProcessorKeyResolver resolver = mock(ProcessorKeyResolver.class);
        when(resolver.publicKeyFor(42L)).thenReturn(Optional.of(ck.getPublicKey()));
        when(vault.getKeyBytes(7L, "missing-key")).thenReturn(Optional.empty());

        SealedSecretService svc = new SealedSecretService(vault, resolver);
        SealedSecretService.Outcome outcome = svc.sealFor(42L, 7L, "missing-key");

        assertEquals(SealedSecretService.Outcome.Reason.VAULT_ENTRY_MISSING, outcome.reason());
        assertNull(outcome.payload());
    }
}
