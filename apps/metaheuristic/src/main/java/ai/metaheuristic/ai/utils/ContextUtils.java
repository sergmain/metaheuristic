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

package ai.metaheuristic.ai.utils;

import ai.metaheuristic.ai.dispatcher.data.TaskData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import static ai.metaheuristic.ai.Consts.SECOND_LEVEL_CONTEXT_ID;
import static ai.metaheuristic.ai.Consts.TOP_LEVEL_CONTEXT_ID;

/**
 * @author Serge
 * Date: 4/7/2020
 * Time: 6:01 PM
 */
@Slf4j
public class ContextUtils {

    public static final String CONTEXT_SEPARATOR = "#";
    public static final char CONTEXT_DIGIT_SEPARATOR = ',';

    public static String buildTaskContextId(String processContextId, String subContext) {
        return processContextId + CONTEXT_SEPARATOR + subContext;
    }

    public static String getLevel(String taskContextId) {
        int idx = taskContextId.indexOf(CONTEXT_SEPARATOR);
        return idx==-1 ?  taskContextId : taskContextId.substring(0, idx);
    }

    @Nullable
    public static String getPath(String taskContextId) {
        if (StringUtils.contains(taskContextId, CONTEXT_SEPARATOR)) {
            return taskContextId.substring(taskContextId.indexOf(CONTEXT_SEPARATOR)+CONTEXT_SEPARATOR.length());
        }
        return null;
    }

    public static String getCurrTaskContextIdForSubProcesses(String currTaskContextId, String processContextId) {
        String level = processContextId;
        String path = ContextUtils.getPath(currTaskContextId);
        final String result = path!=null ? level + ContextUtils.CONTEXT_DIGIT_SEPARATOR + path : level;
        return result;
    }

    @Nullable
    public static String getParentContextId(String taskContextId) {
        if (TOP_LEVEL_CONTEXT_ID.equals(taskContextId)) {
            return null;
        }
        final String withoutSubContext = getLevel(taskContextId);
        if (SECOND_LEVEL_CONTEXT_ID.equals(withoutSubContext)) {
            return TOP_LEVEL_CONTEXT_ID;
        }
        // all other taskContextIds have form of x1,x2,...,xn,topSubContext#currProcessIndex

        throw new IllegalStateException();
    }


    public static TaskData.DetailedTaskContextId parse(String taskContextId) {
        final String level = getLevel(taskContextId);
        final String path = getPath(taskContextId);

        if (TOP_LEVEL_CONTEXT_ID.equals(level)) {
            return new TaskData.DetailedTaskContextId(TOP_LEVEL_CONTEXT_ID, null);
        }
        if (SECOND_LEVEL_CONTEXT_ID.equals(level)) {
            return new TaskData.DetailedTaskContextId(SECOND_LEVEL_CONTEXT_ID, path);
        }

        return new TaskData.DetailedTaskContextId(level, path);
    }

    public static TaskData.DetailedTaskContextId getTopDetailed(String curr) {
        int commas = StringUtils.countMatches(curr, ',');
        if (commas<2) {
            throw new IllegalStateException("(commas<2), "+ curr);
        }
        final int endIndex = curr.lastIndexOf(',');
        final int endIndexBase = curr.lastIndexOf(',', endIndex);

        return new TaskData.DetailedTaskContextId(curr.substring(0, endIndexBase), null);
    }

    @SuppressWarnings("ConstantConditions")
    public static int compareTaskContextIds(String taskContextId1, String taskContextId2) {
        if (taskContextId1.equals(taskContextId2)) {
            return 0;
        }

        String[] split1 = taskContextId1.split(",");
        String[] split2 = taskContextId2.split(",");
        if (split1.length!=split2.length) {
            return split1.length>split2.length ? -1 : 1;
        }
        for (int i = 0; i < split1.length; i++) {
            String s1 = split1[i];
            String s2 = split2[i];
            int cmp;
            if (s1.contains(CONTEXT_SEPARATOR) || s2.contains(CONTEXT_SEPARATOR)) {
                String sc1 = getLevel(s1);
                String sc2 = getLevel(s2);
                String sub1 = getPath(s1);
                String sub2 = getPath(s2);
                if (sc1.equals(sc2)) {
                    if (sub1==null && sub2!=null) {
                        return 1;
                    }
                    else if (sub1!=null && sub2==null) {
                        return -1;
                    }
                    else if (sub1!=null && sub2!=null) {
                        return sub2.compareTo(sub1);
                    }
                }
                else {
                    return sc2.compareTo(sc1);
                }
                cmp = s2.compareTo(s1);
            }
            else {
                cmp = s2.compareTo(s1);
            }
            if (cmp!=0) {
                return cmp;
            }
        }
        throw new IllegalStateException("?");
    }

