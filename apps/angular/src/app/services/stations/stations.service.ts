import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { urls } from './urls';
import { Station } from './response';

export * from './response';

@Injectable({ providedIn: 'root' })

export class StationsService {

    constructor(
        private http: HttpClient
    ) {}

    stations = {
        get: (page: string | number): Observable < any > => this.http.get(urls.stations.get({
            page
        }))
    };

    station = {
        get: (id: string | number): Observable < any > => this.http.get(urls.station.get(id)),
        form: (station: Station): Observable < any > => this.http.post(urls.station.form(station), station),
        delete: (id: string | number): Observable < any > => this.http.post(urls.station.delete(id), { id })
    };
}