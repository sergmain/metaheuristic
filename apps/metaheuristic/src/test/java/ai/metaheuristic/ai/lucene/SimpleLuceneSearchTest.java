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

package ai.metaheuristic.ai.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Sergio Lissner
 * Date: 11/9/2023
 * Time: 8:32 PM
 */
public class SimpleLuceneSearchTest {

    // https://solr.apache.org/guide/6_6/morelikethis.html

    @Test
    public void givenTermQueryWhenFetchedDocumentThenCorrect() throws ParseException {
        Analyzer analyzer = new SimpleAnalyzer();
        MemoryIndex index = new MemoryIndex();
        index.addField("content", "Readings about Salmons and other select Alaska fishing Manuals", analyzer);
        index.addField("author", "Tales of James", analyzer);
        QueryParser parser = new QueryParser( "content", analyzer);
        final Query query = parser.parse("+author:james +salmon~ +fish* manual~");
        float score = index.search(query);
        if (score > 0.0f) {
            System.out.println("it's a match");
        } else {
            System.out.println("no match found");
        }
        System.out.println("indexData=" + index.toString());
    }

    @Test
    public void test_(@TempDir Path tempPath) throws IOException, ParseException {
        Path lucene = tempPath.resolve("lucene");
        if (Files.notExists(lucene)) {
            Files.createDirectory(lucene);
        }
        Directory directory = new NIOFSDirectory(lucene);
        StandardAnalyzer analyzer = new StandardAnalyzer();
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
        IndexWriter writter = new IndexWriter(directory, indexWriterConfig);
        Document document = new Document();

        document.add(new TextField("title", "Tiger", Field.Store.YES));
        document.add(new TextField("body", "Life of tigers", Field.Store.YES));

        writter.addDocument(document);
        writter.close();


        var l = searchIndex("body", "tiger", directory, analyzer);
        for (Document d : l) {
            System.out.println(d);
        }

        directory.close();
    }

    public static List<Document> searchIndex(String inField, String queryString, Directory memoryIndex, StandardAnalyzer analyzer) throws IOException, ParseException {
        Query query = new QueryParser(inField, analyzer).parse(queryString);

        IndexReader indexReader = DirectoryReader.open(memoryIndex);
        IndexSearcher searcher = new IndexSearcher(indexReader);
        TopDocs topDocs = searcher.search(query, 10);
        List<Document> documents = new ArrayList<>();
        for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
            documents.add(searcher.storedFields().document(scoreDoc.doc));
        }

        return documents;
    }
}
