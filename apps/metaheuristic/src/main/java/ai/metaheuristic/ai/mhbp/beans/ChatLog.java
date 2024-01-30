/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

import ai.metaheuristic.ai.mhbp.yaml.chat_log.ChatLogParams;
import ai.metaheuristic.ai.mhbp.yaml.chat_log.ChatLogParamsUtils;
import ai.metaheuristic.commons.utils.threads.ThreadUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.io.Serial;
import java.io.Serializable;

/**
 * @author Sergio Lissner
 * Date: 7/5/2023
 * Time: 1:45 AM
 */
@Entity
@Table(name = "MHBP_CHAT_LOG")
@Data
@NoArgsConstructor
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ChatLog implements Serializable {
    @Serial
    private static final long serialVersionUID = -213514838637724841L;

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
    private final ThreadUtils.CommonThreadLocker<ChatLogParams> paramsLocked =
            new ThreadUtils.CommonThreadLocker<>(this::parseParams);

    private ChatLogParams parseParams() {
        ChatLogParams temp = ChatLogParamsUtils.UTILS.to(params);
        ChatLogParams ecpy = temp==null ? new ChatLogParams() : temp;
        return ecpy;
    }

    @JsonIgnore
    public ChatLogParams getChatLogParams() {
        return paramsLocked.get();
    }

    @JsonIgnore
    public void updateParams(ChatLogParams apy) {
        setParams(ChatLogParamsUtils.UTILS.toString(apy));
    }
}
