/*
 * Metaheuristic, Copyright (C) 2017-2021, Innovation platforms, LLC
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

package ai.metaheuristic.ai.utils;

import org.junit.jupiter.api.Test;
import org.springframework.lang.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static ai.metaheuristic.ai.utils.CollectionUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Serge
 * Date: 11/13/2020
 * Time: 4:22 PM
 */
public class TestCollectionUtils {

    @Test
    public void test() {
        assertTrue(checkTagAllowed(null, null));
        assertTrue(checkTagAllowed(null, " "));
        assertTrue(checkTagAllowed(" ", null));
        assertTrue(checkTagAllowed(" ", " "));

        assertFalse(checkTagAllowed("aaa", null));
        assertFalse(checkTagAllowed("aaa", " "));

        assertFalse(checkTagAllowed("aaa, bbb", null));
        assertFalse(checkTagAllowed("aaa, bbb", " "));

        assertTrue(checkTagAllowed("", "aaa"));
        assertTrue(checkTagAllowed(null, "aaa"));

        assertTrue(checkTagAllowed(" aaa ", " aaa, ccc "));
        assertTrue(checkTagAllowed("aaa", " aaa, ccc "));
        assertTrue(checkTagAllowed("aaa", "aaa"));
        assertTrue(checkTagAllowed("aaa", "aaa,"));
        assertTrue(checkTagAllowed("aaa", " aaa, "));

        assertFalse(checkTagAllowed("aaa, bbb", "aaa, ccc"));
        assertFalse(checkTagAllowed(" aaa, bbb ", " aaa, ccc "));

        assertFalse(checkTagAllowed("ddd, bbb", "aaa, ccc"));
        assertFalse(checkTagAllowed(" ddd, bbb ", " aaa, ccc "));
        assertFalse(checkTagAllowed(" ddd ", " aaa, ccc "));
        assertFalse(checkTagAllowed("ddd", " aaa, ccc "));
        assertFalse(checkTagAllowed("ddd", "aaa"));
        assertFalse(checkTagAllowed("ddd", "aaa,"));
        assertFalse(checkTagAllowed("ddd", " aaa, "));
    }

    @Test
    public void testListEquals() {
        assertTrue(isEquals(null, null));
        assertTrue(isEquals(null, List.of()));
        assertTrue(isEquals(List.of(), null));

        assertFalse(isEquals(null, List.of("aaa")));
        assertFalse(isEquals(List.of("aaa"), null));

        assertFalse(isEquals(List.of(), List.of("aaa")));
        assertFalse(isEquals(List.of("aaa"), List.of()));

        assertTrue(isEquals(List.of("aaa"), List.of("aaa")));
        assertTrue(isEquals(List.of("aaa"), List.of("aaa")));

        assertFalse(isEquals(List.of("aaa"), List.of("aaa", "bbb")));
        assertFalse(isEquals(List.of("aaa"), List.of("aaa", "bbb")));

        assertTrue(isEquals(List.of("aaa", "bbb"), List.of("aaa", "bbb")));
        assertTrue(isEquals(List.of("aaa", "bbb"), List.of("aaa", "bbb")));

        assertTrue(isEquals(List.of("bbb", "aaa"), List.of("aaa", "bbb")));
        assertTrue(isEquals(List.of("bbb", "aaa"), List.of("aaa", "bbb")));
    }

    public static final List<String> list = List.of("1", "2", "3", "4", "5");

    @Test
    public void testPaging() {
        List<List<String>> pages;

        pages = parseAsPages(list, 0);
        assertEquals(0, pages.size());

        pages = parseAsPages(List.of(), 0);
        assertEquals(0, pages.size());

        pages = parseAsPages(list, 1);
        assertEquals(5, pages.size());
        assertEquals(List.of("1"), pages.get(0));
        assertEquals(List.of("2"), pages.get(1));
        assertEquals(List.of("3"), pages.get(2));
        assertEquals(List.of("4"), pages.get(3));
        assertEquals(List.of("5"), pages.get(4));

        pages = parseAsPages(list, 4);
        assertEquals(2, pages.size());
        assertEquals(List.of("1", "2", "3", "4"), pages.get(0));
        assertEquals(List.of("5"), pages.get(1));

        pages = parseAsPages(list, 6);
        assertEquals(1, pages.size());
        assertEquals(List.of("1", "2", "3", "4", "5"), pages.get(0));
    }

