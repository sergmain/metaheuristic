import {
    Component,
    OnInit
} from '@angular/core';
import {
    Location
} from '@angular/common';
import {
    PlansService
} from '@app/services/plans/plans.service';
import {
    PlanResponse
} from '@app/models';

import {
    LoadStates
} from '@app/enums/LoadStates';
import {
    Subscription
} from 'rxjs';

@Component({
    selector: 'add-plan',
    templateUrl: './add-plan.component.pug',
    styleUrls: ['./add-plan.component.scss']
})

export class AddPlanComponent implements OnInit {
    readonly states: any = LoadStates;

    currentState: LoadStates = LoadStates.show;
    code: string = '';
    params: string = '';
    response: PlanResponse.Response;

    constructor(
        private plansService: PlansService,
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
        const subscribe: Subscription = this.plansService.plan
            .add(this.code, this.params)
            .subscribe((data: PlanResponse.Response) => {
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