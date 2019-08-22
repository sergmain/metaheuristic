import { Component, OnInit, ViewChild } from '@angular/core';
import { MatButton, MatTableDataSource } from '@angular/material';
import { ActivatedRoute } from '@angular/router';
import { LoadStates } from '@app/enums/LoadStates';
import { WorkbookExecState } from '@app/enums/WorkbookExecState';
import { WorkbooksResponse } from '@app/models';
import { PlansService } from '@app/services/plans/plans.service';



@Component({
    selector: 'workbooks-view',
    templateUrl: './workbooks.component.pug',
    styleUrls: ['./workbooks.component.scss']
})
// TODO: нет в ответе pegeable
export class WorkbooksComponent implements OnInit {
    readonly states = LoadStates;
    readonly execState = WorkbookExecState;
    currentState = this.states.loading;

    response: WorkbooksResponse.Response;
    plan: WorkbooksResponse.Plan;
    planId;
    dataSource = new MatTableDataSource < WorkbooksResponse.Workbook > ([]);
    columnsToDisplay = [
        'id',
        'planCode',
        'inputResourceParam',
        'createdOn',
        'isPlanValid',
        'isWorkbookValid',
        'execState',
        'completedOn',
        'bts'
    ];

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;

    constructor(
        private route: ActivatedRoute,
        private plansService: PlansService
    ) {}

    ngOnInit() {
        this.planId = this.route.snapshot.paramMap.get('planId');
        this.getResponse(0);
    }

    getResponse(page) {
        this.currentState = this.states.loading;
        let subscribe = this.plansService.workbooks
            .get(this.planId, page)
            .subscribe((response: WorkbooksResponse.Response) => {
                this.response = response;
                this.plan = Object.values(response.plans)[0];
                this.dataSource = new MatTableDataSource(response.instances.content);
                this.currentState = this.states.show;
                this.prevTable.disabled = response.instances.first;
                this.nextTable.disabled = response.instances.last;
                subscribe.unsubscribe();
            });
    }

    updateResponse(page) {
        let subscribe = this.plansService.workbooks
            .get(this.planId, page)
            .subscribe((response: WorkbooksResponse.Response) => {
                this.response = response;
                this.plan = Object.values(response.plans)[0];
                this.dataSource = new MatTableDataSource(response.instances.content);
                this.prevTable.disabled = response.instances.first;
                this.nextTable.disabled = response.instances.last;
                subscribe.unsubscribe();
            });
    }


    delete(el) {
        el.__deleted = true;
        let subscribe = this.plansService.workbook
            .deleteCommit(el.planId, el.id)
            .subscribe((response) => {
                subscribe.unsubscribe();
            });
    }

    next() {
        this.updateResponse(this.response.instances.number + 1);
    }

    prev() {
        this.updateResponse(this.response.instances.number - 1);
    }


    runExecState(id, state) {
        let subscribe = this.plansService.workbook
            .targetExecState(this.planId, state, id)
            .subscribe((response) => {
                subscribe.unsubscribe();
                this.updateResponse(this.response.instances.number);
            });
    }

    stop(el, event) {
        event.target.disabled = true;
        this.runExecState(el.id, 'STOPPED');
    }

    start(el, event) {
        event.target.disabled = true;
        this.runExecState(el.id, 'STARTED');
    }

    produce(el, event) {
        event.target.disabled = true;
        this.runExecState(el.id, 'PRODUCED');
    }
}