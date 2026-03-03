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
    public static final String ANCESTOR_SEPARATOR = "|";

    public static String buildTaskContextId(String processContextId, String subContext) {
        return processContextId + CONTEXT_SEPARATOR + subContext;
    }

    /**
     * Returns everything before the '#' separator.
     * May contain '|'-separated ancestor segments.
     * Examples:
     *   "1,2#1" -> "1,2"
     *   "1,2,5|1#0" -> "1,2,5|1"
     *   "1,2,5,6|1|0#0" -> "1,2,5,6|1|0"
     */
    public static String getLevel(String taskContextId) {
        int idx = taskContextId.indexOf(CONTEXT_SEPARATOR);
        return idx==-1 ?  taskContextId : taskContextId.substring(0, idx);
    }

    /**
     * Returns the instance number (everything after '#'), or null if no '#'.
     */
    @Nullable
    public static String getPath(String taskContextId) {
        if (StringUtils.contains(taskContextId, CONTEXT_SEPARATOR)) {
            return taskContextId.substring(taskContextId.indexOf(CONTEXT_SEPARATOR)+CONTEXT_SEPARATOR.length());
        }
        return null;
    }

    /**
     * Extracts the processContextId (first segment before first '|', or the whole level if no '|').
     * Examples:
     *   "1,2" -> "1,2"
     *   "1,2,5|1" -> "1,2,5"
     *   "1,2,5,6|1|0" -> "1,2,5,6"
     */
    public static String getProcessContextId(String level) {
        int idx = level.indexOf(ANCESTOR_SEPARATOR);
        return idx == -1 ? level : level.substring(0, idx);
    }

    /**
     * Extracts the ancestor path (everything after first '|'), or null if no '|'.
     * Examples:
     *   "1,2" -> null
     *   "1,2,5|1" -> "1"
     *   "1,2,5,6|1|0" -> "1|0"
     */
    @Nullable
    public static String getAncestorPath(String level) {
        int idx = level.indexOf(ANCESTOR_SEPARATOR);
        return idx == -1 ? null : level.substring(idx + ANCESTOR_SEPARATOR.length());
    }

    /**
     * Builds the sub-process level for entering a subprocess from a parent context.
     *
     * Format: processContextId[|ancestorInstance]*#instanceNumber
     * Uses '|' to separate processContextId from ancestor instance segments.
     * This makes parent derivation unambiguous at any nesting depth.
     *
     * Examples:
     *   getCurrTaskContextIdForSubProcesses("1", "1,2") -> "1,2"
     *   getCurrTaskContextIdForSubProcesses("1,2#1", "1,2,5") -> "1,2,5|1"
     *   getCurrTaskContextIdForSubProcesses("1,2,5|1#0", "1,2,5,6") -> "1,2,5,6|1|0"
     *   getCurrTaskContextIdForSubProcesses("1,2,5,6|1|0#2", "1,2,5,6,7") -> "1,2,5,6,7|1|0|2"
     */
    public static String getCurrTaskContextIdForSubProcesses(String currTaskContextId, String processContextId) {
        String parentPath = ContextUtils.getPath(currTaskContextId);
        if (parentPath == null) {
            // parent has no '#' suffix - just use the processContextId directly
            return processContextId;
        }
        String parentLevel = getLevel(currTaskContextId);
        String parentAncestorPath = getAncestorPath(parentLevel);
        // Append parent's ancestor path (if any) and parent's instance number
        String newAncestorPath = parentAncestorPath != null
                ? parentAncestorPath + ANCESTOR_SEPARATOR + parentPath
                : parentPath;
        return processContextId + ANCESTOR_SEPARATOR + newAncestorPath;
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

    /**
     * Compares two taskContextIds for sorting purposes (descending: deepest/most-specific first).
     * Format: processContextId[|ancestorInstance]*#instanceNumber
     *
     * Sort order (descending): longer processContextIds first, then by ancestor segments,
     * then by instance number (#suffix). This ensures variable lookup finds the most specific match first.
     */
    @SuppressWarnings("ConstantConditions")
    public static int compareTaskContextIds(String taskContextId1, String taskContextId2) {
        if (taskContextId1.equals(taskContextId2)) {
            return 0;
        }

        String level1 = getLevel(taskContextId1);
        String level2 = getLevel(taskContextId2);
        String path1 = getPath(taskContextId1);
        String path2 = getPath(taskContextId2);

        // Compare processContextId first (descending: longer/deeper first)
        String processCtxId1 = getProcessContextId(level1);
        String processCtxId2 = getProcessContextId(level2);

        String[] pca1 = StringUtils.split(processCtxId1, CONTEXT_DIGIT_SEPARATOR);
        String[] pca2 = StringUtils.split(processCtxId2, CONTEXT_DIGIT_SEPARATOR);

        if (pca1.length != pca2.length) {
            // Descending by depth: longer processContextId first
            return Integer.compare(pca2.length, pca1.length);
        }
        for (int i = 0; i < pca1.length; i++) {
            int cmp = Integer.compare(Integer.parseInt(pca2[i]), Integer.parseInt(pca1[i]));
            if (cmp != 0) {
                return cmp;
            }
        }

        // ProcessContextIds are equal - compare ancestor paths (descending)
        String ancestorPath1 = getAncestorPath(level1);
        String ancestorPath2 = getAncestorPath(level2);

        // Contexts with ancestors come before those without (more specific first)
        if (ancestorPath1 != null && ancestorPath2 == null) return -1;
        if (ancestorPath1 == null && ancestorPath2 != null) return 1;
        if (ancestorPath1 != null && ancestorPath2 != null) {
            String[] ap1 = ancestorPath1.split("\\|");
            String[] ap2 = ancestorPath2.split("\\|");
            if (ap1.length != ap2.length) {
                return Integer.compare(ap2.length, ap1.length);
            }
            for (int i = 0; i < ap1.length; i++) {
                int cmp = Integer.compare(Integer.parseInt(ap2[i]), Integer.parseInt(ap1[i]));
                if (cmp != 0) {
                    return cmp;
                }
            }
        }

        // Levels are equal - compare instance numbers (#suffix, descending)
        if (path1 != null && path2 == null) return -1;
        if (path1 == null && path2 != null) return 1;
        if (path1 != null && path2 != null) {
            return Integer.compare(Integer.parseInt(path2), Integer.parseInt(path1));
        }
        return 0;
    }

    public static List<String> sortSetAsTaskContextId(Collection<String> collection) {
        List<String> list = collection instanceof List listTemp ? listTemp : new ArrayList<>(collection);
        return list.stream().sorted(ContextUtils::compareTaskContextIds).collect(Collectors.toList());
    }

    /**
     * Derives the parent taskContextId from a given taskContextId.
     *
     * Format: processContextId[|ancestorInstance]*#instanceNumber
     *
     * Three categories:
     *
     * 1. Without '#' suffix (simple nested processContextId):
     *    "1,2,3" -> parent is "1,2" (strip last comma-component)
     *    "1,2" -> parent is "1"
     *    "1" -> null (root)
     *
     * 2. With '#' suffix, NO '|' (first-level fan-out):
     *    "1,2#8" -> parent is "1"
     *    Strip last comma-component from processContextId.
     *
     * 3. With '#' suffix AND '|' (deep nested fan-out):
     *    "1,2,5|1#0" -> parent is "1,2#1"
     *      segments = ["1,2,5", "1"], instance="0"
     *      parent processCtxId = strip last from "1,2,5" -> "1,2"
     *      pop last ancestor "1" -> becomes parent's instance
     *      no more ancestors -> parent = "1,2#1"
     *
     *    "1,2,5,6|1|0#0" -> parent is "1,2,5|1#0"
     *      segments = ["1,2,5,6", "1", "0"], instance="0"
     *      parent processCtxId = "1,2,5"
     *      pop last ancestor "0" -> parent's instance
     *      remaining ancestors = ["1"]
     *      parent = "1,2,5|1#0"
     *
     *    "1,2,5,6,7|1|0|2#0" -> parent is "1,2,5,6|1|0#2"
     */
    @Nullable
    public static String deriveParentTaskContextId(String taskContextId) {
        String level = getLevel(taskContextId);
        String path = getPath(taskContextId);

        String processCtxId = getProcessContextId(level);

        if (!processCtxId.contains(String.valueOf(CONTEXT_DIGIT_SEPARATOR))) {
            // Top-level context like "1" - no parent
            return null;
        }

        if (path == null) {
            // No '#' suffix - simple nested processContextId like "1,2,3"
            int lastComma = processCtxId.lastIndexOf(CONTEXT_DIGIT_SEPARATOR);
            return processCtxId.substring(0, lastComma);
        }

        String ancestorPath = getAncestorPath(level);

        // Strip last component from processContextId to go one level up in the DAG
        int lastComma = processCtxId.lastIndexOf(CONTEXT_DIGIT_SEPARATOR);
        String parentProcessCtxId = processCtxId.substring(0, lastComma);

        if (ancestorPath == null) {
            // First-level fan-out: "1,2#8" -> parent is "1"
            return parentProcessCtxId;
        }

        // Deep nested fan-out: pop the last ancestor segment
        int lastPipe = ancestorPath.lastIndexOf(ANCESTOR_SEPARATOR);
        if (lastPipe == -1) {
            // Single ancestor segment: "1,2,5|1#0" -> parent = "1,2#1"
            return parentProcessCtxId + CONTEXT_SEPARATOR + ancestorPath;
        }

        // Multiple ancestor segments: "1,2,5,6|1|0#0" -> parent = "1,2,5|1#0"
        String parentAncestorPath = ancestorPath.substring(0, lastPipe);
        String parentInstanceNumber = ancestorPath.substring(lastPipe + ANCESTOR_SEPARATOR.length());
        return parentProcessCtxId + ANCESTOR_SEPARATOR + parentAncestorPath + CONTEXT_SEPARATOR + parentInstanceNumber;
    }

}
