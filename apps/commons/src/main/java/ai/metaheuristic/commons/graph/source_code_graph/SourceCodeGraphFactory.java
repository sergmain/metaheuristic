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

import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SourceCodeGraph;

import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * @author Serge
 * Date: 2/14/2020
 * Time: 10:50 PM
 */
public class SourceCodeGraphFactory {

    private final static SourceCodeGraphLanguageYaml YAML_LANG = new SourceCodeGraphLanguageYaml();
    private final static SourceCodeGraphLanguageMhsc MHSC_LANG = new SourceCodeGraphLanguageMhsc();

    private record SourceCodeKey(EnumsApi.SourceCodeLang lang, String sourceCode) {}

    private final static LinkedHashMap<SourceCodeKey, SourceCodeGraph> CACHE = new LinkedHashMap<>();

    private final static ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final static ReentrantReadWriteLock.ReadLock readLock = lock.readLock();
    private final static ReentrantReadWriteLock.WriteLock writeLock = lock.writeLock();

    public static SourceCodeGraph parse(EnumsApi.SourceCodeLang lang, String sourceCode) {
        SourceCodeKey key = new SourceCodeKey(lang, sourceCode);
        try {
            readLock.lock();
            SourceCodeGraph graph = CACHE.get(key);
            if (graph!=null) {
                return graph;
            }
        } finally {
            readLock.unlock();
        }

        try {
            writeLock.lock();
            AtomicLong contextId = new AtomicLong();
            Supplier<String> contextIdSupplier = () -> String.valueOf(contextId.incrementAndGet());
            SourceCodeGraph graph = switch (lang) {
                case yaml -> YAML_LANG.parse(sourceCode, contextIdSupplier);
                case mhsc -> MHSC_LANG.parse(sourceCode, contextIdSupplier);
                default -> throw new IllegalStateException("Unknown language dialect: " + lang);
            };
            CACHE.put(key, graph);
            return graph;
        } finally {
            writeLock.unlock();
        }
    }
}
