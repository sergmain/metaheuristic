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

package ai.metaheuristic.ww2003.document.style;

import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.tags.xml.Attr;
import ai.metaheuristic.ww2003.document.tags.xml.Ind;
import ai.metaheuristic.ww2003.document.tags.xml.Name;
import ai.metaheuristic.ww2003.document.tags.xml.PProp;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * @author Serge
 * Date: 6/5/2021
 * Time: 7:31 PM
 */
public class InnerStyles {
    private final List<InnerStyle> styles = new ArrayList<>();

    @SuppressWarnings("CodeBlock2Expr")
    public void initStyles(List<CDNode> nodes) {
        nodes.stream()
                .filter(CDNode::instanceOfStyles)
                .map(CDNode::asStyles)
                .findFirst()
                .ifPresent(styles -> {
                    styles.streamNodes().forEach(style -> {
                        InnerStyle s = new InnerStyle();
                        s.type = style.findAttributeByName("type").map(Attr::getValue).orElse("");
                        s.styleId = style.findAttributeByName("styleId").map(Attr::getValue).orElse("");
                        s.defaultStyle = style.findAttributeByName("default").map(attr -> "on".equals(attr.value)).orElse(false);
                        s.name = style.findFirst(Name.class).flatMap(name -> name.findAttributeByName("val")).map(Attr::getValue).orElse("");
                        s.indent = style.asHasProperty().findProperty(PProp.class)
                                .flatMap(pProp -> pProp.findFirst(Ind.class))
                                .flatMap(ind -> ind.findAttributeByName("first-line"))
                                .map(Attr::getValue)
                                .orElse("");
                        this.styles.add(s);
                    });
                });
    }

    public Optional<InnerStyle> findStyleByName(String name) {
        return styles.stream().filter(style -> name.equalsIgnoreCase(style.name)).findFirst();
    }

    public Optional<InnerStyle> findStyleById(String id) {
        return styles.stream().filter(style -> id.equalsIgnoreCase(style.styleId)).findFirst();
    }

    public void destroy() {
        styles.clear();
    }
}
