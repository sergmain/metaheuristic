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

package ai.metaheuristic.ww2003.document.tags.xml.table;

import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.Composite;
import ai.metaheuristic.ww2003.document.tags.HasProperty;
import ai.metaheuristic.ww2003.document.tags.Indentation;
import ai.metaheuristic.ww2003.document.tags.Property;
import ai.metaheuristic.ww2003.document.tags.PropertyElement;
import ai.metaheuristic.ww2003.document.tags.xml.XmlTag;
import lombok.NoArgsConstructor;
import javax.annotation.Nullable;

@NoArgsConstructor
public class TRow extends Composite implements XmlTag, HasProperty, Indentation {

    private static final String NS = "w";
    private static final String TAG_NAME = "tr";

    @Nullable
    public Integer indent = null;

    public TRow(CDNode... nodes) {
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
    public void addPropertyElement(Class<? extends Property> propertyClass, PropertyElement propertyElement) {
        findProperty(propertyClass, propertyElement.getClass()).ifPresentOrElse(
                prEl -> prEl.getParent().replace(prEl, propertyElement),
                () -> findProperty(propertyClass).ifPresentOrElse(
                        pr -> pr.asComposite().add(propertyElement),
                        () -> {
                            if (propertyClass.isAssignableFrom(TRowProp.class)) {
                                setProperty(new TRowProp(propertyElement));
                            } else if (propertyClass.isAssignableFrom(TblPropEx.class)) {
                                setProperty(new TblPropEx(propertyElement));
                            }
                        }
                ));
    }

    @Nullable
    @Override
    public Integer getIndent() {
        return indent;
    }

    @Override
    public void setIndent(@Nullable Integer indent) {
        this.indent = indent;
    }

    @Override
    public TRow clone() {
        TRow clone = (TRow) super.clone();
        return clone;
    }
}
