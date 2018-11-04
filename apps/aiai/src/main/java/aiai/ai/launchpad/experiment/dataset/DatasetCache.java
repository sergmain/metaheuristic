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
package aiai.ai.launchpad.experiment.dataset;

import aiai.ai.launchpad.beans.Dataset;
import aiai.ai.launchpad.beans.Feature;
import aiai.ai.launchpad.repositories.FeatureRepository;
import aiai.ai.launchpad.repositories.DatasetRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Profile("launchpad")
public class DatasetCache {

    private final DatasetRepository datasetRepository;
    private final FeatureRepository featureRepository;

    public DatasetCache(DatasetRepository datasetRepository, FeatureRepository featureRepository) {
        this.datasetRepository = datasetRepository;
        this.featureRepository = featureRepository;
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

    @CacheEvict(cacheNames = "datasets", key = "#feature.dataset.id")
    public void saveGroup(Feature feature) {
        featureRepository.save(feature);
    }

    @Cacheable(cacheNames = "datasets", unless="#result==null")
    public Dataset findById(long id) {
        return datasetRepository.findById(id).orElse(null);
    }

    @CacheEvict(cacheNames = "datasets", key = "#datasetId")
    public void saveAllFeatures(List<Feature> features, long datasetId) {
        featureRepository.saveAll(features);
    }

    @CacheEvict(cacheNames = "datasets", key = "#feature.dataset.id")
    public void delete(Feature feature) {
        featureRepository.delete(feature);
    }
}
