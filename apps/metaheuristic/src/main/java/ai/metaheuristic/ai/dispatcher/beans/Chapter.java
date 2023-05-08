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

import ai.metaheuristic.ai.yaml.chapter.ChapterParams;
import ai.metaheuristic.ai.yaml.chapter.ChapterParamsUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 4/26/2023
 * Time: 11:44 PM
 */
@Entity
@Table(name = "MHBP_CHAPTER")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Chapter implements Serializable {
    @Serial
    private static final long serialVersionUID = -7617920402229837826L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "COMPANY_ID")
    public long companyId;

    @Column(name = "ACCOUNT_ID")
    public long accountId;

    @Column(name = "KB_ID")
    public long kbId;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name = "CODE")
    public String code;

    @Column(name="DISABLED")
    public boolean disabled;

    @Column(name = "PARAMS")
    private String params;

    public int status;

    @Column(name = "PROMPT_COUNT")
    public int promptCount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name="CHAPTER_ID")
    private List<Part> parts = new ArrayList<>();

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.chapterParams = null;
        }
    }

    public String getParams() {
        return params;
    }

    @Transient
    @JsonIgnore
    @Nullable
    private ChapterParams chapterParams = null;

    @Transient
    @JsonIgnore
    private final Object syncParamsObj = new Object();

    @JsonIgnore
    public ChapterParams getChapterParams() {
        if (chapterParams==null) {
            synchronized (syncParamsObj) {
                if (chapterParams==null) {
                    //noinspection UnnecessaryLocalVariable
                    ChapterParams temp = ChapterParamsUtils.UTILS.to(params);
                    chapterParams = temp;
                }
            }
        }
        return chapterParams;
    }

    @JsonIgnore
    public void updateParams(ChapterParams apy) {
        setParams(ChapterParamsUtils.UTILS.toString(apy));
    }

}
