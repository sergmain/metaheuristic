/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
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

package ai.metaheuristic.ai.dispatcher.exec_context_segment;

import ai.metaheuristic.commons.utils.ContextUtils;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The shapes shared by the ExecContext-segment tests (041-EXEC-CONTEXT-SEGMENTS-PLAN, section 8.2).
 *
 * <p>Phase 1 carries the SourceCode half: each shape is a {@code .mhsc} built from MH internal Functions only
 * ({@code mh.nop}, {@code mh.batch-line-splitter}), plus the value of its source-level input {@code items}.
 * The splitters split {@code items} one line per Task, so the number of lines decides how many lines each
 * fork produces. Phase 4 adds the DOT half of the same shapes for the Spring-less tests.
 *
 * <p>Why a source-level input and not {@code mh.evaluation}: the evaluation context resolves every type to
 * String and refuses every method call, and the {@code .mhsc} string reader does not unescape {@code \n},
 * so an evaluation expression cannot produce a multi-line value.
 *
 * <p>The shapes are named after the observations the plan lists, not copied from them - MH knows nothing
 * of RG, so each keeps only the topology:
 * <ul>
 *   <li>{@link #S1} - a top-level chain with a static {@code parallel} fork and a splitter whose lines are
 *       in-band grafts, closed by a {@code tag terminal} join. The splitter is NOT last in its chain, so
 *       every line's tail joins the next task of that chain.</li>
 *   <li>{@link #S2} - S1's splitter lines each end in a second splitter. That inner splitter IS last in its
 *       line, so its lines' tails resolve to the enclosing join: many forks, one join.</li>
 *   <li>{@link #S6} - three splitters nested that way; the innermost tails skip two levels.</li>
 *   <li>{@link #S5} - F1: an in-band RUN_NOW graft that is the LAST element of a sequential block, so its target is
 *       the chain tail and the grafted line's tail must rejoin the enclosing join.</li>
 *   <li>{@link #S1_PLACE_NOW} - S1 with the in-band graft laid PLACE_NOW instead of RUN_NOW.</li>
 *   <li>{@link #K1} ... {@link #K5} - skip propagation. A Task fails deterministically by writing to an undeclared
 *       variable ({@code 509.060}), independent of any data. K1 and K2: a wrapper forks two grafted lines (two grafts
 *       in one sequential block) - K1 one failing and one healthy line, K2 two failing lines - into a plain join.
 *       K4: a static {@code parallel} fork whose two branches both fail, into a plain join. K3 and K5: the join's
 *       only parent - the chain predecessor - fails, and the join is {@code tag terminal} (K3) or plain (K5).
 *       {@code mh.finish} is the leaf in all of them.</li>
 *   <li>{@link #R1} - reset: a top-level chain, a splitter whose grafted lines have three Tasks each (so one can be
 *       reset mid-line), and a {@code tag terminal} join.</li>
 * </ul>
 *
 * <p><b>DOT half</b> (Phase 4, for the Spring-less algebra tests): {@link DotShape}s. S1, S2 and S3 are the graphs of
 * the RG ExecContexts the plan names (325, 328, 334), stored verbatim under {@code /segment/dot/}; S8 is S3 (its splitter
 * fork carries both line bases {@code 1,6#…} and {@code 1,17#…}). S4, S5, S6 are hand-written in the same form; S7 is
 * generated; X1-X3 are invalid on purpose. Which lines start a segment cannot be read from a DOT (a static
 * {@code parallel} branch and a splitter line look alike), so each shape carries the ctx levels of its splitter / graft
 * lines - what its SourceCode knows.
 */
public final class SegmentFixtureShapes {

    private SegmentFixtureShapes() {
    }

    /**
     * @param id    the shape id used in the plan (S1, S2, ...)
     * @param mhsc  the {@code .mhsc} source; its uid is fixed, so the SourceCode is created once and reused
     * @param items the value of the source-level input {@code items}, one line per splitter line; null for a
     *              shape that declares no input
     */
    public record Shape(String id, String mhsc, @Nullable String items) {
    }

    public static final Shape S1 = new Shape("S1", """
            source "segment-baseline-s1-1.0" {
                variables {
                    <- items
                }
                group line reset-point lineHead {
                    lineHead := internal mh.nop { }
                    lineTail := internal mh.nop { }
                }
                prepare := internal mh.nop { }
                fanout := internal mh.nop {
                    parallel {
                        branchA := internal mh.nop { }
                        branchB := internal mh.nop { }
                    }
                }
                splitter := internal mh.batch-line-splitter {
                    <- items
                    meta number-of-lines-per-task = "1",
                         variable-for-splitting = "items",
                         output-is-dynamic = "true",
                         output-variable = "lineVal",
                         is-array = "false"
                    sequential {
                        graft line driver run-now
                    }
                }
                post := internal mh.nop {
                    tag terminal
                }
            }
            """, "L1\nL2\nL3");

    public static final Shape S2 = new Shape("S2", """
            source "segment-baseline-s2-1.0" {
                variables {
                    <- items
                }
                group leaf reset-point leafHead {
                    leafHead := internal mh.nop { }
                }
                group line reset-point lineHead {
                    lineHead := internal mh.nop { }
                    inner := internal mh.batch-line-splitter {
                        <- items
                        meta number-of-lines-per-task = "1",
                             variable-for-splitting = "items",
                             output-is-dynamic = "true",
                             output-variable = "innerVal",
                             is-array = "false"
                        sequential {
                            graft leaf driver run-now
                        }
                    }
                }
                outer := internal mh.batch-line-splitter {
                    <- items
                    meta number-of-lines-per-task = "1",
                         variable-for-splitting = "items",
                         output-is-dynamic = "true",
                         output-variable = "lineVal",
                         is-array = "false"
                    sequential {
                        graft line driver run-now
                    }
                }
                post := internal mh.nop {
                    tag terminal
                }
            }
            """, "L1\nL2\nL3");

    public static final Shape S6 = new Shape("S6", """
            source "segment-baseline-s6-1.0" {
                variables {
                    <- items
                }
                group l3 reset-point l3Head {
                    l3Head := internal mh.nop { }
                }
                group l2 reset-point l2Head {
                    l2Head := internal mh.nop { }
                    split3 := internal mh.batch-line-splitter {
                        <- items
                        meta number-of-lines-per-task = "1",
                             variable-for-splitting = "items",
                             output-is-dynamic = "true",
                             output-variable = "v3",
                             is-array = "false"
                        sequential {
                            graft l3 driver run-now
                        }
                    }
                }
                group l1 reset-point l1Head {
                    l1Head := internal mh.nop { }
                    split2 := internal mh.batch-line-splitter {
                        <- items
                        meta number-of-lines-per-task = "1",
                             variable-for-splitting = "items",
                             output-is-dynamic = "true",
                             output-variable = "v2",
                             is-array = "false"
                        sequential {
                            graft l2 driver run-now
                        }
                    }
                }
                split1 := internal mh.batch-line-splitter {
                    <- items
                    meta number-of-lines-per-task = "1",
                         variable-for-splitting = "items",
                         output-is-dynamic = "true",
                         output-variable = "v1",
                         is-array = "false"
                    sequential {
                        graft l1 driver run-now
                    }
                }
                post := internal mh.nop {
                    tag terminal
                }
            }
            """, "L1\nL2");

    public static final Shape S5 = new Shape("S5", """
            source "segment-baseline-s5-1.0" {
                group g reset-point gHead {
                    gHead := internal mh.nop { }
                    gTail := internal mh.nop { }
                }
                wrapper := internal mh.nop {
                    sequential {
                        a := internal mh.nop { }
                        graft g driver run-now
                    }
                }
                post := internal mh.nop {
                    tag terminal
                }
            }
            """, null);

    public static final Shape S1_PLACE_NOW = new Shape("S1-place-now", """
            source "segment-baseline-s1-place-now-1.0" {
                variables {
                    <- items
                }
                group line reset-point lineHead {
                    lineHead := internal mh.nop { }
                    lineTail := internal mh.nop { }
                }
                prepare := internal mh.nop { }
                fanout := internal mh.nop {
                    parallel {
                        branchA := internal mh.nop { }
                        branchB := internal mh.nop { }
                    }
                }
                splitter := internal mh.batch-line-splitter {
                    <- items
                    meta number-of-lines-per-task = "1",
                         variable-for-splitting = "items",
                         output-is-dynamic = "true",
                         output-variable = "lineVal",
                         is-array = "false"
                    sequential {
                        graft line driver place-now
                    }
                }
                post := internal mh.nop {
                    tag terminal
                }
            }
            """, "L1\nL2\nL3");

    public static final Shape K1 = new Shape("K1", """
            source "segment-baseline-k1-1.0" {
                group bad reset-point boom {
                    boom := internal mh.evaluation {
                        meta expression = "undeclared = 'x'"
                    }
                    afterBoom := internal mh.nop { }
                }
                group good reset-point good1 {
                    good1 := internal mh.nop { }
                    good2 := internal mh.nop { }
                }
                wrapper := internal mh.nop {
                    sequential {
                        graft bad driver run-now
                        graft good driver run-now
                    }
                }
                join := internal mh.nop { }
                post := internal mh.nop {
                    tag terminal
                }
            }
            """, null);

    public static final Shape K2 = new Shape("K2", """
            source "segment-baseline-k2-1.0" {
                group bad reset-point boom {
                    boom := internal mh.evaluation {
                        meta expression = "undeclared = 'x'"
                    }
                    afterBoom := internal mh.nop { }
                }
                wrapper := internal mh.nop {
                    sequential {
                        graft bad driver run-now
                        graft bad driver run-now
                    }
                }
                join := internal mh.nop { }
                afterJoin := internal mh.nop { }
                post := internal mh.nop {
                    tag terminal
                }
            }
            """, null);

    public static final Shape K3 = new Shape("K3", """
            source "segment-baseline-k3-1.0" {
                boom := internal mh.evaluation {
                    meta expression = "undeclared = 'x'"
                }
                join := internal mh.nop {
                    tag terminal
                }
                afterJoin := internal mh.nop { }
            }
            """, null);

    public static final Shape K4 = new Shape("K4", """
            source "segment-baseline-k4-1.0" {
                fork := internal mh.nop {
                    parallel {
                        boomA := internal mh.evaluation {
                            meta expression = "undeclared = 'x'"
                        }
                        boomB := internal mh.evaluation {
                            meta expression = "undeclared = 'x'"
                        }
                    }
                }
                join := internal mh.nop { }
                afterJoin := internal mh.nop { }
                post := internal mh.nop {
                    tag terminal
                }
            }
            """, null);

    public static final Shape K5 = new Shape("K5", """
            source "segment-baseline-k5-1.0" {
                boom := internal mh.evaluation {
                    meta expression = "undeclared = 'x'"
                }
                join := internal mh.nop { }
                afterJoin := internal mh.nop { }
                post := internal mh.nop {
                    tag terminal
                }
            }
            """, null);

    public static final Shape R1 = new Shape("R1", """
            source "segment-baseline-r1-1.0" {
                variables {
                    <- items
                }
                group line reset-point lineHead {
                    lineHead := internal mh.nop { }
                    lineMid := internal mh.nop { }
                    lineTail := internal mh.nop { }
                }
                prepare := internal mh.nop { }
                splitter := internal mh.batch-line-splitter {
                    <- items
                    meta number-of-lines-per-task = "1",
                         variable-for-splitting = "items",
                         output-is-dynamic = "true",
                         output-variable = "lineVal",
                         is-array = "false"
                    sequential {
                        graft line driver run-now
                    }
                }
                post := internal mh.nop {
                    tag terminal
                }
            }
            """, "L1\nL2");

    // =================================================================================================================
    // DOT half
    // =================================================================================================================

    /**
     * @param id              the shape id used in the plan
     * @param dot             the graph as DOT
     * @param segmentLevels   the ctx levels (processContextId, no ancestors, no '#') of lines that start a segment
     */
    public record DotShape(String id, String dot, Set<String> segmentLevels) {
        public Predicate<SegmentData.Line> startsSegment() {
            return line -> segmentLevels.contains(ContextUtils.getProcessContextId(ContextUtils.getLevel(line.ctx())));
        }
    }

    @SneakyThrows
    private static String dotResource(String name) {
        return IOUtils.resourceToString("/segment/dot/" + name, StandardCharsets.UTF_8);
    }

    private static final String SPLITTER_0 = "1,6";
    private static final String SPLITTER_1 = "1,6,7,10,11,12,13,22";
    private static final String MANUAL = "1,17";

    /** RG manual genesis, 26 Tasks - ExecContext 325. */
    public static final DotShape DOT_S1 = new DotShape("S1", dotResource("S1-ec325.dot"), Set.of(SPLITTER_0));

    /** RG decomposed genesis, 52 Tasks, 11 forks resolving to one join - ExecContext 328. */
    public static final DotShape DOT_S2 = new DotShape("S2", dotResource("S2-ec328.dot"), Set.of(SPLITTER_0, SPLITTER_1));

    /** S2 plus four manual grafts {@code 1,17#1…4}, a clone (Task ids out of chain order) - ExecContext 334. */
    public static final DotShape DOT_S3 = new DotShape("S3", dotResource("S3-ec334.dot"), Set.of(SPLITTER_0, SPLITTER_1, MANUAL));

    /** A producer (#2) with zero lines: only fork -> continuation. */
    public static final DotShape DOT_S4 = new DotShape("S4", """
            strict digraph G {
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" tag="terminal" ];
              4 [ ctxid="1" ];
              1 -> 2;
              2 -> 3;
              3 -> 4;
            }
            """, Set.of("1,2"));

    /**
     * F1: a graft (line {@code 1,3,2|0#1}) under the last Task (#20) of a static line ({@code 1,3#0}); its join is the
     * enclosing join #11.
     */
    public static final DotShape DOT_S5 = new DotShape("S5", """
            strict digraph G {
              10 [ ctxid="1" ];
              11 [ ctxid="1" tag="terminal" ];
              12 [ ctxid="1" ];
              20 [ ctxid="1,3#0" ];
              30 [ ctxid="1,3,2|0#1" ];
              31 [ ctxid="1,3,2|0#1" ];
              10 -> 11;
              11 -> 12;
              10 -> 20;
              20 -> 11;
              20 -> 30;
              30 -> 31;
              31 -> 11;
            }
            """, Set.of("1,3,2"));

    /**
     * Three nested splitters: every splitter is the last Task of its line, so the innermost tails (#30, #31) skip two
     * levels to the top-level join #2. The second level-1 line ({@code 1,5#2}) has a splitter with no lines.
     */
    public static final DotShape DOT_S6 = new DotShape("S6", """
            strict digraph G {
              1 [ ctxid="1" ];
              2 [ ctxid="1" tag="terminal" ];
              3 [ ctxid="1" ];
              10 [ ctxid="1,5#1" ];
              11 [ ctxid="1,5#1" ];
              12 [ ctxid="1,5#2" ];
              13 [ ctxid="1,5#2" ];
              20 [ ctxid="1,5,3|1#1" ];
              21 [ ctxid="1,5,3|1#1" ];
              30 [ ctxid="1,5,3,2|1|1#1" ];
              31 [ ctxid="1,5,3,2|1|1#2" ];
              1 -> 2;
              2 -> 3;
              1 -> 10;
              10 -> 11;
              11 -> 2;
              1 -> 12;
              12 -> 13;
              13 -> 2;
              11 -> 20;
              20 -> 21;
              21 -> 2;
              21 -> 30;
              30 -> 2;
              21 -> 31;
              31 -> 2;
            }
            """, Set.of("1,5", "1,5,3", "1,5,3,2"));

    /** One fork (#1) with {@code lines} lines {@code 1,2#i} of two Tasks each, joining #2 ({@code tag terminal}). */
    public static DotShape dotS7(int lines) {
        final StringBuilder sb = new StringBuilder("strict digraph G {\n");
        sb.append("  1 [ ctxid=\"1\" ];\n  2 [ ctxid=\"1\" tag=\"terminal\" ];\n  3 [ ctxid=\"1\" ];\n");
        for (int i = 1; i <= lines; i++) {
            sb.append("  ").append(1000 + 2L * i).append(" [ ctxid=\"1,2#").append(i).append("\" ];\n");
            sb.append("  ").append(1001 + 2L * i).append(" [ ctxid=\"1,2#").append(i).append("\" ];\n");
        }
        sb.append("  1 -> 2;\n  2 -> 3;\n");
        for (int i = 1; i <= lines; i++) {
            final long head = 1000 + 2L * i;
            sb.append("  1 -> ").append(head).append(";\n");
            sb.append("  ").append(head).append(" -> ").append(head + 1).append(";\n");
            sb.append("  ").append(head + 1).append(" -> 2;\n");
        }
        return new DotShape("S7", sb.append("}\n").toString(), Set.of("1,2"));
    }

    public static final DotShape DOT_S7 = dotS7(1000);

    /** Two line bases under one fork - S3's splitter fork carries {@code 1,6#1} and {@code 1,17#1…4}. */
    public static final DotShape DOT_S8 = new DotShape("S8", DOT_S3.dot(), DOT_S3.segmentLevels());

    /** Invalid: an edge between two lines of the same fork (line #1's tail into line #2's head). */
    public static final DotShape DOT_X1 = new DotShape("X1", """
            strict digraph G {
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" ];
              10 [ ctxid="1,2#1" ];
              11 [ ctxid="1,2#1" ];
              20 [ ctxid="1,2#2" ];
              21 [ ctxid="1,2#2" ];
              1 -> 2;
              2 -> 3;
              1 -> 10;
              10 -> 11;
              11 -> 2;
              1 -> 20;
              20 -> 21;
              21 -> 2;
              11 -> 20;
            }
            """, Set.of("1,2"));

    /** Invalid: a line tail (#10) wired into #3, which is not its fork's join (#2). */
    public static final DotShape DOT_X2 = new DotShape("X2", """
            strict digraph G {
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" ];
              4 [ ctxid="1" ];
              10 [ ctxid="1,2#1" ];
              1 -> 2;
              2 -> 3;
              3 -> 4;
              1 -> 10;
              10 -> 3;
            }
            """, Set.of("1,2"));

    /**
     * Invalid: one join (#2) fed by the tails of two different forks - fork #1 (line {@code 1,2#1}, join #2) and fork #10
     * (line {@code 1,2,3|1#1}, whose derived join is #11, the Task after #10 in its line).
     */
    public static final DotShape DOT_X3 = new DotShape("X3", """
            strict digraph G {
              1 [ ctxid="1" ];
              2 [ ctxid="1" ];
              3 [ ctxid="1" ];
              10 [ ctxid="1,2#1" ];
              11 [ ctxid="1,2#1" ];
              20 [ ctxid="1,2,3|1#1" ];
              1 -> 2;
              2 -> 3;
              1 -> 10;
              10 -> 11;
              11 -> 2;
              10 -> 20;
              20 -> 2;
            }
            """, Set.of("1,2", "1,2,3"));
}
