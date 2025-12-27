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

import ai.metaheuristic.commons.S;

import ai.metaheuristic.ww2003.document.exceptions.DocumentProcessingException;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
@EqualsAndHashCode(of={"nameSpace", "name", "value"})
public class Attr implements Comparable<Attr> {

    @Nullable
    public final String nameSpace;
    public final String name;
    public final String value;
    private static final ArrayList<String> ATTR_ORDER = new ArrayList<>();
    private static final Map<String, Attr> ATTR_POOL = new HashMap<>();

    static {
        ATTR_ORDER.add("val");
        ATTR_ORDER.add("bidi");
        ATTR_ORDER.add("sz");
        ATTR_ORDER.add("color");
        ATTR_ORDER.add("fill");
        ATTR_ORDER.add("w");
        ATTR_ORDER.add("h");
        ATTR_ORDER.add("id");
        ATTR_ORDER.add("type");
        ATTR_ORDER.add("style");
        ATTR_ORDER.add("default");
        ATTR_ORDER.add("styleId");
        ATTR_ORDER.add("first-line");
        ATTR_ORDER.add("top");
        ATTR_ORDER.add("left");
        ATTR_ORDER.add("right");
        ATTR_ORDER.add("bottom");
        ATTR_ORDER.add("macrosPresent");
        ATTR_ORDER.add("embeddedObjPresent");
        ATTR_ORDER.add("ocxPresent");
        ATTR_ORDER.add("space");
        ATTR_ORDER.add("h-ansi");
        ATTR_ORDER.add("cs");
    }

    private static final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private static final ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private static final ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public static Attr get(@Nullable String nameSpace, String name, String value) {
        if ("null".equals(nameSpace)) {
            throw new DocumentProcessingException("051.020 (\"null\".equals(nameSpace))");
        }
        String ns = S.b(nameSpace) ? "" :nameSpace;
        String key = ns + ':' + name + ':' + value;

        writeLock.lock();
        try {
            return ATTR_POOL.computeIfAbsent(key, (o) -> new Attr(ns, name, value));
        } finally {
            writeLock.unlock();
        }
    }

    public static void destroy() {
        writeLock.lock();
        try {
            ATTR_POOL.clear();
            ATTR_ORDER.clear();
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public int compareTo(Attr attr) {
        int nsCompare = StringUtils.compare(this.nameSpace, attr.nameSpace);
        if (nsCompare!=0) {
            return nsCompare;
        }
        int ind1;
        int ind2;
        writeLock.lock();
        try {
            ind1 = ATTR_ORDER.indexOf(name);
            ind2 = ATTR_ORDER.indexOf(attr.name);
        } finally {
            writeLock.unlock();
        }
        return Integer.compare(ind1, ind2);
    }

    public String asString() {
        return (S.b(nameSpace) ?  "<NONE> : " : nameSpace + " : ") + name + " : " + value;
    }

}
