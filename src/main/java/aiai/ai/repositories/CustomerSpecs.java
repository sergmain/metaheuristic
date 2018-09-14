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
package aiai.ai.repositories;

import aiai.ai.beans.ExperimentSequence;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.*;

import static org.springframework.data.jpa.domain.Specifications.where;

@Component
public class CustomerSpecs {
/*

    private final ExperimentHyperParamsRepository experimentHyperParamsRepository;

    public Specification<ExperimentSequence> getFilter(String[] params, long featureId, ) {
        return (root, query, cb) -> {
            query.distinct(true); //Important because of the join in the addressAttribute specifications

            CustomerSpecs.HelperSpecification spec1 = new CustomerSpecs.HelperSpecification(new CustomerSpecs.SearchCriteria("isCompleted", "==B", true));
            CustomerSpecs.HelperSpecification spec2 = new CustomerSpecs.HelperSpecification(new CustomerSpecs.SearchCriteria("featureId", "==L", featureId));


            return where(
                    where(firstNameContains(request.search))
                            .or(lastNameContains(request.search))
                            .or(emailContains(request.search))
            )
                    .and(spec1)
                    .and(spec2)
                    .toPredicate(root, query, cb);
        };
    }

    private Specification<ExperimentSequence> firstNameContains(String firstName) {
        return userAttributeContains("firstName", firstName);
    }

    private Specification<ExperimentSequence> lastNameContains(String lastName) {
        return userAttributeContains("lastName", lastName);
    }

    private Specification<ExperimentSequence> emailContains(String email) {
        return userAttributeContains("email", email);
    }

    private Specification<ExperimentSequence> userAttributeContains(String attribute, String value) {
        return (root, query, cb) -> {
            if(value == null) {
                return null;
            }

            return cb.like(
                    cb.lower(root.get(attribute)),
                    containsLowerCase(value)
            );
        };
    }

    private Specification<ExperimentSequence> hyperParams(String[] params) {
        return (root, query, cb) -> {
            Predicate p = cb.disjunction();

            for (String param : params) {
                if (StringUtils.isBlank(param)) {
                    continue;
                }

                String key = param.substring(0, param.indexOf('-') );
                int index = Integer.parseInt( param.substring(param.indexOf('-')+1) );

                p.getExpressions().add(cb.equal(root.get("key"), key));

            }
            return cb.like(
                    cb.lower(root.get(attribute)),
                    containsLowerCase(value)
            );
        };
    }

    private Specification<ExperimentSequence> cityContains(String city) {
        return addressAttributeContains("city", city);
    }

    private Specification<ExperimentSequence> streetContains(String street) {
        return addressAttributeContains("street", street);
    }

    private Specification<ExperimentSequence> addressAttributeContains(String attribute, String value) {
        return (root, query, cb) -> {
            if(value == null) {
                return null;
            }

            ListJoin<ExperimentSequence, Address> addresses = root.joinList("addresses", JoinType.INNER);

            return cb.like(
                    cb.lower(addresses.get(attribute)),
                    containsLowerCase(value)
            );
        };
    }

    public class ExperimentSequenceSpecification implements Specification<ExperimentSequence> {
        private String[] params;

        public void init(String[] params) {
            this.params = params;
        }

        public Predicate toPredicate(Root<ExperimentSequence> root, CriteriaQuery<?> cq, CriteriaBuilder cb) {
            Predicate p = cb.disjunction();

            for (String param : params) {
                if (StringUtils.isBlank(param)) {
                    continue;
                }

                String key = param.substring(0, param.indexOf('-') );
                int index = Integer.parseInt( param.substring(param.indexOf('-')+1) );

                p.getExpressions().add(cb.equal(root.get("key"), key));

            }
            return p;
        }
    }

    @Data
    @AllArgsConstructor
    public static class SearchCriteria {
        private String key;
        private String operation;
        private Object value;
    }

    public static class HelperSpecification implements Specification<ExperimentSequence> {

        private SearchCriteria criteria;

        public HelperSpecification(SearchCriteria criteria) {
            this.criteria = criteria;
        }

        @Override
        public Predicate toPredicate(Root<ExperimentSequence> root, CriteriaQuery<?> query, CriteriaBuilder builder) {

            if (criteria.getOperation().equalsIgnoreCase(">")) {
                return builder.greaterThanOrEqualTo(root.<String> get(criteria.getKey()), criteria.getValue().toString());
            }
            else if (criteria.getOperation().equalsIgnoreCase("<")) {
                return builder.lessThanOrEqualTo(root.<String> get(criteria.getKey()), criteria.getValue().toString());
            }
            else if (criteria.getOperation().equalsIgnoreCase("==")) {
                return builder.equal(root.<String> get(criteria.getKey()), criteria.getValue().toString());
            }
            else if (criteria.getOperation().equalsIgnoreCase("==L")) {
                return builder.equal(root.<Long> get(criteria.getKey()), criteria.getValue());
            }
            else if (criteria.getOperation().equalsIgnoreCase("==B")) {
                return builder.equal(root.<Boolean> get(criteria.getKey()), criteria.getValue());
            }
            else if (criteria.getOperation().equalsIgnoreCase(":")) {
                if (root.get(criteria.getKey()).getJavaType() == String.class) {
                    return builder.like(root.<String>get(criteria.getKey()), "%" + criteria.getValue() + "%");
                } else {
                    return builder.equal(root.get(criteria.getKey()), criteria.getValue());
                }
            }
            return null;
        }
    }
*/
}
