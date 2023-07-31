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

package ai.metaheuristic.ai.mhbp.yaml.chat_log;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

public class ChatLogParamsUtilsV1 extends
        AbstractParamsYamlUtils<ChatLogParamsV1, ChatLogParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ChatLogParamsV1.class);
    }

    @Override
    public ChatLogParams upgradeTo(ChatLogParamsV1 v1) {
        v1.checkIntegrity();

        ChatLogParams t = new ChatLogParams();
        t.api = new ChatLogParams.Api(v1.api.apiId, v1.api.code);
        t.prompt = ChatLogParamsUtilsV1.toPrompt(v1.prompt);
        t.scenarioId = v1.scenarioId;
        t.chatId = v1.chatId;
        t.stateless = v1.stateless;

        t.checkIntegrity();
        return t;
    }

    private static ChatLogParams.Prompt toPrompt(ChatLogParamsV1.PromptV1 v1) {
        ChatLogParams.Prompt f = new ChatLogParams.Prompt(v1.p, v1.a, v1.r, v1.e);
        return f;
    }

    @Override
    public Void downgradeTo(Void yaml) {
        return null;
    }

    @Override
    public Void nextUtil() {
        return null;
    }

    @Override
    public Void prevUtil() {
        return null;
    }

    @Override
    public String toString(ChatLogParamsV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @Override
    public ChatLogParamsV1 to(String s) {
        final ChatLogParamsV1 p = getYaml().load(s);
        return p;
    }

}
