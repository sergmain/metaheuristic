import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { urls } from './urls';
import { Observable } from 'rxjs';

export * from './response';

@Injectable({ providedIn: 'root' })
export class AtlasService {

    constructor(private http: HttpClient) {}

    experiments = {
        get: (page: number): Observable < any > =>
            this.http.get(urls.experiments.get(page))
    };

    experiment = {
        get: (id: string): Observable < any > =>
            this.http.get(urls.experiment.get(id)),

        info: (id: string): Observable < any > =>
            this.http.get(urls.experiment.info(id)),

        deleteCommit: (data: any): Observable < any > =>
            this.http.post(urls.experiment.deleteCommit(data), null),

        featureProgress: (atlasId: string, experimentId: string, featureId: string): Observable < any > =>
            this.http.get(urls.experiment.featureProgress(atlasId, experimentId, featureId)),

        featureProgressPart: (atlasId: string, experimentId: string, featureId: string, params: any, page: number): Observable < any > =>
            this.http.post(urls.experiment.featureProgressPart(atlasId, experimentId, featureId, params, { page }), null),

        featureProgressConsolePart: (atlasId: string, taskId: string): Observable < any > =>
            this.http.post(urls.experiment.featureProgressConsolePart(atlasId, taskId), null),

        featurePlotDataPart: (atlasId: string, experimentId: string, featureId: string, params: any, paramsAxis: any): Observable < any > =>
            this.http.post(urls.experiment.featurePlotDataPart(atlasId, experimentId, featureId, params, paramsAxis), null),
    };
}