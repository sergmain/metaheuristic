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
    FlowResponse
} from '@app/models';

import {
    LoadStates
} from '@app/enums/LoadStates';
import {
    Subscription
} from 'rxjs';

@Component({
    selector: 'add-flow',
    templateUrl: './add-flow.component.pug',
    styleUrls: ['./add-flow.component.scss']
})

export class AddFlowComponent implements OnInit {
    readonly states: any = LoadStates;

    currentState: LoadStates = LoadStates.show;
    code: string = '';
    params: string = '';
    response: FlowResponse.Response;

    constructor(
        private flowsService: FlowsService,
        private location: Location
    ) {}

    ngOnInit(): any {

    }

    cancel(): any {
        this.location.back();
    }

    create(): any {
        this.currentState = LoadStates.loading;
        this.response = null;
        const subscribe: Subscription = this.flowsService.flow
            .add(this.code, this.params)
            .subscribe((data: FlowResponse.Response) => {
                this.currentState = LoadStates.show;
                this.response = {
                    ...data
                };
                subscribe.unsubscribe();
                if (this.response.status.toLowerCase() === 'ok') {
                    this.cancel();
                }
            });
    }
}