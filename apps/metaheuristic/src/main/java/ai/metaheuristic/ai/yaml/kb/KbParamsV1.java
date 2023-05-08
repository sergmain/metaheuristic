/*
 *    Copyright 2023, Sergio Lissner, Innovation platforms, LLC
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 *
 */

package ai.metaheuristic.ai.yaml.kb;

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.data.BaseParams;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("FieldMayBeStatic")
@Data
public class KbParamsV1 implements BaseParams {

    public final int version=1;

    @Override
    public boolean checkIntegrity() {
        if (kb.git==null && kb.file==null && (kb.inline==null || kb.inline.isEmpty())) {
            throw new CheckIntegrityFailedException("(kb.git==null && kb.file==null && (kb.inline==null || kb.inline.isEmpty()))");
        }
        return true;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KbPathV1 {
        public String evals;
        public String data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GitV1 {
        public String repo;
        public String branch;
        public String commit;
        public final List<KbPathV1> kbPaths = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileV1 {
        public String url;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InlineV1 {
        public String p;
        public String a;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KbV1 {
        public String code;
        public Enums.KbFileFormat type;
        public GitV1 git;
        public FileV1 file;
        @Nullable
        public List<InlineV1> inline;
    }

    public final KbV1 kb = new KbV1();
    public boolean disabled = false;
}
