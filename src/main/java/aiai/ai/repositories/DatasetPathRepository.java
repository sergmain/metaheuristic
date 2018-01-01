package aiai.ai.repositories;

import aiai.ai.launchpad.dataset.Dataset;
import aiai.ai.launchpad.dataset.DatasetPath;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * User: Serg
 * Date: 03.08.2017
 * Time: 21:00
 */
@Component
@Transactional
public interface DatasetPathRepository extends CrudRepository<DatasetPath, Long> {

//    @Transactional(readOnly = true)
//    List<DatasetPath> findByDataset_Id(Long datasetId);

    @Transactional(readOnly = true)
    List<DatasetPath> findByDataset(Dataset dataset);

    @Transactional(readOnly = true)
    List<DatasetPath> findByDataset_OrderByPathNumber(Dataset dataset);


    void deleteByDataset(Dataset dataset);
}