import { HttpClient, HttpHeaders, HttpResponse } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { urls } from './urls';

export * from './Bacth';
export * from './response';

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

        deleteCommit: (batchId: string): Observable < any > =>
            this.http.post(urls.batch.deleteCommit(batchId), { batchId }),

        status: (id: string): Observable < any > =>
            this.http.get(urls.batch.status(id)),

        add: (): Observable < any > =>
            this.http.get < any > (urls.batch.add()),

        upload: (formData: any): Observable < any > =>
            this.http.post(urls.batch.upload(), formData),
        downloadBatchResult: (batchId: string): Observable < any > =>
            this.http.get(urls.batch.downloadBatchResult(batchId))
    };

    downloadFileSystem(batchId: string): Observable < HttpResponse < Blob >> {
        let headers = new HttpHeaders();
        headers = headers.append('Accept', 'application/zip');

        return this.http.get(urls.batch.downloadBatchResult(batchId), {
            headers,
            observe: 'response',
            responseType: 'blob'
        });
    }

    downloadClasspathFile(batchId: string): Observable < HttpResponse < Blob >> {
        let headers = new HttpHeaders();
        headers = headers.append('Accept', 'application/zip');

        return this.http.get(urls.batch.downloadBatchResult(batchId), {
            headers,
            observe: 'response',
            responseType: 'blob'
        }); 
    }

}