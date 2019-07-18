import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { LoadStates } from '@app/enums/LoadStates';
import { PlanResponse } from '@app/models';
import { PlansService } from '@app/services/plans/plans.service';
import { Subscription } from 'rxjs';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'add-plan',
    templateUrl: './add-plan.component.pug',
    styleUrls: ['./add-plan.component.scss']
})

export class AddPlanComponent implements OnInit {
    readonly states: any = LoadStates;

    currentState: LoadStates = LoadStates.show;
    planYaml: string = '';
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
            .add(this.planYaml)
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