import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { urls } from './urls';
import { batches } from './response';
import { PlansResponse } from '@app/models/PlansResponse';

export * from './response';

@Injectable({ providedIn: 'root' })
export class BatchService {

    constructor(
        private http: HttpClient
    ) {}

    batches: any = {
        get: (data: any): Observable < any > => this.http.get(urls.batches.get(data))
    };

    batch: any = {
        get: (id: string): Observable < any > => this.http.get(urls.batch.get(id)),

        delete: (id: string | number): Observable < any > => {
            return this.http.post(urls.batch.delete({ id }), { id });
        },
        status: (id: string | number): Observable < any > => {
            return this.http.get(urls.batch.status(id));
        },
        add: (code: string, params: string): Observable < any > => {
            return this.http.get < any > (urls.batch.add());
        },
        upload: (): void => {}
    };
}