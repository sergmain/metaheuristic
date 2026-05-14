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

package ai.metaheuristic.ai.processor.security;

import ai.metaheuristic.commons.security.CreateKeys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * In-memory-only RSA keypair owned by a Processor for the lifetime of the
 * process. Generated once at {@code @PostConstruct}, lost on Processor restart.
 *
 * <p>The public key is sent to Dispatcher on every keep-alive request — see
 * {@code KeepAliveRequestParamYaml.ProcessorStatus.publicKeySpki} (Stage 4).
 * No disk persistence, no passphrase plumbing, no rotation ceremony.
 *
 * <p>A Processor reboot produces a fresh keypair; any task encrypted under the
 * old key fails to decrypt on the Processor side and is retried via MH's
 * normal re-execution path. This trade-off is accepted to shrink the trust
 * boundary to the running process.
 *
 * @author Sergio Lissner
 */
@Component
@Profile("processor")
@Slf4j
public class ProcessorKeyPair {

    @Nullable
    private volatile PrivateKey privateKey;

    @Nullable
    private volatile PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            CreateKeys ck = new CreateKeys(2048);
            this.privateKey = ck.getPrivateKey();
            this.publicKey  = ck.getPublicKey();
            log.info("Processor keypair generated in memory (RSA-2048)");
        } catch (Exception e) {
            // Fail fast so Spring boot aborts and the operator sees the cause.
            throw new IllegalStateException("Failed to generate processor keypair: " + e.getMessage(), e);
        }
    }

    public PrivateKey getPrivateKey() {
        PrivateKey pk = this.privateKey;
        if (pk == null) {
            throw new IllegalStateException("Processor private key not initialized");
        }
        return pk;
    }

    public PublicKey getPublicKey() {
        PublicKey pk = this.publicKey;
        if (pk == null) {
            throw new IllegalStateException("Processor public key not initialized");
        }
        return pk;
    }

    /** SPKI-DER bytes — what the Processor sends to Dispatcher on every keep-alive. */
    public byte[] getPublicKeySpki() {
        return getPublicKey().getEncoded();
    }
}
