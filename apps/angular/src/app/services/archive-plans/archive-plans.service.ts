import { Injectable } from '@angular/core';
import { HttpClient, } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PlanResponse } from '@app/models';
import { environment } from '@src/environments/environment';
import jsonToUrlParams from '@app/helpers/jsonToUrlParams';

const base: string = environment.baseUrl + 'launchpad/plan';

const urls: any = {
    plan: {
        delete: (data: any): string => base + '/plan-delete-commit?' + jsonToUrlParams(data)

    },
    plans: {
        get: (data: any): string => base + '/plans-archived-only?' + jsonToUrlParams(data)
    }
};


@Injectable({ providedIn: 'root' })
export class ArchivePlansService {
    constructor(
        private http: HttpClient
    ) {}

    plans = {
        get: (data: any): any => this.http.get(urls.plans.get(data))
    };
    plan = {
        delete: (id: string | number): Observable < object > => {
            return this.http.post(urls.plan.delete({ id }), { id });
        },
    };
}