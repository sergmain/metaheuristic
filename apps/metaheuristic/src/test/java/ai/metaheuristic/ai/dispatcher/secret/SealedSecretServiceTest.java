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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT;

/**
 * Unit tests for {@link SealedSecretService}.
 *
 * <p>⚠️ Moved off Mockito, which RULE-NO-MOCKITO prohibits, and the move was forced rather than
 * tidy-minded: when {@code sealFor} started asking {@code vaultService.isOpened(...)}, the mock
 * answered {@code false} for a method no test had stubbed, and the happy path silently returned
 * VAULT_LOCKED instead of OK. That is §3.4b(c) of the rule verbatim — an un-stubbed method answers
 * with a default the test never considered. The in-memory Vault below cannot do that: it has real
 * unlock and entry state, seeded through its own API, and every test that touches it uses it
 * unchanged. Assertions are on what {@code sealFor} returned, never on a double.
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

        byte[] plaintext = "sk-test-1234".getBytes(StandardCharsets.UTF_8);

        InMemoryVault vault = new InMemoryVault();
        vault.unlock(7L);
        vault.put(7L, "openai_api_key", "sk-test-1234");

        SealedSecretService svc = new SealedSecretService(vault, new Enrolled(pub));
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
        InMemoryVault vault = new InMemoryVault();
        vault.unlock(7L);
        vault.put(7L, "k", "whatever");

        SealedSecretService svc = new SealedSecretService(vault, new NotEnrolled());
        SealedSecretService.Outcome outcome = svc.sealFor(42L, 7L, "k");

        assertEquals(SealedSecretService.Outcome.Reason.PROCESSOR_NOT_ENROLLED, outcome.reason());
        assertNull(outcome.payload());
    }

    @Test
    void test_sealFor_vaultEntryMissing_returnsReason() throws Exception {
        CreateKeys ck = new CreateKeys(2048);

        InMemoryVault vault = new InMemoryVault();
        vault.unlock(7L);

        SealedSecretService svc = new SealedSecretService(vault, new Enrolled(ck.getPublicKey()));
        SealedSecretService.Outcome outcome = svc.sealFor(42L, 7L, "missing-key");

        assertEquals(SealedSecretService.Outcome.Reason.VAULT_ENTRY_MISSING, outcome.reason());
        assertNull(outcome.payload());
    }

    /** A real, if simple, Vault: unlock state and entries, both honest enough to disagree with a test. */
    static class InMemoryVault extends VaultService {
        private final Set<Long> opened = new HashSet<>();
        private final Map<Long, Map<String, String>> entries = new HashMap<>();

        InMemoryVault() {
            super(null);
        }

        void unlock(long companyUniqueId) {
            opened.add(companyUniqueId);
        }

        void put(long companyUniqueId, String code, String value) {
            entries.computeIfAbsent(companyUniqueId, k -> new HashMap<>()).put(code, value);
        }

        @Override
        public boolean isOpened(long companyUniqueId) {
            return opened.contains(companyUniqueId);
        }

        @Override
        public Optional<byte[]> getKeyBytes(long companyUniqueId, String code) {
            if (!opened.contains(companyUniqueId)) {
                return Optional.empty();
            }
            return Optional.ofNullable(entries.getOrDefault(companyUniqueId, Map.of()).get(code))
                    .map(s -> s.getBytes(StandardCharsets.UTF_8));
        }
    }

    static class Enrolled extends ProcessorKeyResolver {
        private final PublicKey pub;

        Enrolled(PublicKey pub) {
            super(null);
            this.pub = pub;
        }

        @Override
        public Optional<PublicKey> publicKeyFor(long processorId) {
            return Optional.of(pub);
        }
    }

    static class NotEnrolled extends ProcessorKeyResolver {
        NotEnrolled() {
            super(null);
        }

        @Override
        public Optional<PublicKey> publicKeyFor(long processorId) {
            return Optional.empty();
        }
    }
}
