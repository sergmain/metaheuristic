/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
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

package ai.metaheuristic.api.data.checksum_signature;

import ai.metaheuristic.api.EnumsApi;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import javax.annotation.Nullable;

/**
 * @author Serge
 * Date: 1/2/2021
 * Time: 3:40 AM
 */
public class ChecksumAndSignatureData {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChecksumWithSignature {
        @Nullable
        public String checksum = null;
        @Nullable
        public String signature = null;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChecksumWithSignatureInfo {
        public final ChecksumWithSignature checksumWithSignature = new ChecksumWithSignature();
        public String originChecksumWithSignature;
        public EnumsApi.HashAlgo hashAlgo;

        public void set(ChecksumWithSignature checksumWithSignature, String originChecksumWithSignature, EnumsApi.HashAlgo hashAlgo){
            this.originChecksumWithSignature = originChecksumWithSignature;
            this.hashAlgo = hashAlgo;
            this.checksumWithSignature.checksum = checksumWithSignature.checksum;
            this.checksumWithSignature.signature = checksumWithSignature.signature;
        }
    }
}
