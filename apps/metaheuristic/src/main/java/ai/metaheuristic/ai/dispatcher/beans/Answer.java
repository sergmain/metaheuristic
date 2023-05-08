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

import ai.metaheuristic.ai.yaml.answer.AnswerParams;
import ai.metaheuristic.ai.yaml.answer.AnswerParamsUtils;
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
