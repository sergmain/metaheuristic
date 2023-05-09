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

import ai.metaheuristic.ai.Enums;
import ai.metaheuristic.ai.mhbp.data.KbData;
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
public class KbParams implements BaseParams {

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
    public static class KbPath {
        public String evals;
        public String data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Git implements KbData.KbGit {
        public String repo;
        public String branch;
        public String commit;
        public final List<KbPath> kbPaths = new ArrayList<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class File {
        public String url;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Inline {
        public String p;
        public String a;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Kb {
        public String code;
        public Enums.KbFileFormat type;
        @Nullable
        public Git git;
        @Nullable
        public File file;
        @Nullable
        public List<Inline> inline;
    }

    public final Kb kb = new Kb();
    public boolean disabled = false;
}