    static class SimpleTreeItem implements TreeUtils.TreeItem<Long> {
        Long id;
        @Nullable
        Long topId;
        @Nullable
        List<TreeUtils.TreeItem<Long>> subTree;

        public SimpleTreeItem(Long id, @Nullable Long topId, @Nullable List<TreeUtils.TreeItem<Long>> tree) {
            this.id=id;
            this.topId = topId==null || topId==0L ? null : topId;
            this.subTree=tree;
        }

        @Nullable
        public Long getTopId() {
            return topId;
        }

        public Long getId() {
            return id;
        }

        @Nullable
        public List<TreeUtils.TreeItem<Long>> getSubTree() {
            return subTree;
        }

        public void setSubTree(List<TreeUtils.TreeItem<Long>> list) {
            this.subTree = list;
        }

        @Override
        public String toString() {
            return "SimpleTreeItem{" +
                   "id=" + id +
                   ", topId=" + topId +
                   '}';
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            SimpleTreeItem that = (SimpleTreeItem) o;

            if (!id.equals(that.id)) return false;
            if (topId != null ? !topId.equals(that.topId) : that.topId != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + (topId != null ? topId.hashCode() : 0);
            return result;
        }
    }

    @Test
    public void testGetParentAsSet() {
        LinkedList<TreeUtils.TreeItem<Long>> root = new LinkedList<>();
        root.add(new SimpleTreeItem(1L, 0L, null));
        root.add(new SimpleTreeItem(2L, 0L, null));
        root.add(new SimpleTreeItem(3L, 0L, null));
        root.add(new SimpleTreeItem(4L, 0L, null));

        LinkedList<TreeUtils.TreeItem<Long>> leaf = new LinkedList<>();
        leaf.add(new SimpleTreeItem(10L, 1L, null));
        leaf.add(new SimpleTreeItem(11L, 1L, null));
        leaf.add(new SimpleTreeItem(12L, 1L, null));

        leaf.add(new SimpleTreeItem(100L, 10L, null));
        leaf.add(new SimpleTreeItem(101L, 10L, null));
        leaf.add(new SimpleTreeItem(102L, 10L, null));
        leaf.add(new SimpleTreeItem(103L, 10L, null));
        leaf.add(new SimpleTreeItem(104L, 10L, null));

        leaf.add(new SimpleTreeItem(21L, 1L, null));
        leaf.add(new SimpleTreeItem(21L, 2L, null));
        leaf.add(new SimpleTreeItem(21L, 3L, null));

        leaf.add(new SimpleTreeItem(30L, 3L, null));
        leaf.add(new SimpleTreeItem(30L, 100L, null));
        leaf.add(new SimpleTreeItem(30L, 21L, null));

        LinkedList<TreeUtils.TreeItem<Long>> list = new LinkedList<>();
        list.addAll(root);
        list.addAll(leaf);

        final TreeUtils<Long> longTreeUtils = new TreeUtils<>();
        List<TreeUtils.TreeItem<Long>> result = longTreeUtils.rebuildTree(list);
        assertEquals(root.size(), result.size(), "Count items in top level wrong");


        Set<TreeUtils.TreeItem<Long>> items = longTreeUtils.toPlainList(result);
        assertEquals(list.size(), items.size());
        for (TreeUtils.TreeItem<Long> treeItem : list) {
            if (!items.contains(treeItem)) {
                throw new RuntimeException("Not found item: " + treeItem);
            }
        }

        Map<Long, List<TreeUtils.TreeItem<Long>>> map = longTreeUtils.fillParentMap(result);
        assertTrue(map.get(1L).isEmpty());
        assertTrue(map.get(2L).isEmpty());
        assertTrue(map.get(3L).isEmpty());
        assertTrue(map.get(4L).isEmpty());

        assertEquals(1, map.get(10L).size());
        assertEquals(1, map.get(11L).size());
        assertEquals(1, map.get(12L).size());

        assertEquals(1, map.get(100L).size());

        assertEquals(5, map.get(30L).size());
    }

}
