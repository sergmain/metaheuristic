import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { urls } from './urls';
import { Observable } from 'rxjs';

export * from './response';


@Injectable({ providedIn: 'root' })
export class AtlasService {

    constructor(private http: HttpClient) {}

    experiments = {
        get: (page: number): Observable < any > => this.http.get(urls.experiments.get(page))
    };

    experiment = {
        get: (id: string | number): Observable < any > => this.http.get(urls.experiment.get(id)),
        info: (id: string | number): Observable < any > => this.http.get(urls.experiment.info(id)),
    };
}