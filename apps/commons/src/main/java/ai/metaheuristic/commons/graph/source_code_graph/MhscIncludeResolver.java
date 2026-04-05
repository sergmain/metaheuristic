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

package ai.metaheuristic.commons.graph.source_code_graph;

import ai.metaheuristic.commons.CommonConsts;
import ai.metaheuristic.commons.exceptions.BundleProcessingException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Resolves {@code include "name"} directives in .mhsc files by textually substituting
 * the content of the referenced .mhscp (part) file.
 *
 * <p>Resolution happens before ANTLR parsing — the result is a self-contained .mhsc string
 * with all includes expanded. This is the string that gets stored in the database.</p>
 *
 * @author Serge
 * Date: 4/2026
 */
public class MhscIncludeResolver {

    // Matches: include "name"  (with optional leading whitespace)
    private static final Pattern INCLUDE_PATTERN =
            Pattern.compile("^([ \\t]*)include\\s+\"([^\"]+)\"\\s*$", Pattern.MULTILINE);

    private static final int MAX_INCLUDE_DEPTH = 10;

    /**
     * Resolve all {@code include "name"} directives in the given .mhsc content.
     * Part files are looked up in {@code sourceDir} with the {@code .mhscp} extension.
     *
     * @param content    the raw .mhsc content
     * @param sourceDir  directory containing .mhscp part files
     * @return the content with all includes resolved
     * @throws IOException if a part file cannot be read
     */
    public static String resolve(String content, Path sourceDir) throws IOException {
        return resolve(content, name -> {
            Path partFile = sourceDir.resolve(name + CommonConsts.MHSCP_EXT);
            if (Files.notExists(partFile)) {
                throw new BundleProcessingException("564.500 Include file not found: " + name + CommonConsts.MHSCP_EXT + " in " + sourceDir);
            }
            try {
                return Files.readString(partFile);
            } catch (IOException e) {
                throw new BundleProcessingException("564.520 Error reading include file: " + partFile + ", error: " + e.getMessage());
            }
        });
    }

    /**
     * Resolve all {@code include "name"} directives using a content resolver function.
     * This overload is useful for testing (no filesystem dependency).
     *
     * @param content         the raw .mhsc content
     * @param contentResolver function that maps an include name to its text content
     * @return the content with all includes resolved
     */
    public static String resolve(String content, Function<String, String> contentResolver) {
        return resolveRecursive(content, contentResolver, new HashSet<>(), 0);
    }

    private static String resolveRecursive(String content, Function<String, String> contentResolver,
                                           Set<String> visited, int depth) {
        if (depth > MAX_INCLUDE_DEPTH) {
            throw new BundleProcessingException("564.540 Include depth exceeded maximum of " + MAX_INCLUDE_DEPTH + ". Circular include?");
        }

        Matcher m = INCLUDE_PATTERN.matcher(content);
        if (!m.find()) {
            return content;
        }

        StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find()) {
            String indent = m.group(1);
            String name = m.group(2);

            if (visited.contains(name)) {
                throw new BundleProcessingException("564.560 Circular include detected: " + name);
            }

            String partContent = contentResolver.apply(name);

            Set<String> newVisited = new HashSet<>(visited);
            newVisited.add(name);

            // Recursively resolve includes in the part content
            String resolved = resolveRecursive(partContent, contentResolver, newVisited, depth + 1);

            // Indent the included content to match the include directive's indentation
            String indented = resolved.lines()
                    .map(line -> line.isEmpty() ? line : indent + line)
                    .collect(Collectors.joining("\n"));

            m.appendReplacement(sb, Matcher.quoteReplacement(indented));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Check if the given content contains any {@code include} directives.
     */
    public static boolean hasIncludes(String content) {
        return INCLUDE_PATTERN.matcher(content).find();
    }
}
