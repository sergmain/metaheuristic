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

import ai.metaheuristic.db_copy.primary.beans.FunctionData;
import ai.metaheuristic.db_copy.primary.repo.PrimaryFunctionDataRepository;
import ai.metaheuristic.db_copy.secondary.repo.SecondaryFunctionDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.List;

/**
 * @author Serge
 * Date: 2/21/2022
 * Time: 9:00 PM
 */
@Service
@RequiredArgsConstructor
public class DbCopyService {

    public final PrimaryFunctionDataRepository srcFunctionDataRepository;
    public final SecondaryFunctionDataRepository trgFunctionDataRepository;
    public final DbCopyTxService dbCopyTxService;

    @SneakyThrows
    public void process() {
        List<String> srcCodes = srcFunctionDataRepository.findAllFunctionCodes();
        System.out.println("Source: ");
        srcCodes.forEach(System.out::println);

        List<String> trgCodes = trgFunctionDataRepository.findAllFunctionCodes();
        System.out.println("Target: ");
        trgCodes.forEach(System.out::println);

        for (FunctionData functionData : srcFunctionDataRepository.findAll()) {
            System.out.println("migrate " + functionData.getFunctionCode());

            System.out.print("\tcopy to file");
            File f = File.createTempFile("db-copy-", ".bin");
            dbCopyTxService.storeToFileFromPrimary(functionData.getFunctionCode(), f);
            System.out.println(" OK, lenght: " + f.length());

            System.out.println("\tpersist to db");
/*
            try (FileInputStream fis = new FileInputStream(f)) {
                dbCopyTxService.save(functionData.functionCode, functionData.uploadTs, functionData.params, fis, f.length());
            }
*/
            byte[] bytes = "123".getBytes();
            try (ByteArrayInputStream fis = new ByteArrayInputStream(bytes)) {
                dbCopyTxService.save(functionData.functionCode, functionData.uploadTs, functionData.params, fis, bytes.length);
            }
            System.out.println("\tpersist was OK");
        }

    }

    @SneakyThrows
    public void process1() {
        List<String> srcCodes = srcFunctionDataRepository.findAllFunctionCodes();
        System.out.println("Source: ");
        srcCodes.forEach(System.out::println);

        List<String> trgCodes = trgFunctionDataRepository.findAllFunctionCodes();
        System.out.println("Target: ");
        trgCodes.forEach(System.out::println);

        for (FunctionData functionData : srcFunctionDataRepository.findAll()) {
            System.out.println("migrate " + functionData.getFunctionCode());

            final String child = (functionData.functionCode + ".jar").replace(':', '-');
            System.out.print("\tcopy to file " + child);
            File f = new File("data", child);
            dbCopyTxService.storeToFileFromPrimary(functionData.getFunctionCode(), f);
            System.out.println(" OK, lenght: " + f.length());
        }

    }
}
