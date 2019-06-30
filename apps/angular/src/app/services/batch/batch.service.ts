import { Injectable } from '@angular/core';
import { HttpClient, } from '@angular/common/http';
import { urls } from './urls';
import { Observable } from 'rxjs';
import { PlanResponse } from '@app/models';

@Injectable({ providedIn: 'root' })
export class BatchService {

    constructor(
        private http: HttpClient
    ) {}

    batches: any = {
        get: (data: any): Observable < object > => this.http.get(urls.batches.get(data))
    };

    batch: any = {
        get: (id: string): Observable < object > => this.http.get(urls.batch.get(id)),

        delete: (id: string | number): Observable < object > => {
            return this.http.post(urls.batch.delete({ id }), { id });
        },

        add: (code: string, params: string): Observable < PlanResponse.Response > => {
            return this.http.post < PlanResponse.Response > (urls.batch.add(), { code, params });
        }
    };
}