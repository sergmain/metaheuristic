/*
 * Metaheuristic, Copyright (C) 2017-2020  Serge Maslyukov
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

package ai.metaheuristic.ai.dispatcher.source_code;

import ai.metaheuristic.ai.Consts;
import ai.metaheuristic.ai.dispatcher.DispatcherContext;
import ai.metaheuristic.ai.dispatcher.beans.Company;
import ai.metaheuristic.ai.dispatcher.beans.SourceCodeImpl;
import ai.metaheuristic.ai.dispatcher.company.CompanyCache;
import ai.metaheuristic.ai.dispatcher.data.SourceCodeData;
import ai.metaheuristic.ai.dispatcher.repositories.SourceCodeRepository;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYaml;
import ai.metaheuristic.ai.yaml.company.CompanyParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeParamsYamlUtils;
import ai.metaheuristic.ai.yaml.source_code.SourceCodeStoredParamsYamlUtils;
import ai.metaheuristic.api.data.source_code.SourceCodeParamsYaml;
import ai.metaheuristic.api.data.source_code.SourceCodeStoredParamsYaml;
import ai.metaheuristic.api.dispatcher.SourceCode;
import ai.metaheuristic.commons.S;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Serge
 * Date: 2/23/2020
 * Time: 11:33 PM
 */
@Service
@Slf4j
@Profile("dispatcher")
@RequiredArgsConstructor
public class SourceCodeSelectorService {

    private final SourceCodeCache sourceCodeCache;
    private final SourceCodeRepository sourceCodeRepository;
    private final CompanyCache companyCache;

    public SourceCodeData.SourceCodesForCompany getAvailableSourceCodesForCompany(DispatcherContext context) {
        return getAvailableSourceCodesForCompany(context.getCompanyId());
    }

    public SourceCodeData.SourceCodesForCompany getSourceCodeById(Long sourceCodeId, Long companyId) {
        return getSourceCodeInternal(companyId, (o) -> o.getId().equals(sourceCodeId));
    }

    public SourceCodeData.SourceCodesForCompany getSourceCodeByUid(String sourceCodeUid, Long companyId) {
        return getSourceCodeInternal(companyId, (o) -> o.getUid().equals(sourceCodeUid));
    }

    private SourceCodeData.SourceCodesForCompany getSourceCodeInternal(Long companyId, final Function<SourceCode, Boolean> sourceCodeFilter) {
        SourceCodeData.SourceCodesForCompany availableSourceCodesForCompany = getAvailableSourceCodesForCompany(companyId, sourceCodeFilter);
        if (availableSourceCodesForCompany.items.size()>1) {
            log.error("#984.020 !!!!!!!!!!!!!!!! error in code -  (availableSourceCodesForCompany.items.size()>1) !!!!!!!!!!!!!!!!!!!!!!!!!");
        }
        return availableSourceCodesForCompany;
    }

    public SourceCodeData.SourceCodesForCompany getAvailableSourceCodesForCompany(Long companyId) {
        return getAvailableSourceCodesForCompany(companyId, (f) -> true);
    }

    private SourceCodeData.SourceCodesForCompany getAvailableSourceCodesForCompany(Long companyUniqueId, final Function<SourceCode, Boolean> sourceCodeFilter) {
        final SourceCodeData.SourceCodesForCompany sourceCodesForCompany = new SourceCodeData.SourceCodesForCompany();
        sourceCodesForCompany.items = sourceCodeRepository.findAllAsSourceCode(companyUniqueId).stream().filter(sourceCodeFilter::apply).filter(o->{
            if (!o.isValid()) {
                return false;
            }
            try {
                SourceCodeStoredParamsYaml scspy = SourceCodeStoredParamsYamlUtils.BASE_YAML_UTILS.to(o.getParams());
                return !scspy.internalParams.archived;
            } catch (YAMLException e) {
                final String es = "#984.040 Can't parse SourceCode params. It's broken or unknown version. SourceCode id: #" + o.getId();
                sourceCodesForCompany.addErrorMessage(es);
                log.error(es);
                log.error("#984.060 Params:\n{}", o.getParams());
                log.error("#984.080 Error: {}", e.toString());
                return false;
            }
        }).collect(Collectors.toList());

        Company company = companyCache.findByUniqueId(companyUniqueId);
        if (company!=null && !S.b(company.getParams())) {
            final Set<String> groups = new HashSet<>();
            try {
                CompanyParamsYaml cpy = CompanyParamsYamlUtils.BASE_YAML_UTILS.to(company.getParams());
                if (cpy.ac!=null && !S.b(cpy.ac.groups)) {
                    String[] arr = StringUtils.split(cpy.ac.groups, ',');
                    Stream.of(arr).forEach(s-> groups.add(s.strip()));
                }
            } catch (YAMLException e) {
                final String es = "#984.100 Can't parse Company params. It's broken or version is unknown. Company companyUniqueId: #" + companyUniqueId;
                sourceCodesForCompany.addErrorMessage(es);
                log.error(es);
                log.error("#984.120 Params:\n{}", company.getParams());
                log.error("#984.140 Error: {}", e.toString());
                return sourceCodesForCompany;
            }

            if (!groups.isEmpty()) {
                List<SourceCodeImpl> commonSourceCodes = sourceCodeRepository.findAllAsSourceCode(Consts.ID_1).stream().filter(sourceCodeFilter::apply).filter(o -> {
                    if (!o.isValid()) {
                        return false;
                    }
                    try {
                        SourceCodeImpl sc = sourceCodeCache.findById(o.id);
                        if (sc==null) {
                            return false;
                        }
                        SourceCodeStoredParamsYaml scspy = sc.getSourceCodeStoredParamsYaml();
                        SourceCodeParamsYaml ppy = SourceCodeParamsYamlUtils.BASE_YAML_UTILS.to(scspy.source);
                        if (ppy.source.ac!=null) {
                            String[] arr = StringUtils.split(ppy.source.ac.groups, ',');
                            return Stream.of(arr).map(String::strip).anyMatch(groups::contains);
                        }
                        return false;
                    } catch (YAMLException e) {
                        final String es = "#984.160 Can't parse SourceCode params. It's broken or unknown version. SourceCode id: #" + o.getId();
                        sourceCodesForCompany.addErrorMessage(es);
                        log.error(es);
                        log.error("#984.180 Params:\n{}", o.getParams());
                        log.error("#984.200 Error: {}", e.toString());
                        return false;
                    }
                }).collect(Collectors.toList());
                sourceCodesForCompany.items.addAll(commonSourceCodes);
            }
        }
        sourceCodesForCompany.items.sort((o1, o2) -> Long.compare(o2.getId(), o1.getId()));

        return sourceCodesForCompany;
    }

}
