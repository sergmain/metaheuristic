package ai.metaheuristic.ai.dispatcher.lucene;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/**
 * User: SMaslyukov
 * Date: 01.06.2007
 * Time: 17:13:40
 */
@Slf4j
public class DirectorySearchImpl implements DirectorySearch {

    public static final String DIR = "/lucene/index";

    private String luceneDirectoryPath = DIR;

    public String getLuceneDirectoryPath() {
        return luceneDirectoryPath;
    }

    public void setLuceneDirectoryPath(String luceneDirectoryPath) {
        this.luceneDirectoryPath = luceneDirectoryPath;
    }

    public Directory getDirectory(Long siteId) {
        return initDirectory();
    }

    @SneakyThrows
    private synchronized Directory initDirectory() {
        Path path = Path.of(getLuceneDirectoryPath());
        boolean isNew = false;
        if (Files.notExists(path)) {
            isNew = true;
            Files.createDirectories(path);
        }
        else if (!Files.isDirectory(path)) {
            String es = path.toAbsolutePath() + " is not directory.";
            log.error(es);
            throw new PortalSearchException(es);
        }
        if (!Files.isWritable(path)) {
            String es = path.toAbsolutePath() + " is not writable.";
            log.error(es);
            throw new PortalSearchException(es);
        }
//        boolean isSegmentPresent = checkSegmentFiles(path);
        boolean isSegmentPresent = false;
        try {
            Directory directory = new NIOFSDirectory(path);
            if (isNew || !isSegmentPresent) {
                CharArraySet stopWordsOverride = new CharArraySet(Collections.emptySet(), true);
                Analyzer analyzer = new StandardAnalyzer(stopWordsOverride);
                IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
                IndexWriter writer = new IndexWriter(directory, indexWriterConfig);
                writer.close();
            }
            return directory;
        }
        catch (IOException e) {
            String es = "Error get FSDirectory for path " + path.getFileName();
            log.error(es, e);
            throw new PortalSearchException(es, e);
        }
    }

    static boolean checkSegmentFiles(File path) {
        File[] files = path.listFiles(
            new FileFilter() {
                public boolean accept(File pathname) {
                    return pathname.isFile() && pathname.getName().startsWith("segments");
                }
            }
        );
        return files!=null && files.length>0;
    }
}
