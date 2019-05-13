/*
 * AiAi, Copyright (C) 2017-2019  Serge Maslyukov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package aiai.ai.snippet;

import aiai.ai.launchpad.beans.ExperimentSnippet;
import aiai.ai.launchpad.snippet.SnippetService;
import metaheuristic.api.v1.EnumsApi;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestSnippet {

    @Test
    public void testSnippetSorting() {

        List<ExperimentSnippet> snippets = new ArrayList<>();
        snippets.add(new ExperimentSnippet("aaa", EnumsApi.ExperimentTaskType.PREDICT.toString(), 1L));
        snippets.add(new ExperimentSnippet("bbb", EnumsApi.ExperimentTaskType.FIT.toString(), 2L));
        SnippetService.sortSnippetsByType(snippets);

        assertEquals(EnumsApi.ExperimentTaskType.FIT, EnumsApi.ExperimentTaskType.valueOf(snippets.get(0).getType()));
        assertEquals(EnumsApi.ExperimentTaskType.PREDICT, EnumsApi.ExperimentTaskType.valueOf(snippets.get(1).getType()));

    }
}
