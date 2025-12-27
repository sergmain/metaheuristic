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

package ai.metaheuristic.ai.mhbp.beans;

import ai.metaheuristic.commons.S;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.jspecify.annotations.Nullable;

import jakarta.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Sergio Lissner
 * Date: 4/15/2023
 * Time: 7:32 PM
 */
@Entity
@Table(name = "MHBP_EVALUATION")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Evaluation implements Serializable {

    public static class ChapterIdsConverter implements AttributeConverter<List<String>, String> {

        @Override
        public String convertToDatabaseColumn(@Nullable List<String> extraFields) {
            if (extraFields==null) {
                throw new IllegalStateException("(extraFields==null)");
            }
            String s = extraFields.stream().map(Object::toString).collect(Collectors.joining(","));
            return s;
        }

        @Override
        public List<String> convertToEntityAttribute(String data) {
            if (S.b(data)) {
                return new ArrayList<>();
            }
            List<String> list = List.of(StringUtils.split(data, ','));
            return list;
        }
    }

    @Serial
    private static final long serialVersionUID = -5515608565018985069L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "COMPANY_ID")
    public long companyId;

    @Column(name = "ACCOUNT_ID")
    public long accountId;

    @Column(name = "API_ID")
    public long apiId;

    @Column(name = "CHAPTER_IDS")
    @Convert(converter = ChapterIdsConverter.class)
    public List<String> chapterIds;

    public long createdOn;

    public String code;
}
