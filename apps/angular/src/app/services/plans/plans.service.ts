import { Injectable } from '@angular/core';
import { HttpClient, } from '@angular/common/http';
import { urls } from './urls';
import { Observable } from 'rxjs';

@Injectable({ providedIn: 'root' })

export class PlansService {
    constructor(
        private http: HttpClient
    ) {}

    plans: any = {
        get: (data: any): Observable < any > => this.http.get(urls.plans.get(data))
    };

    plan: any = {
        get: (id: string): Observable < any > => this.http.get(urls.plan.get(id)),
        update: (id: number, code: string, params: string): Observable < any > => {
            return this.http.post(
                urls.plan.edit(), {
                    id,
                    code,
                    params
                });
        },

        validate: (id: string | number): Observable < any > => {
            return this.http.get(urls.plan.validate(id));
        },

        delete: (
                id: string | number
            ): Observable < any > =>
            this.http.post(urls.plan.delete({ id }), { id }),

        archive: (id: string | number): Observable < any > =>
            this.http.post(urls.plan.archive({ id }), { id }),

        add: (planYaml: string): Observable < any > =>
            this.http.post(urls.plan.add({ planYaml }), {})
    };

    workbooks: any = {
        get: (
                planId: string | number,
                page: any
            ): Observable < any > =>
            this.http.get(urls.workbooks.get(planId, { page }))

    };

    workbook: any = {
        get: (): void => {},
        edit: (): void => {},
        validate: (): void => {},
        delete: (): void => {},

        targetExecState: (
                planId: string | number,
                state: string | number,
                id: string | number
            ): Observable < any > =>
            this.http.get(urls.workbook.targetExecState(planId, state, id)),

        deleteCommit: (
                planId: string | number,
                workbookId: string | number
            ): Observable < any > =>
            this.http.post(urls.workbook.deleteCommit({
                planId,
                workbookId,
            }), null),

        addCommit: (
                floplanId: string | number,
                code: string,
                inputResourceParams: string
            ): Observable < any > =>
            this.http.post(urls.workbook.addCommit({
                planId: floplanId,
                poolCode: code || '',
                inputResourceParams: inputResourceParams || ''
            }), null),
    };
}