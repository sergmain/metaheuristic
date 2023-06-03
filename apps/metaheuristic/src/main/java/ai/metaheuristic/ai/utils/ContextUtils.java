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

import ai.metaheuristic.ai.dispatcher.data.TaskData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

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


}
