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

package ai.metaheuristic.ai.mhbp.yaml.kb;

import ai.metaheuristic.api.sourcing.GitInfo;
import ai.metaheuristic.commons.exceptions.CheckIntegrityFailedException;
import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.api.data.BaseParams;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("FieldMayBeStatic")
@Data
public class KbParamsV1 implements BaseParams  {

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

    @NoArgsConstructor
    public static class GitV1 extends GitInfo {
        public GitV1(String repo, String branch, String commit, @Nullable String path) {
            super(repo, branch, commit, path);
        }

        //        public String repo;
        //        public String branch;
        //        public String commit;
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
