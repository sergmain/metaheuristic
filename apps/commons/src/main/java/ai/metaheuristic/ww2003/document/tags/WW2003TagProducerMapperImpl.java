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

import ai.metaheuristic.commons.S;
import ai.metaheuristic.ww2003.document.tags.xml.XmlTag;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Serge
 * Date: 5/9/2022
 * Time: 10:10 AM
 */
@SuppressWarnings("UnnecessaryLocalVariable")
public class WW2003TagProducerMapperImpl implements TagProducerMapper {

    private static final Map<String, TagProducer> producers = new HashMap<>();

    public WW2003TagProducerMapperImpl() {
        producers.put(XmlTag.class.getName(), new TagProducer(
                (n)->{
                    XmlTag xmlTag = ((XmlTag)n);
                    String nameSpace = xmlTag.getNameSpace();
                    if (S.b(nameSpace)) {
                        return '<' + xmlTag.getTagName() + '>';
                    }
                    return '<' + xmlTag.getNameSpace() + ':' + xmlTag.getTagName() + '>';
                },
                (n) -> {
                    XmlTag xmlTag = ((XmlTag)n);
                    String nameSpace = xmlTag.getNameSpace();
                    if (S.b(nameSpace)) {
                        return "</" + xmlTag.getTagName() + '>';
                    }
                    return "</" + xmlTag.getNameSpace() + ':' + xmlTag.getTagName() + '>';
                }));
    }

    private static final TagProducer NULL_TAG_PRODUCER = new TagProducer(TagProducer.NULL_TAG, TagProducer.NULL_TAG);

    @Override
    public TagProducer map(String clazzName) {
        TagProducer tagProducer = producers.getOrDefault(clazzName, NULL_TAG_PRODUCER);
        return tagProducer;
    }
}
