import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { urls } from './urls';
import { Observable } from 'rxjs';

export * from './response';

@Injectable({ providedIn: 'root' })

export class ExperimentsService {
    constructor(private http: HttpClient) {}

    experiments = {
        get: (page: number): Observable < any > => this.http.get(urls.experiments.get(page))
    };

    experiment = {
        get: (id: string | number): Observable < any > => this.http.get(urls.experiment.get(id)),

        info: (id: string | number): Observable < any > => this.http.get(urls.experiment.info(id)),

        edit: (id: string | number): Observable < any > => this.http.get(urls.experiment.edit(id)),

        addCommit: (data: any): Observable < any > => this.http.post(urls.experiment.addCommit(), data),

        editCommit: (data: any): Observable < any > => this.http.post(urls.experiment.editCommit(), data),

        deleteCommit: (data: any): Observable < any > => this.http.post(urls.experiment.deleteCommit(data), null),

        cloneCommit: (data: any): Observable < any > => this.http.post(urls.experiment.cloneCommit(data), null),

        featurePlotDataPart: (experimentId: string | number, featureId: string | number, params: any, paramsAxis: any): Observable < any > => this.http.post(urls.experiment.featurePlotDataPart(experimentId, featureId, params, paramsAxis), null),

        featureProgressPart: (experimentId: string | number, featureId: string | number, params: any): Observable < any > => this.http.post(urls.experiment.featureProgressPart(experimentId, featureId, params), null),

        featureProgress: (experimentId: string | number, featureId: string | number): Observable < any > => this.http.get(urls.experiment.featureProgress(experimentId, featureId)),

        featureProgressConsole: (taskId: string | number): Observable < any > => this.http.get(urls.experiment.featureProgressConsole(taskId)),

        featureProgressConsolePart: (taskId: string | number): Observable < any > => this.http.post(urls.experiment.featureProgressConsolePart(taskId), null),

        taskRerun: (taskId: string | number): Observable < any > => this.http.post(urls.experiment.taskRerun(taskId), null),

        metadataAddCommit: (experimentId: string | number, data: any): Observable < any > => this.http.post(urls.experiment.metadataAddCommit(experimentId, data), null),

        metadataEditCommit: (experimentId: string | number, data: any): Observable < any > => this.http.post(urls.experiment.metadataEditCommit(experimentId, data), null),

        metadataDeleteCommit: (experimentId: string | number, id: string | number): Observable < any > => this.http.get(urls.experiment.metadataDeleteCommit(experimentId, id)),

        metadataDefaultAddCommit: (experimentId: string | number): Observable < any > => this.http.get(urls.experiment.metadataDefaultAddCommit(experimentId)),

        snippetAddCommit: (id: string | number, data: any): Observable < any > => this.http.post(urls.experiment.snippetAddCommit(id, data), null),

        snippetDeleteCommit: (experimentId: string | number, id: string | number): Observable < any > => this.http.get(urls.experiment.snippetDeleteCommit(experimentId, id)),
    };
}