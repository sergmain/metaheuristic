import { Injectable } from '@angular/core';
import { HttpClient, } from '@angular/common/http';
import { urls } from './urls';
import { Observable } from 'rxjs';
import { PlanResponse } from '@app/models';

@Injectable({ providedIn: 'root' })

export class PlansService {
    constructor(
        private http: HttpClient
    ) {}

    plans: any = {
        get: data => this.http.get(urls.plans.get(data))
    };

    plan: any = {
        get: (id: string): Observable < object > => this.http.get(urls.plan.get(id)),
        update: (id: number, params: string): Observable < object > => {
            return this.http.post< PlanResponse.Response > (urls.plan.edit({
                planId: id,
                planYaml: params
            }), null);
        },

        validate: (id: string | number): Observable < object > => {
            return this.http.get(urls.plan.validate(id))
        },

        delete: (id: string | number): Observable < object > => {
            return this.http.post(urls.plan.delete({
                id
            }), {
                id
            });
        },

        add: (params: string): Observable < PlanResponse.Response > => {
            return this.http.post < PlanResponse.Response > (urls.plan.add({
                planYaml: params
            }), null);
        }
    };

    workbooks: any = {
        get: (planId, page): Observable < object > => {
            return this.http.get(urls.workbooks.get(planId, {
                page
            }));
        }
    };

    workbook: any = {
        get: () => {},
        edit: () => {},
        validate: () => {},
        delete: () => {},
        targetExecState: (planId, state, id): Observable < object > => {
            return this.http.get(urls.workbook.targetExecState(planId, state, id))
        },
        deleteCommit: (planId, workbookId): Observable < object > => {
            return this.http.post(urls.workbook.deleteCommit({
                planId: planId,
                workbookId: workbookId,
            }), null);
        },
        addCommit: (floplanId, code, inputResourceParams): Observable < object > => {
            return this.http.post(urls.workbook.addCommit({
                planId: floplanId,
                poolCode: code || '',
                inputResourceParams: inputResourceParams || ''
            }), null);
        },
    }
}