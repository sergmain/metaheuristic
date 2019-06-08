import {
    Component,
    OnInit,
    ViewChild
} from '@angular/core';
import {
    FlowsService
} from '@app/services/flows/flows.service';
import {
    MatButton,
    MatTableDataSource
} from '@angular/material';
import {
    ActivatedRoute
} from '@angular/router';

import {
    FlowInstancesResponse,
    Flow,
    FlowInstance
} from '@app/models';
import {
    FlowInstanceExecState
} from '@app/enums/FlowInstanceExecState';
import {
    LoadStates
} from '@app/enums/LoadStates';


@Component({
    selector: 'instances-view',
    templateUrl: './instances.component.pug',
    styleUrls: ['./instances.component.scss']
})
// TODO: нет в ответе pegeable
export class InstancesComponent implements OnInit {
    readonly states = LoadStates;
    readonly execState = FlowInstanceExecState;
    currentState = this.states.loading;

    response: FlowInstancesResponse.Response;
    flow: FlowInstancesResponse.Flow;
    flowId;
    dataSource = new MatTableDataSource < FlowInstancesResponse.Instance > ([]);
    columnsToDisplay = [
        'id',
        'flowCode',
        'inputResourceParam',
        'createdOn',
        'isFlowValid',
        'isFlowInstanceValid',
        'execState',
        'completedOn',
        'bts'
    ];

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;

    constructor(
        private route: ActivatedRoute,
        private flowService: FlowsService
    ) {}

    ngOnInit() {
        this.flowId = this.route.snapshot.paramMap.get('flowId');
        this.getResponse(0);
    }

    getResponse(page) {
        this.currentState = this.states.loading;
        let subscribe = this.flowService.instances
            .get(this.flowId, page)
            .subscribe((response: FlowInstancesResponse.Response) => {
                this.response = response;
                this.flow = Object.values(response.flows)[0];
                this.dataSource = new MatTableDataSource(response.instances.content);
                this.currentState = this.states.show;
                this.prevTable.disabled = response.instances.first;
                this.nextTable.disabled = response.instances.last;
                subscribe.unsubscribe();
            });
    }

    updateResponse(page) {
        let subscribe = this.flowService.instances
            .get(this.flowId, page)
            .subscribe((response: FlowInstancesResponse.Response) => {
                this.response = response;
                this.flow = Object.values(response.flows)[0];
                this.dataSource = new MatTableDataSource(response.instances.content);
                this.prevTable.disabled = response.instances.first;
                this.nextTable.disabled = response.instances.last;
                subscribe.unsubscribe();
            });
    }


    delete(el) {
        el.__deleted = true;
        let subscribe = this.flowService.instance
            .deleteCommit(el.flowId, el.id)
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
        let subscribe = this.flowService.instance
            .targetExecState(this.flowId, state, id)
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