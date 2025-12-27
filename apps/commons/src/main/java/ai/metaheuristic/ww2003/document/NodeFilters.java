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

package ai.metaheuristic.ww2003.document;

import ai.metaheuristic.ww2003.Enums;
import ai.metaheuristic.ww2003.document.tags.xml.Run;
import ai.metaheuristic.ww2003.document.tags.xml.Text;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Serge
 * Date: 5/7/2022
 * Time: 11:01 PM
 */
public class NodeFilters {

    public static final FindFirstFilter FIND_FIRST_FILTER = new FindFirstFilter();
    public static final FirstCharInRunLetterOrDigitFindFirstFilter FIRST_CHAR_IN_RUN_LETTER_OR_DIGIT_FIND_FIRST_FILTER = new FirstCharInRunLetterOrDigitFindFirstFilter();

    public static final PositiveFilter POSITIVE_FILTER = new PositiveFilter();
    public static final Filter[] POSITIVE_FILTERS = new NodeFilters.Filter[]{ NodeFilters.POSITIVE_FILTER };

    public record FilterResult(Enums.ContinueStrategy strategy, boolean accepted) {}

    public static final FilterResult ACCEPT_AND_CONTINUE_RESULT = new FilterResult(Enums.ContinueStrategy.non_stop, true);
    public static final FilterResult ACCEPT_AND_STOP_RESULT = new FilterResult(Enums.ContinueStrategy.stop, true);
    public static final FilterResult SKIP_AND_CONTINUE_RESULT = new FilterResult(Enums.ContinueStrategy.non_stop, false);
    public static final FilterResult SKIP_AND_STOP_RESULT = new FilterResult(Enums.ContinueStrategy.stop, false);

    public interface Filter {
        Function<CDNode, FilterResult> getFilter();
    }

    @NoArgsConstructor
    public static  class FilteringContext<T extends CDNode> {
        public final LinkedList<CDNode> list = new LinkedList<>();
        public Consumer<T> consumer = list::add;
        public ai.metaheuristic.ww2003.document.Enums.Relation relation = ai.metaheuristic.ww2003.document.Enums.Relation.DESCENDANT;

        public FilteringContext(ai.metaheuristic.ww2003.document.Enums.Relation relation) {
            this.relation = relation;
        }

        public FilteringContext(ai.metaheuristic.ww2003.document.Enums.Relation relation, Consumer<T> consumer) {
            this.relation = relation;
            this.consumer = consumer;
        }
    }

