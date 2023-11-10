package ai.metaheuristic.ai.dispatcher.lucene;

import org.apache.lucene.store.Directory;

/**
 * User: SMaslyukov
 * Date: 01.06.2007
 * Time: 17:13:04
 */
public interface DirectorySearch {
    public Directory getDirectory(Long siteId);
}
