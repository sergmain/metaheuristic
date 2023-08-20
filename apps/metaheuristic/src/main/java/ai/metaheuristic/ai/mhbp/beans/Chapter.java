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

package ai.metaheuristic.ai.mhbp.beans;

import ai.metaheuristic.ai.mhbp.yaml.chapter.ChapterParams;
import ai.metaheuristic.ai.mhbp.yaml.chapter.ChapterParamsUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

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

    public int status;

    @Column(name = "PROMPT_COUNT")
    public int promptCount;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name="CHAPTER_ID")
    private List<Part> parts = new ArrayList<>();

    @Column(name = "PARAMS")
    private String params;

    public String getParams() {
        return params;
    }

    public void setParams(String params) {
        this.paramsLocked.reset(()->this.params = params);
    }

    @Transient
    @JsonIgnore
    private final ThreadUtils.CommonThreadLocker<ChapterParams> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ChapterParams parseParams() {
        ChapterParams temp = ChapterParamsUtils.UTILS.to(params);
        ChapterParams ecpy = temp==null ? new ChapterParams() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ChapterParams getChapterParams() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ChapterParams apy) {
        setParams(ChapterParamsUtils.UTILS.toString(apy));
    }
}
