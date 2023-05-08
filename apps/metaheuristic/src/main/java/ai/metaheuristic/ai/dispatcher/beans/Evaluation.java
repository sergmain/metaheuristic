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

package ai.metaheuristic.ai.dispatcher.beans;

import ai.metaheuristic.commons.S;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
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
