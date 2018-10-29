/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.core;

import aiai.ai.launchpad.beans.TaskSnippet;
import aiai.ai.launchpad.experiment.ExperimentsController;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class TestSnippetSorting {


    @Test
    public void sort() {
        List<TaskSnippet> snippets = new ArrayList<>();
        TaskSnippet s1 = new TaskSnippet();
        s1.type = "predict";
        TaskSnippet s2 = new TaskSnippet();
        s2.type = "fit";
        Collections.addAll(snippets, s1, s2);
        assertEquals("predict", snippets.get(0).type);
        assertEquals("fit", snippets.get(1).type);
        ExperimentsController.sortSnippetsByType(snippets);
        assertEquals("fit", snippets.get(0).type);
        assertEquals("predict", snippets.get(1).type);
    }
}
