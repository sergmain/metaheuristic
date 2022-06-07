/*
 * Metaheuristic, Copyright (C) 2017-2022, Innovation platforms, LLC
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

package ai.metaheuristic.db_copy;

import ai.metaheuristic.db_copy.primary.repo.PrimaryFunctionDataRepository;
import ai.metaheuristic.db_copy.secondary.repo.SecondaryFunctionDataRepository;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Blob;
import java.sql.Timestamp;

/**
 * @author Serge
 * Date: 2/21/2022
 * Time: 9:33 PM
 */
@Service
@Slf4j
public class DbCopyTxService {

//    @Autowired
//    @Qualifier("secondaryEntityManagerFactory")
//    private LocalContainerEntityManagerFactoryBean emf;
    @Autowired
    @Qualifier("secondaryEntityManagerFactory")
    public EntityManagerFactory secondaryEntityManagerFactory;

    @Autowired
    public PrimaryFunctionDataRepository primaryFunctionDataRepository;
    @Autowired
    public SecondaryFunctionDataRepository secondaryFunctionDataRepository;

    @Transactional(readOnly = true, value="primaryTransactionManager")
    @SneakyThrows
    public void storeToFileFromPrimary(String code, Path trgFile) {
        Blob blob = primaryFunctionDataRepository.getDataAsStreamByCode(code);
        if (blob==null) {
            log.warn("#088.010 Binary data for code {} wasn't found", code);
            throw new RuntimeException("#088.010 Function data wasn't found, code: " + code);
        }
        try (InputStream is = blob.getBinaryStream()) {
            Files.copy(is, trgFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Transactional("secondaryTransactionManager")
    @SneakyThrows
    public void save(String functionCode, Timestamp uploadTs, String params, InputStream is, long size) {
        ai.metaheuristic.db_copy.secondary.beans.FunctionData fd = new ai.metaheuristic.db_copy.secondary.beans.FunctionData();
        fd.functionCode = functionCode;
        fd.uploadTs = uploadTs;
        fd.params = params;

        System.out.print("\t\tget EntityManager");
        EntityManager em = secondaryEntityManagerFactory.createEntityManager();
        System.out.println(" OK");

        Blob blob = Hibernate.getLobCreator(em.unwrap(SessionImplementor.class)).createBlob(is, size);
        fd.setData(blob);

        System.out.print("\t\tsave to db in tx");
        secondaryFunctionDataRepository.save(fd);
        System.out.println(" OK");
    }

}
