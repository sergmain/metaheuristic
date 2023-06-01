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

import ai.metaheuristic.ai.mhbp.yaml.answer.AnswerParams;
import ai.metaheuristic.ai.mhbp.yaml.answer.AnswerParamsUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.springframework.lang.Nullable;

import javax.persistence.*;
import java.io.Serial;
import java.io.Serializable;

/**
 * @author Sergio Lissner
 * Date: 3/22/2023
 * Time: 1:48 AM
 */
@Entity
@Table(name = "MHBP_ANSWER")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Answer implements Serializable {
    @Serial
    private static final long serialVersionUID = -5515608565018985069L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    public Long sessionId;

    @Column(name = "CHAPTER_ID")
    public long chapterId;

    public long answeredOn;

    @Column(name = "Q_CODE")
    public String questionCode;

    public int status;

    @Column(name = "PARAMS")
    private String params;

    public int total;
    public int failed;

    @Column(name = "SYSTEM_ERROR")
    public int systemError;

    public void setParams(String params) {
        synchronized (this) {
            this.params = params;
            this.answerParams = null;
        }
    }

    public String getParams() {
        return params;
    }

    @Transient
    @JsonIgnore
    @Nullable
    private AnswerParams answerParams = null;

    @Transient
    @JsonIgnore
    private final Object syncParamsObj = new Object();

    @JsonIgnore
    public AnswerParams getAnswerParams() {
        if (answerParams==null) {
            synchronized (syncParamsObj) {
                if (answerParams==null) {
                    //noinspection UnnecessaryLocalVariable
                    AnswerParams temp = AnswerParamsUtils.UTILS.to(params);
                    answerParams = temp;
                }
            }
        }
        return answerParams;
    }

    @JsonIgnore
    public void updateParams(AnswerParams apy) {
        setParams(AnswerParamsUtils.UTILS.toString(apy));
    }
}
