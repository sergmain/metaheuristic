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

package ai.metaheuristic.ww2003.document.tags.xml;

import ai.metaheuristic.ww2003.Enums;
import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.Composite;
import ai.metaheuristic.ww2003.document.tags.HasProperty;
import ai.metaheuristic.ww2003.document.tags.Property;
import ai.metaheuristic.ww2003.document.tags.PropertyElement;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * The &lt;r> stands for run, which is a region of text with a common set of properties, such as formatting.<br/>
 * <a href="https://docs.microsoft.com/en-us/office/open-xml/structure-of-a-wordprocessingml-document">
 *     https://docs.microsoft.com/en-us/office/open-xml/structure-of-a-wordprocessingml-document
 * </a>
 */
@NoArgsConstructor
public class Run extends Composite implements XmlTag, HasProperty {

    private static final String NS = "w";
    private static final String TAG_NAME = "r";

    public Run(CDNode... nodes) {
        super(nodes);
    }

    @Override
    public String getNameSpace() {
        return NS;
    }

    @Override
    public String getTagName() {
        return TAG_NAME;
    }


    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        streamNodes().filter(CDNode::instanceOfText).forEach(node -> builder.append(node.toString()));
        return builder.toString();
    }

    @Override
    public void addPropertyElement(Class<? extends Property> propertyClass, PropertyElement propertyElement) {
        Optional<? extends Property> optionalProperty = findProperty(propertyClass);
        Optional<? extends PropertyElement> optionalOldPropElement = optionalProperty.flatMap(p -> p.findFirst(propertyElement.getClass()));
        if (optionalOldPropElement.isPresent()) {
            PropertyElement oldPropElement = optionalOldPropElement.get();
            optionalProperty.get().asComposite().replace(oldPropElement, propertyElement);
        } else {
            if (optionalProperty.isPresent()) {
                optionalProperty.get().asComposite().add(propertyElement);
            } else {
                if (propertyClass.isAssignableFrom(RProp.class)) {
                    setProperty(new RProp(propertyElement));
                }
            }
        }
    }

    public static Run text(String text) {
        return new Run(new Text(text));
    }

    public static Run t(String ... text) {
        Run r = new Run();
        for (String s : text) {
            r.add(new Text(s));
        }
        return r;
    }

    public static Run t(Enums.Align align, String text) {
        final Run run = new Run(new Text(text));
        run.setAlign(align);
        return run;
    }
}
