/*
 AiAi, Copyright (C) 2017 - 2018, Serge Maslyukov

 This program is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <https://www.gnu.org/licenses/>.

 */
package aiai.ai.launchpad.dataset;

import aiai.ai.launchpad.beans.Dataset;
import aiai.ai.launchpad.beans.DatasetGroup;
import aiai.ai.launchpad.repositories.DatasetGroupsRepository;
import aiai.ai.launchpad.repositories.DatasetRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DatasetCache {

    private final DatasetRepository datasetRepository;
    private final DatasetGroupsRepository groupsRepository;

    public DatasetCache(DatasetRepository datasetRepository, DatasetGroupsRepository groupsRepository) {
        this.datasetRepository = datasetRepository;
        this.groupsRepository = groupsRepository;
    }

    @CachePut(cacheNames = "datasets", key = "#dataset.id")
    public Dataset save(Dataset dataset) {
        return datasetRepository.save(dataset);
    }

    @CacheEvict(cacheNames = "datasets", key = "#dataset.id")
    public void delete(Dataset dataset) {
        datasetRepository.delete(dataset);
    }

    @CacheEvict(cacheNames = "datasets", key = "#datasetId")
    public void delete(long datasetId) {
        datasetRepository.deleteById(datasetId);
    }

    @CacheEvict(cacheNames = "datasets", key = "#group.dataset.id")
    public void saveGroup(DatasetGroup group) {
        groupsRepository.save(group);
    }

    @Cacheable(cacheNames = "datasets", unless="#result==null")
    public Dataset findById(long id) {
        return datasetRepository.findById(id).orElse(null);
    }

    @CacheEvict(cacheNames = "datasets", key = "#datasetId")
    public void saveAllGroups(List<DatasetGroup> groups, long datasetId) {
        groupsRepository.saveAll(groups);
    }

    @CacheEvict(cacheNames = "datasets", key = "#group.dataset.id")
    public void delete(DatasetGroup group) {
        groupsRepository.delete(group);
    }
}
