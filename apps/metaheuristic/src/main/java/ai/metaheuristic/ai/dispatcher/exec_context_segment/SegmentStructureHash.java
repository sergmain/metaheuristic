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

import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Canonical digest of an ExecContext's structure (041-EXEC-CONTEXT-SEGMENTS-PLAN, Phase 5, decision 12).
 *
 * <p>Each segment has a {@code STRUCTURE_HASH}: SHA-256, lowercase hex, over the canonical text of its structure - for
 * every line, in ctx order: the ctx, the fork, and the Tasks in chain order with their tags. Task state and variable-state
 * entries are not an input, so changing them never changes a hash.
 *
 * <p>The ExecContext's root is SHA-256 over {@code lineCtxId;structureHash} of every segment, in lineCtxId order: it is
 * computed from the stored hashes alone - sealing reads 64 characters per segment, never the params - and is independent
 * of the order segments are stored or read in. The edges are not hashed because every edge (within a chain, fork ->
 * head, tail -> derived join) is a function of the lines, so the root attests them.
 *
 * <p>Separators: a ctx holds digits, {@code ,}, {@code |} and {@code #}; a tag holds {@code [a-zA-Z0-9_.-]}; so {@code ;}
 * and a newline cannot occur in either and the canonical text is unambiguous.
 *
 * <p>Error code prefix: {@code 01.911.} (unique to this class).
 */
public final class SegmentStructureHash {

    private SegmentStructureHash() {
    }

    /** The algorithm id recorded with a root; a different canonical form must get a different id. */
    public static final String ALGO = "mh-segment-root-sha256-v1";

    /** A stored segment hash, as sealing reads it. */
    public record SegmentHash(String lineCtxId, String structureHash) {
    }

    /** The canonical text of a segment's structure. */
    public static String canonical(SegmentData.Segment segment) {
        final StringBuilder sb = new StringBuilder();
        sb.append("S;").append(segment.lineCtxId()).append(';').append(fork(segment.forkTaskId())).append('\n');
        final List<SegmentData.Line> lines = new ArrayList<>(segment.lines());
        lines.sort(Comparator.comparing(SegmentData.Line::ctx));
        for (SegmentData.Line line : lines) {
            sb.append("L;").append(line.ctx()).append(';').append(fork(line.forkTaskId())).append('\n');
            for (SegmentData.Vertex v : line.tasks()) {
                sb.append("T;").append(v.taskId()).append(';').append(v.tag() == null ? "" : v.tag()).append('\n');
            }
        }
        return sb.toString();
    }

    public static String structureHash(SegmentData.Segment segment) {
        return sha256(canonical(segment));
    }

    /** The root over stored segment hashes, in lineCtxId order. */
    public static String root(Collection<SegmentHash> hashes) {
        final List<SegmentHash> sorted = new ArrayList<>(hashes);
        sorted.sort(Comparator.comparing(SegmentHash::lineCtxId));
        final StringBuilder sb = new StringBuilder();
        String prev = null;
        for (SegmentHash h : sorted) {
            if (h.lineCtxId().equals(prev)) {
                throw new IllegalStateException("01.911.020 two segments share lineCtxId " + prev);
            }
            prev = h.lineCtxId();
            sb.append(h.lineCtxId()).append(';').append(h.structureHash()).append('\n');
        }
        return sha256(sb.toString());
    }

    /** The root of segments whose hashes are computed from their structure. */
    public static String rootOf(Collection<SegmentData.Segment> segments) {
        return root(segments.stream().map(s -> new SegmentHash(s.lineCtxId(), structureHash(s))).toList());
    }

    /**
     * The lineCtxIds whose stored hash differs from the hash recomputed from content, plus any present on one side only;
     * sorted. Verification recomputes every segment's hash and then the root, so a changed segment and a changed stored
     * hash are both caught, and this names the segment.
     */
    public static List<String> divergent(Collection<SegmentHash> stored, Collection<SegmentHash> recomputed) {
        final Map<String, String> s = new TreeMap<>();
        stored.forEach(h -> s.put(h.lineCtxId(), h.structureHash()));
        final Map<String, String> r = new TreeMap<>();
        recomputed.forEach(h -> r.put(h.lineCtxId(), h.structureHash()));
        final Set<String> all = new TreeSet<>(s.keySet());
        all.addAll(r.keySet());
        return all.stream().filter(ctx -> !Objects.equals(s.get(ctx), r.get(ctx))).toList();
    }

    private static String fork(@Nullable Long forkTaskId) {
        return forkTaskId == null ? "-" : forkTaskId.toString();
    }

    private static String sha256(String text) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("01.911.040 SHA-256 is not available", e);
        }
    }
}
