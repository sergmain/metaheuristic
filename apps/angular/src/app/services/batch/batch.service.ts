import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { urls } from './urls';

export * from './response';
export * from './Bacth';

@Injectable({ providedIn: 'root' })

export class BatchService {

    constructor(
        private http: HttpClient
    ) {}

    batches: any = {
        get: (data: any): Observable < any > =>
            this.http.get(urls.batches.get(data))
    };

    batch: any = {
        get: (id: string): Observable < any > =>
            this.http.get(urls.batch.get(id)),

        delete: (batchId: string): Observable < any > =>
            this.http.get(urls.batch.delete(batchId)),

        status: (id: string): Observable < any > =>
            this.http.get(urls.batch.status(id)),

        add: (): Observable < any > =>
            this.http.get < any > (urls.batch.add()),

        upload: (formData: any): Observable < any > =>
            this.http.post(urls.batch.upload(), formData)
    };
}