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

package ai.metaheuristic.ai.mhbp.yaml.chat;

import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.stream.Collectors;

public class ChatParamsUtilsV1 extends
        AbstractParamsYamlUtils<ChatParamsV1, ChatParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public Yaml getYaml() {
        return YamlUtils.init(ChatParamsV1.class);
    }

    @Override
    public ChatParams upgradeTo(ChatParamsV1 v1) {
        v1.checkIntegrity();

        ChatParams t = new ChatParams();
        t.api = new ChatParams.Api(v1.api.apiId, v1.api.code);
        t.prompts = v1.prompts.stream().map(ChatParamsUtilsV1::toPrompt).collect(Collectors.toList());
        t.checkIntegrity();
        return t;
    }

    private static ChatParams.Prompt toPrompt(ChatParamsV1.PromptV1 v1) {
        ChatParams.Prompt f = new ChatParams.Prompt(v1.p, v1.a, v1.r, v1.e);
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
    public String toString(ChatParamsV1 yaml) {
        yaml.checkIntegrity();

        return getYaml().dump(yaml);
    }

    @Override
    public ChatParamsV1 to(String s) {
        final ChatParamsV1 p = getYaml().load(s);
        return p;
    }

}
