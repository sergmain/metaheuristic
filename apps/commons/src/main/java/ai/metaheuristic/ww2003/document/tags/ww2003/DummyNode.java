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

package ai.metaheuristic.ww2003.document.tags.ww2003;

import ai.metaheuristic.ww2003.Enums;
import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.Composite;
import ai.metaheuristic.ww2003.document.persistence.ww2003.property.WW2003PropertyUtils;
import ai.metaheuristic.ww2003.document.tags.HasProperty;
import ai.metaheuristic.ww2003.document.tags.Indentation;
import ai.metaheuristic.ww2003.document.tags.Property;
import ai.metaheuristic.ww2003.document.tags.xml.PProp;
import ai.metaheuristic.ww2003.document.tags.xml.table.TCellProp;
import ai.metaheuristic.ww2003.document.tags.xml.table.TRowProp;
import ai.metaheuristic.ww2003.document.tags.xml.table.TblProp;
import ai.metaheuristic.ww2003.document.tags.xml.table.TblPropEx;
import ai.metaheuristic.ww2003.document.tags.xml.*;

import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * @author Serge
 * Date: 5/11/2022
 * Time: 3:27 PM
 */
public class DummyNode extends Composite implements XmlTag, HasProperty, Closeable, AutoCloseable {

    private static final String TAG_NAME = "dummy";

    public DummyNode(Composite composite, boolean vanish) {
        this(composite, vanish, false);
    }


    public DummyNode(Composite composite, boolean vanish, boolean isIndent) {
        super();
        if (composite.propertiesSize()>0) {
            initProperties();
        }
        composite.streamProperties().forEach(p -> {
            List<CDNode> nodes = ((Composite) p).getNodes();
            if (nodes.isEmpty()) {
                setNewProp(p.getClass());
            } else {
                for (CDNode node : nodes) {
                    addPropertyElementInternal(p.getClass(), node);
                }
            }
        });
        if (isIndent && composite instanceof Indentation indent && indent.getIndent()!=null) {
            this.addPropertyElement(PProp.class, new Ind(Attr.get("w", "first-line", String.valueOf(indent.getIndent()))), false);
        }
        if (composite.getAlign()!=Enums.Align.none) {
            this.addPropertyElement(PProp.class, (WW2003PropertyUtils.getJc(composite.getAlign())), false);
        }
        if (vanish) {
            WW2003PropertyUtils.addVanishRProp(this);
        }
    }

    private void addPropertyElementInternal(Class<? extends Property> propertyClass, CDNode propertyElement) {
        Optional<? extends CDNode> prElOpt = findProperty(propertyClass).flatMap(property -> property.findFirst(propertyElement.getClass()));
        if (prElOpt.isPresent() && !propertyElement.instanceOfUnIdentifiedNode()) {
            CDNode prEl = prElOpt.get();
            prEl.getParent().replace(prEl, propertyElement);
        } else {
            findProperty(propertyClass).ifPresentOrElse(
                    pr -> {
                        if (propertyElement instanceof Composite c) {
                            c.assign(pr.asComposite());
                        }
                        pr.asComposite().add(propertyElement);
                    },
                    () -> {
                        if (propertyClass.isAssignableFrom(PProp.class)) {
                            final PProp pProp = new PProp();
                            if (propertyElement instanceof Composite c) {
                                c.assign(pProp);
                            }
                            pProp.assign(this);
                            pProp.add(propertyElement);
                            setProperty(pProp, false);
                        } else {
                            setNewProp(propertyClass, propertyElement);
                        }
                    }
            );
        }
    }

    private void setNewProp(Class<? extends Property> propertyClass, CDNode... propertyElements) {
        if (propertyClass.isAssignableFrom(RProp.class)) {
            setProperty(new RProp(propertyElements));
        } else if (propertyClass.isAssignableFrom(SectProp.class)) {
            setProperty(new SectProp(propertyElements));
        } else if (propertyClass.isAssignableFrom(TCellProp.class)) {
            setProperty(new TCellProp(propertyElements));
        } else if (propertyClass.isAssignableFrom(TRowProp.class)) {
            setProperty(new TRowProp(propertyElements));
        } else if (propertyClass.isAssignableFrom(TblProp.class)) {
            setProperty(new TblProp(propertyElements));
        } else if (propertyClass.isAssignableFrom(TblPropEx.class)) {
            setProperty(new TblPropEx(propertyElements));
        }
    }

    @Override
    public void close() throws IOException {
        if (attributes!=null) {
            attributes.clear();
            attributes = null;
        }
        if (getProperties()!=null) {
            getProperties().clear();
        }
        unAssign();
    }

    @Override
    public String getNameSpace() {
        return null;
    }

    @Override
    public String getTagName() {
        return TAG_NAME;
    }
}
