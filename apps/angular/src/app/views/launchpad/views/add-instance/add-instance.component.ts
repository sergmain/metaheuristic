import {
    Component,
    OnInit
} from '@angular/core';
import {
    Location
} from '@angular/common';
import {
    FlowsService
} from '@app/services/flows/flows.service';
import {
    ActivatedRoute,
    Router
} from '@angular/router';
import {
    state
} from '@app/helpers/state';
import {
    LoadStates
} from '@app/enums/LoadStates';
import {
    FlowInstancesAddCommitResponse
} from '@app/models';
import {
    Subscription
} from 'rxjs';
@Component({
    // tslint:disable-next-line: component-selector
    selector: 'add-instance',
    templateUrl: './add-instance.component.pug',
    styleUrls: ['./add-instance.component.scss']
})


export class AddInstanceComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();

    state = state;
    currentState = state.show;

    code: string;
    resources: string;

    responseSingle: FlowInstancesAddCommitResponse.Response;
    responseMulti: FlowInstancesAddCommitResponse.Response;
    constructor(
        private location: Location,
        private route: ActivatedRoute,
        private router: Router,
        private flowsService: FlowsService
    ) {}

    ngOnInit() {

    }

    cancel() {
        this.router.navigate(['/launchpad', 'flows', this.route.snapshot.paramMap.get('flowId'), 'instances']);
    }

    createWithCode() {
        this.currentStates.add(this.states.loading);
        const flowId: string = this.route.snapshot.paramMap.get('flowId');
        const subscribe: Subscription = this.flowsService.instance
            .addCommit(flowId, this.code, this.resources)
            .subscribe(
                (response: FlowInstancesAddCommitResponse.Response) => {
                    if (response.errorMessages) {
                        this.responseSingle = response;
                    } else {
                        this.router.navigate(['/launchpad', 'flows', response.flow.id, 'instances']);
                    }
                },
                () => {},
                () => {
                    this.currentStates.delete(this.states.loading);
                    subscribe.unsubscribe();
                });
    }

    createWithResource() {
        this.currentStates.add(this.states.loading);
        const flowId: string = this.route.snapshot.paramMap.get('flowId');
        const subscribe: Subscription = this.flowsService.instance
            .addCommit(flowId, this.code, this.resources)
            .subscribe(
                (response: FlowInstancesAddCommitResponse.Response) => {
                    if (response.errorMessages) {
                        this.responseMulti = response;
                    } else {
                        this.router.navigate(['/launchpad', 'flows', response.flow.id, 'instances']);
                    }
                },
                () => {},
                () => {
                    this.currentStates.delete(this.states.loading);
                    subscribe.unsubscribe();
                });
    }
}