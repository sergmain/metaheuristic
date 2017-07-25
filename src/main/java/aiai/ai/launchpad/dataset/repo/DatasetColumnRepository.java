package aiai.ai.launchpad.dataset.repo;

import aiai.ai.launchpad.dataset.DatasetColumn;
import aiai.ai.launchpad.dataset.DatasetGroup;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User: Serg
 * Date: 23.07.2017
 * Time: 12:36
 */
@Component
@Transactional
public interface DatasetColumnRepository extends CrudRepository<DatasetColumn, Long> {

    void deleteByDatasetGroup_Id(Long datasetId);
}