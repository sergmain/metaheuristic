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
        get: (id: string): Observable < any > => this.http.get(urls.experiment.get(id)),

        info: (id: string): Observable < any > => this.http.get(urls.experiment.info(id)),

        // edit: (id: string): Observable < any > => this.http.get(urls.experiment.edit(id)),

        // addCommit: (data: any): Observable < any > => this.http.post(urls.experiment.addCommit(), data),

        // editCommit: (data: any): Observable < any > => this.http.post(urls.experiment.editCommit(), data),

        deleteCommit: (data: any): Observable < any > => this.http.post(urls.experiment.deleteCommit(data), null),

        // cloneCommit: (data: any): Observable < any > => this.http.post(urls.experiment.cloneCommit(data), null),

        featurePlotDataPart: (atlasId: string, experimentId: string, featureId: string, params: any, paramsAxis: any): Observable < any > => this.http.post(urls.experiment.featurePlotDataPart(atlasId, experimentId, featureId, params, paramsAxis), null),

        featureProgressPart: (atlasId: string, experimentId: string, featureId: string, params: any): Observable < any > => this.http.post(urls.experiment.featureProgressPart(atlasId, experimentId, featureId, params), null),

        featureProgress: (atlasId: string, experimentId: string, featureId: string): Observable < any > => this.http.get(urls.experiment.featureProgress(atlasId, experimentId, featureId)),

        // featureProgressConsole: (taskId: string): Observable < any > => this.http.get(urls.experiment.featureProgressConsole(taskId)),

        featureProgressConsolePart: (atlasId: string, taskId: string): Observable < any > => this.http.post(urls.experiment.featureProgressConsolePart(atlasId, taskId), null),

        // taskRerun: (taskId: string): Observable < any > => this.http.post(urls.experiment.taskRerun(taskId), null),

        // metadataAddCommit: (experimentId: string, data: any): Observable < any > => this.http.post(urls.experiment.metadataAddCommit(experimentId, data), null),

        // metadataEditCommit: (experimentId: string, data: any): Observable < any > => this.http.post(urls.experiment.metadataEditCommit(experimentId, data), null),

        // metadataDeleteCommit: (experimentId: string, id: string): Observable < any > => this.http.get(urls.experiment.metadataDeleteCommit(experimentId, id)),

        // metadataDefaultAddCommit: (experimentId: string): Observable < any > => this.http.get(urls.experiment.metadataDefaultAddCommit(experimentId)),

        // snippetAddCommit: (id: string, data: any): Observable < any > => this.http.post(urls.experiment.snippetAddCommit(id, data), null),

        // snippetDeleteCommit: (experimentId: string, id: string): Observable < any > => this.http.get(urls.experiment.snippetDeleteCommit(experimentId, id)),
    };
}