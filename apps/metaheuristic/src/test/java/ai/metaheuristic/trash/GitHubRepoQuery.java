/*
 * Metaheuristic, Copyright (C) 2017-2024, Innovation platforms, LLC
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

package ai.metaheuristic.trash;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * @author Sergio Lissner
 * Date: 2/22/2024
 * Time: 6:44 PM
 */
public class GitHubRepoQuery {

    public static void main(String[] args) throws URISyntaxException, IOException, InterruptedException {
        String language = "python";
        String license = "mit";
        int perPage = 10; // Number of items per page
        int page = 1; // Page number

        HttpResponse<String> response;
        try (HttpClient httpClient = HttpClient.newHttpClient()) {
            String apiUrl = String.format("https://api.github.com/search/repositories?q=language:%s+license:%s&page=%d&per_page=%d",
                    language, license, page, perPage);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(new URI(apiUrl))
                    .header("Accept", "application/json")
                    .build();

            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        }

        // Parse the response JSON to extract the count
        String responseBody = response.body();
        int repoCount = parseRepoCount(responseBody);

        System.out.println("Number of repositories: " + repoCount);
    }

    private static int parseRepoCount(String responseBody) {
        // Assuming the response body is in JSON format
        // Extract the count from the JSON string
        // This is just a simple example, in a real-world scenario, you would use a JSON parsing library
        String countStr = responseBody.split("\"total_count\":")[1].split(",")[0];
        return Integer.parseInt(countStr.trim());
    }
}