    public record ExcludeNodesFilter(CDNode ... excludes) implements Filter {
        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                boolean exclude= false;
                for (CDNode cdNode : excludes) {
                    if (cdNode==node) {
                        exclude=true;
                        break;
                    }
                }
                return new FilterResult(Enums.ContinueStrategy.non_stop, !exclude);
            };
        }
    }

    public static final class StartStopNodesFilter implements Filter {
        private final CDNode start;
        private final CDNode stop;
        boolean startAccepting = false;

        public StartStopNodesFilter(CDNode start, CDNode stop) {
            this.start = start;
            this.stop = stop;
        }

        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                if (node==start) {
                    startAccepting = true;
                }
                if (node==stop) {
                    return new FilterResult(Enums.ContinueStrategy.stop, startAccepting);
                }
                return new FilterResult(Enums.ContinueStrategy.non_stop, startAccepting);
            };
        }
    }

    public record ExcludeNodesAsListFilter(List<CDNode> excludes) implements Filter {
        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                boolean exclude= false;
                for (CDNode cdNode : excludes) {
                    if (cdNode==node) {
                        exclude=true;
                        break;
                    }
                }
                return new FilterResult(Enums.ContinueStrategy.non_stop, !exclude);
            };
        }
    }

    public record TextEqualsFilter(String searchText) implements Filter {
        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                if (node instanceof Text text) {
                    return new FilterResult(Enums.ContinueStrategy.non_stop, text.getText().equalsIgnoreCase(this.searchText));
                }
                return SKIP_AND_CONTINUE_RESULT;
            };
        }
    }

    public record TextContainsFilter(String searchText) implements Filter {
        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                if (node instanceof Text text) {
                    return new FilterResult(Enums.ContinueStrategy.non_stop, StringUtils.containsIgnoreCase(text.getText(), searchText));
                }
                return SKIP_AND_CONTINUE_RESULT;
            };
        }
    }

    public record TextEndsFilter(String searchText) implements Filter {
        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                if (node instanceof Text text) {
                    return new FilterResult(Enums.ContinueStrategy.non_stop, text.getText().endsWith(searchText));
                }
                return SKIP_AND_CONTINUE_RESULT;
            };
        }
    }

    public record TextStartsFilter(String searchText) implements Filter {
        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                if (node instanceof Text text) {
                    return new FilterResult(Enums.ContinueStrategy.non_stop, text.getText().startsWith(searchText));
                }
                return SKIP_AND_CONTINUE_RESULT;
            };
        }
    }

    public static class PositiveFilter implements Filter {
        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> new FilterResult(Enums.ContinueStrategy.non_stop, true);
        }
    }

    public static class CountFilter implements Filter {
        private int count=0;
        private final int max;

        public CountFilter(int max) {
            this.max = max;
        }

        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                ++count;
                return new FilterResult(count<=max ? Enums.ContinueStrategy.non_stop : Enums.ContinueStrategy.stop, true);
            };
        }
    }

    public static class StartWithNodeAndCountFilter implements Filter {
        private final int max;
        private final CDNode node;
        private int count=0;
        private boolean found;

        public StartWithNodeAndCountFilter(int max, CDNode node) {
            this.max = max;
            this.node = node;
        }

        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                if (found) {
                    ++count;
                    return new FilterResult(count <= max ? Enums.ContinueStrategy.non_stop : Enums.ContinueStrategy.stop, true);
                }
                if (node==this.node) {
                    found = true;
                }
                return new FilterResult(Enums.ContinueStrategy.non_stop, false);
            };
        }
    }

    public static class FindFirstFilter extends CountFilter {
        public FindFirstFilter() {
            super(1);
        }
    }

    public static class InstanceFilter implements Filter {
        private final Class[] clazzs;

        public InstanceFilter(Class ... clazz) {
            this.clazzs = clazz;
        }

        private boolean isInstanceOf(Object o) {
            Class c = o.getClass();
            for (Class clazz : clazzs) {
                if (clazz.isAssignableFrom(c)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                if (isInstanceOf(node)) {
                    return new FilterResult(Enums.ContinueStrategy.non_stop, true);
                }
                return SKIP_AND_CONTINUE_RESULT;
            };
        }
    }

    public static final class InstanceFindFirstFilter implements Filter {
        private final Class[] clazzs;

        public InstanceFindFirstFilter(Class ... clazz) {
            this.clazzs = clazz;
        }

        private boolean isInstanceOf(Object o) {
            Class c = o.getClass();
            for (Class clazz : clazzs) {
                if (clazz.isAssignableFrom(c)) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                if (isInstanceOf(node)) {
                    return new FilterResult(Enums.ContinueStrategy.stop, true);
                }
                return SKIP_AND_CONTINUE_RESULT;
            };
        }
    }

    public static class FirstCharInRunLetterOrDigitFindFirstFilter implements Filter {
        @Override
        public Function<CDNode, FilterResult> getFilter() {
            return (node) -> {
                if (node instanceof Run run) {
                    Text text = run.findFirst(Text.class).orElse(null);
                    boolean is;
                    if (text != null) {
                        String s = text.getText().strip();
                        is = s.length() > 0 && Character.isLetterOrDigit(s.charAt(0));
                    }
                    else {
                        is = false;
                    }
                    if (is) {
                        return ACCEPT_AND_STOP_RESULT;
                    }
                }
                return SKIP_AND_CONTINUE_RESULT;
            };
        }
    }
}
