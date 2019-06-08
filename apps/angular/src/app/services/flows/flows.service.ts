import {
    Injectable
} from '@angular/core';
import {
    HttpClient,
} from '@angular/common/http';
import {
    urls
} from './urls';
import {
    Observable
} from 'rxjs';
import {
    FlowResponse
} from '@app/models';

@Injectable({
    providedIn: 'root'
})

export class FlowsService {
    constructor(
        private http: HttpClient
    ) {}

    flows: any = {
        get: data => this.http.get(urls.flows.get(data))
    }

    flow: any = {
        get: (id: string): Observable < object > => this.http.get(urls.flow.get(id)),
        update: (id: number, code: string, params: string): Observable < object > => {
            return this.http.post(urls.flow.edit(), {
                id,
                code,
                params
            });
        },

        validate: (id: string | number): Observable < object > => {
            return this.http.get(urls.flow.validate(id))
        },

        delete: (id: string | number): Observable < object > => {
            return this.http.post(urls.flow.delete({
                id
            }), {
                id
            });
        },

        add: (code: string, params: string): Observable < FlowResponse.Response > => {
            return this.http.post < FlowResponse.Response > (urls.flow.add(), {
                code,
                params
            });
        }
    };

    instances: any = {
        get: (flowId, page): Observable < object > => {
            return this.http.get(urls.instances.get(flowId, {
                page
            }));
        }
    }

    instance: any = {
        get: () => {},
        edit: () => {},
        validate: () => {},
        delete: () => {},
        targetExecState: (flowId, state, id): Observable < object > => {
            return this.http.get(urls.instance.targetExecState(flowId, state, id))
        },
        deleteCommit: (flowId, instanceId): Observable < object > => {
            return this.http.post(urls.instance.deleteCommit({
                flowId,
                flowInstanceId: instanceId,
            }), null);
        },
        addCommit: (flowId, code, inputResourceParams): Observable < object > => {
            return this.http.post(urls.instance.addCommit({
                flowId,
                poolCode: code || '',
                inputResourceParams: inputResourceParams || ''
            }), null);
        },
    }
}