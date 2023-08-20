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

package ai.metaheuristic.ww2003.document.tags.xml;

import ai.metaheuristic.ww2003.document.Leaf;
import ai.metaheuristic.ww2003.document.exceptions.UtilsExecutingException;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import javax.annotation.Nullable;

import java.util.regex.Pattern;

@SuppressWarnings({"UnusedReturnValue"})
public class Text extends Leaf implements XmlTag, TextContainer {

    private static final String NS = "w";
    private static final String TAG_NAME = "t";

    @Getter
    private String text;

    public Text(String text) {
        super();
        this.text = text;
    }

    public Text() {
        this("");
    }

    public Text concat(String str) {
        text += str;
        return this;
    }

    @SuppressWarnings("ConstantConditions")
    public void setText(String text) {
        if (text==null) {
            throw new UtilsExecutingException("044.100 new text must be not null");
        }
        this.text = text;
    }

    public int indexOf(int ch) {
        return text.indexOf(ch);
    }

    public int lastIndexOf(int ch) {
        return text.lastIndexOf(ch);
    }

    public int indexOf(int ch, int fromIndex) {
        return text.indexOf(ch, fromIndex);
    }

    public int indexOf(String str, int fromIndex) {
        return text.indexOf(str, fromIndex);
    }

    public int indexOf(String str) {
        return text.indexOf(str);
    }

    public int lastIndexOf(String str) {
        return text.lastIndexOf(str);
    }

    public Text replace(String target, String replacement) {
        text = text.replace(target, replacement);
        return this;
    }

    public Text replaceIgnoreCase(String target, String replacement) {
        text = StringUtils.replaceIgnoreCase(text, target, replacement);
        return this;
    }

    public Text replaceAll(Pattern pattern, String replacement) {
        text = pattern.matcher(text).replaceAll(replacement);
        return this;
    }

    public Text replaceFirst(Pattern pattern, String replacement) {
        text = pattern.matcher(text).replaceFirst(replacement);
        return this;
    }

    public Text replaceLast(String target, String replacement) {
        final int index = text.lastIndexOf(target);
        if (index != -1) {
            text = text.substring(0, index) + replacement + text.substring(index + target.length());
        }
        return this;
    }

    public Text insert(String string, int index) {
        text = text.substring(0, index) + string + text.substring(index);
        return this;
    }

    public boolean startsWith(String prefix) {
        return text.startsWith(prefix);
    }

    public boolean endsWith(String suffix) {
        return text.endsWith(suffix);
    }

    public String substring(int beginIndex, int endIndex) {
        return text.substring(beginIndex, endIndex);
    }

    public String substring(int beginIndex) {
        return text.substring(beginIndex);
    }

    public int length() {
        return text.length();
    }

    @Nullable
    @Override
    public String tag() {
        return null;
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    public String getNameSpace() {
        return NS;
    }

    @Override
    public String getTagName() {
        return TAG_NAME;
    }

}
