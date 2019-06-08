import {
    Injectable
} from '@angular/core';
import {
    environment
} from 'environments/environment';
import {
    HttpClient
} from '@angular/common/http';
import {
    Observable
} from 'rxjs';
import {
    StationResponse
} from '@app/models';
import {
    urls
} from './urls';
@Injectable({
    providedIn: 'root'
})

export class StationsService {


    // @GetMapping("/stations")
    // @GetMapping(value = "/station/{id}")
    // @PostMapping("/station-form-commit")
    // @PostMapping("/station-delete-commit")

    constructor(
        private http: HttpClient
    ) {}

    stations = {
        get: (page: string | number) => this.http.get(urls.stations.get({
            page
        }))
    }

    station = {
        get: (id) => this.http.get(urls.station.get + id),
        form: (station: StationResponse.Station): Observable < any > => this.http.post(urls.station.form, station),
        delete: (id) => {
            return Observable.create((observer) => {
                this.http.post < any > (urls.station.delete + '?id=' + id, {
                        id
                    })
                    .subscribe(data => {
                        observer.next(data);
                        observer.complete();
                    });
            });
        }
    };
}