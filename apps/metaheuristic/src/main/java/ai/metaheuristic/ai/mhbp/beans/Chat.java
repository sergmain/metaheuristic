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
import ai.metaheuristic.ai.mhbp.yaml.chat.ChatParams;
import ai.metaheuristic.ai.mhbp.yaml.chat.ChatParamsUtils;
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
 * Date: 6/21/2023
 * Time: 10:59 PM
 */
@Entity
@Table(name = "MHBP_CHAT")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Chat implements Serializable {
    @Serial
    private static final long serialVersionUID = 9177241903618935304L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Version
    public Integer version;

    @Column(name = "COMPANY_ID")
    public long companyId;

    @Column(name = "ACCOUNT_ID")
    public long accountId;

    @Column(name="CREATED_ON")
    public long createdOn;

    @Column(name = "NAME")
    public String name;

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
    private final ThreadUtils.CommonThreadLocker<ChatParams> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ChatParams parseParams() {
        ChatParams temp = ChatParamsUtils.UTILS.to(params);
        ChatParams ecpy = temp==null ? new ChatParams() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ChatParams getChapterParams() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ChatParams apy) {
        setParams(ChatParamsUtils.UTILS.toString(apy));
    }
}
