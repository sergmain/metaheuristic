/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.apps.gen_keys;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;

import java.security.*;

public class GenerateKeys {

    private KeyPairGenerator keyGen;
    private KeyPair pair;
    private PrivateKey privateKey;
    private PublicKey publicKey;

    public GenerateKeys(int keylength) throws NoSuchAlgorithmException {
        this.keyGen = KeyPairGenerator.getInstance("RSA");
        this.keyGen.initialize(keylength);
    }

    public void createKeys() {
        this.pair = this.keyGen.generateKeyPair();
        this.privateKey = pair.getPrivate();
        this.publicKey = pair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return this.privateKey;
    }

    public PublicKey getPublicKey() {
        return this.publicKey;
    }

    public static String encodeBase64String(final byte[] binaryData) {
        return StringUtils.newStringUsAscii(Base64.encodeBase64(binaryData, true));
    }

    public static void main(String[] args) throws NoSuchAlgorithmException {
        GenerateKeys myKeys = new GenerateKeys(2048);
        myKeys.createKeys();

        String privateKey64 = encodeBase64String(myKeys.getPrivateKey().getEncoded());
        String publicKey64 = Base64.encodeBase64String(myKeys.getPublicKey().getEncoded());
        System.out.println("Private key in base64 format:\n" + privateKey64 +"\n\n");
        System.out.println("Public key in base64 format:\n" + publicKey64);

   }
}