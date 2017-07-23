package aiai.ai.launchpad.dataset.repo;

import aiai.ai.launchpad.dataset.DatasetGroup;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User: Serg
 * Date: 20.07.2017
 * Time: 23:23
 */
@Component
@Transactional
public interface DatasetGroupsRepository extends CrudRepository<DatasetGroup, Long> {

    @Transactional(readOnly = true)
    List<DatasetGroup> findByDataset_Id(Long datasetId);
}