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

package ai.metaheuristic.ai.yaml.ws_event;

import ai.metaheuristic.commons.S;
import ai.metaheuristic.commons.exceptions.BlankYamlParamsException;
import ai.metaheuristic.commons.yaml.YamlUtils;
import ai.metaheuristic.commons.yaml.versioning.AbstractParamsYamlUtils;
import org.springframework.lang.NonNull;
import org.yaml.snakeyaml.Yaml;

/**
 * @author Sergio Lissner
 * Date: 2/9/2024
 * Time: 3:46 PM
 */
public class WebsocketEventParamsUtilsV1
        extends AbstractParamsYamlUtils<WebsocketEventParamsV1, WebsocketEventParams, Void, Void, Void, Void> {

    @Override
    public int getVersion() {
        return 1;
    }

    @NonNull
    @Override
    public Yaml getYaml() {
        return YamlUtils.init(WebsocketEventParamsV1.class);
    }

    @NonNull
    @Override
    public WebsocketEventParams upgradeTo(@NonNull WebsocketEventParamsV1 src) {
        src.checkIntegrity();
        WebsocketEventParams trg = new WebsocketEventParams();
        if (src.functions!=null) {
            trg.functions = src.functions;
        }
        trg.type = src.type;

        trg.checkIntegrity();
        return trg;
    }

    @NonNull
    @Override
    public Void downgradeTo(@NonNull Void yaml) {
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
    public String toString(@NonNull WebsocketEventParamsV1 yaml) {
        return getYaml().dump(yaml);
    }

    @NonNull
    @Override
    public WebsocketEventParamsV1 to(@NonNull String s) {
        if (S.b(s)) {
            throw new BlankYamlParamsException("'yaml' parameter is blank");
        }
        final WebsocketEventParamsV1 p = getYaml().load(s);
        return p;
    }

}
