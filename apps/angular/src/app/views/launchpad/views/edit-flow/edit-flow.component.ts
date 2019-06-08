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
    FlowResponse
} from '@app/models';
import {
    LoadStates
} from '@app/enums/LoadStates';

import {
    Subscription
} from 'rxjs';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'edit-flow',
    templateUrl: './edit-flow.component.pug',
    styleUrls: ['./edit-flow.component.scss']
})

export class EditFlowComponent implements OnInit {
    // tslint:disable-next-line: typedef
    readonly states = LoadStates;
    currentState: LoadStates = LoadStates.firstLoading;

    flow: FlowResponse.Flow;
    response: FlowResponse.Response;

    constructor(
        private location: Location,
        private route: ActivatedRoute,
        private flowsService: FlowsService,
        private router: Router,
    ) {}

    // tslint:disable-next-line: typedef
    ngOnInit() {
        this.updateResponse();
    }
    // tslint:disable-next-line: typedef
    updateResponse() {
        const id: string | number = this.route.snapshot.paramMap.get('flowId');
        const subscribe: Subscription = this.flowsService.flow
            .get(id)
            .subscribe((response: FlowResponse.Response) => {
                this.response = response;
                this.flow = response.flow;
                this.currentState = this.states.show;
                subscribe.unsubscribe();
            });
    }
    // tslint:disable-next-line: typedef
    cancel() {
        this.router.navigate(['/launchpad', 'flows']);
    }
    // tslint:disable-next-line: typedef
    save() {
        this.currentState = this.states.wait;
        const subscribe: Subscription = this.flowsService.flow
            .update(this.flow.id, this.flow.code, this.flow.params)
            .subscribe((data: FlowResponse.Response) => {
                if (data.errorMessages) {
                    this.currentState = this.states.show;
                    this.response = data;
                } else {
                    this.cancel();
                }
                subscribe.unsubscribe();
            });
    }
    // tslint:disable-next-line: typedef
    validate() {
        this.currentState = this.states.wait;
        const id: string | number = this.route.snapshot.paramMap.get('flowId');
        const subscribe: Subscription = this.flowsService
            .flow.validate(id)
            .subscribe((data: FlowResponse.Response) => {
                this.response = data;
                this.currentState = this.states.show;
                subscribe.unsubscribe();
            });
    }
}