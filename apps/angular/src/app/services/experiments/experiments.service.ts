import {
    Injectable
} from '@angular/core';
import {
    HttpClient,
    HttpHeaders
} from '@angular/common/http';
import {
    Observable,
    of ,
    from
} from 'rxjs';
import {
    map
} from 'rxjs/operators';
import {
    urls
} from './urls';

@Injectable({
    providedIn: 'root'
})

export class ExperimentsService {
    constructor(
        private http: HttpClient
    ) {}

    experiments = {
        get: (page) => this.http.get(urls.experiments.get(page))
    };

    experiment = {
        get: id => this.http.get(urls.experiment.get(id)),
        info: id => this.http.get(urls.experiment.info(id)),

        edit: (id) => this.http.get(urls.experiment.edit(id)),
        addCommit: (data) => this.http.post(urls.experiment.addCommit(), data),
        editCommit: (data) => this.http.post(urls.experiment.editCommit(), data),
        deleteCommit: (data) => this.http.post(urls.experiment.deleteCommit(data), null),
        cloneCommit: (data) => this.http.post(urls.experiment.cloneCommit(data), null),

        featurePlotDataPart: (experimentId, featureId, params, paramsAxis) => {
            return this.http.post(urls.experiment.featurePlotDataPart(experimentId, featureId, params, paramsAxis), null);
        },
        featureProgressPart: (experimentId, featureId, params) => {
            return this.http.post(urls.experiment.featureProgressPart(experimentId, featureId, params), null);
        },
        featureProgress: (experimentId, featureId) => this.http.get(urls.experiment.featureProgress(experimentId, featureId)),
        featureProgressConsole: taskId => this.http.get(urls.experiment.featureProgressConsole(taskId)),
        featureProgressConsolePart: taskId => this.http.post(urls.experiment.featureProgressConsolePart(taskId), null),

        taskRerun: taskId => this.http.post(urls.experiment.taskRerun(taskId), null),

        metadataAddCommit: (experimentId, data) => this.http.post(urls.experiment.metadataAddCommit(experimentId, data), null),
        metadataEditCommit: (experimentId, data) => this.http.post(urls.experiment.metadataEditCommit(experimentId, data), null),
        metadataDeleteCommit: (experimentId, id) => this.http.get(urls.experiment.metadataDeleteCommit(experimentId, id)),
        metadataDefaultAddCommit: (experimentId) => this.http.get(urls.experiment.metadataDefaultAddCommit(experimentId)),

        snippetAddCommit: (id, data) => this.http.post(urls.experiment.snippetAddCommit(id, data), null),
        snippetDeleteCommit: (experimentId, id) => this.http.get(urls.experiment.snippetDeleteCommit(experimentId, id)),
    };
}