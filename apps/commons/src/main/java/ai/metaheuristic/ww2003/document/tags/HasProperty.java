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

package ai.metaheuristic.ww2003.document.tags;

import ai.metaheuristic.ww2003.document.CDNode;
import ai.metaheuristic.ww2003.document.tags.xml.Attr;

import java.util.Optional;
import java.util.stream.Stream;

public interface HasProperty extends CDNode {

    @SuppressWarnings("unchecked")
    default <T extends Property> Optional<T> findProperty(Class<T> propClass) {
        return (Optional<T>) streamProperties()
                .filter(property -> propClass.isAssignableFrom(property.getClass()))
                .findFirst();
    }

    default <T extends Property, U extends PropertyElement> Optional<U> findProperty(Class<T> propClass, Class<U> propElementClass) {
        return findProperty(propClass).flatMap(property -> property.findFirst(propElementClass));
    }

    default <T extends Property, U extends PropertyElement> Optional<Attr> findProperty(Class<T> propClass, Class<U> propElementClass, String attrName) {
        return findProperty(propClass, propElementClass).flatMap(u -> u.findAttributeByName(attrName));
    }

    default <T extends Property> boolean hasProperty(Class<T> propClass) {
        return findProperty(propClass).isPresent();
    }

    default <T extends Property, U extends PropertyElement> boolean hasProperty(Class<T> propClass, Class<U> propElementClass) {
        return findProperty(propClass, propElementClass).isPresent();
    }

    default <T extends Property, U extends PropertyElement> boolean hasProperty(Class<T> propClass, Class<U> propElementClass, String attrName, String attrValue) {
        return findProperty(propClass, propElementClass)
                .map(propElement -> propElement.findAttributeByName(attrName).filter(attr -> attr.value.equals(attrValue)).isPresent())
                .orElse(false);
    }

    void setProperty(Property property);

    void setProperty(Property property, boolean process);

    void removeProperty(Class<? extends Property> clazz);

    void addPropertyElement(Class<? extends Property> propertyClass, PropertyElement propertyElement);

    void addPropertyElement(Class<? extends Property> propertyClass, PropertyElement propertyElement, boolean process);

    Stream<Property> streamProperties();

    int propertiesSize();

}