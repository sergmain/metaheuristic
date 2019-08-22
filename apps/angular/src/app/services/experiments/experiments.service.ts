import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { experiment, experiments } from './urls';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })

export class ExperimentsService {
    constructor(private http: HttpClient) {}

    experiments = {
        get: (page: number): Observable < any > =>
            this.http.get(experiments.get(page))
    };

    experiment = {
        get: (id: string): Observable < any > =>
            this.http.get(experiment.get(id)),

        info: (id: string): Observable < any > =>
            this.http.get(experiment.info(id)),

        edit: (id: string): Observable < any > =>
            this.http.get(experiment.edit(id)),

        addCommit: (data: any): Observable < any > =>
            this.http.post(experiment.addCommit(), data),

        editCommit: (data: any): Observable < any > =>
            this.http.post(experiment.editCommit(), data),

        deleteCommit: (data: any): Observable < any > =>
            this.http.post(experiment.deleteCommit(data), null),

        cloneCommit: (data: any): Observable < any > =>
            this.http.post(experiment.cloneCommit(data), null),

        featurePlotDataPart: (experimentId: string, featureId: string, params: any, paramsAxis: any): Observable < any > =>
            this.http.post(experiment.featurePlotDataPart(experimentId, featureId, params, paramsAxis), null),

        featureProgressPart: (experimentId: string, featureId: string, params: any): Observable < any > =>
            this.http.post(experiment.featureProgressPart(experimentId, featureId, params), null),

        featureProgress: (experimentId: string, featureId: string): Observable < any > =>
            this.http.get(experiment.featureProgress(experimentId, featureId)),

        featureProgressConsole: (taskId: string): Observable < any > =>
            this.http.get(experiment.featureProgressConsole(taskId)),

        featureProgressConsolePart: (taskId: string): Observable < any > =>
            this.http.post(experiment.featureProgressConsolePart(taskId), null),

        taskRerun: (taskId: string): Observable < any > =>
            this.http.post(experiment.taskRerun(taskId), null),

        metadataAddCommit: (experimentId: string, data: any): Observable < any > =>
            this.http.post(experiment.metadataAddCommit(experimentId, data), null),

        metadataEditCommit: (experimentId: string, data: any): Observable < any > =>
            this.http.post(experiment.metadataEditCommit(experimentId, data), null),

        metadataDeleteCommit: (experimentId: string, id: string): Observable < any > =>
            this.http.get(experiment.metadataDeleteCommit(experimentId, id)),

        metadataDefaultAddCommit: (experimentId: string): Observable < any > =>
            this.http.get(experiment.metadataDefaultAddCommit(experimentId)),

        snippetAddCommit: (id: string, data: any): Observable < any > =>
            this.http.post(experiment.snippetAddCommit(id, data), null),

        snippetDeleteCommit: (experimentId: string, id: string): Observable < any > =>
            this.http.get(experiment.snippetDeleteCommit(experimentId, id)),

        toAtlas: (id: string): Observable < any > =>
            this.http.get(experiment.toAtlas(id))
    };
}