    public static List<String> sortSetAsTaskContextId(Collection<String> collection) {
        List<String> list = collection instanceof List listTemp ? listTemp : new ArrayList<>(collection);
        return list.stream().sorted(ContextUtils::compareTaskContextIds).collect(Collectors.toList());
    }

    /**
     * Derives the parent taskContextId from a given taskContextId.
     *
     * Two categories of taskContextId:
     *
     * 1. Without '#' suffix (simple nested processContextId):
     *    "1,1,1" → parent is "1,1" (strip last comma-component)
     *    "1,1" → parent is "1"
     *    "1" → null (root)
     *
     * 2. With '#' suffix (fan-out or path-propagated):
     *    "1,2#8" → parent is "1" (the batch-splitter task that created this fan-out)
     *    "1,2,3,8#0" → parent is "1,2#8" (path propagation from parent)
     *    "1,2,3,4,8,0#0" → parent is "1,2,3,8#0" (deeper nesting)
     *
     * For case 2, the level (before '#') is composed of processContextId + propagated path components.
     * Given level = "X1,...,Xn" where the last components are propagated paths:
     * We need to find where the processContextId ends and the path begins.
     *
     * The pattern for path-propagated contexts:
     * If level has format processCtxId + "," + parentPath components, then:
     * - Strip last path component to get the remaining level
     * - The parent's level is the processContextId without the last digit
     * - The parent's suffix is the last path component stripped
     */
    @Nullable
    public static String deriveParentTaskContextId(String taskContextId) {
        String level = getLevel(taskContextId);
        String path = getPath(taskContextId);

        if (!level.contains(String.valueOf(CONTEXT_DIGIT_SEPARATOR))) {
            // Top-level context like "1" — no parent
            return null;
        }

        if (path == null) {
            // No '#' suffix — simple nested processContextId like "1,1,1"
            // Parent is the context with last comma-component removed
            int lastComma = level.lastIndexOf(CONTEXT_DIGIT_SEPARATOR);
            return level.substring(0, lastComma);
        }

        // Has '#' suffix — fan-out or path-propagated context
        // level = processContextId + "," + path_components (from getCurrTaskContextIdForSubProcesses)
        //
        // Examples:
        // "1,2#8" → level="1,2", path="8" → parent is "1"
        //   (the processContextId is "1,2", the batch-splitter at parent level "1" created instance #8)
        //   parent level = strip last comma-component from processContextId = "1"
        //
        // "1,2,3,8#0" → level="1,2,3,8", path="0"
        //   This was created by getCurrTaskContextIdForSubProcesses("1,2#8", "1,2,3")
        //   = "1,2,3" + "," + "8" = "1,2,3,8", then buildTaskContextId("1,2,3,8", "0")
        //   The last component of level ("8") is the parent's suffix
        //   The rest ("1,2,3") is the child's processContextId
        //   Parent's level = strip last from child's processContextId = "1,2"
        //   Parent = "1,2#8"

        int lastComma = level.lastIndexOf(CONTEXT_DIGIT_SEPARATOR);
        String beforeLastComma = level.substring(0, lastComma);
        String lastComponent = level.substring(lastComma + 1);

        // beforeLastComma is the child's processContextId (or prefix thereof)
        // lastComponent is the propagated path from the parent's suffix

        int parentLevelEnd = beforeLastComma.lastIndexOf(CONTEXT_DIGIT_SEPARATOR);
        if (parentLevelEnd == -1) {
            // beforeLastComma is a single value like "1" — parent is top-level
            return beforeLastComma;
        }

        String parentLevel = beforeLastComma.substring(0, parentLevelEnd);
        return parentLevel + CONTEXT_SEPARATOR + lastComponent;
    }

}
