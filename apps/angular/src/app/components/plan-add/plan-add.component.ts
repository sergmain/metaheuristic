import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { LoadStates } from '@app/enums/LoadStates';
import { PlanResponse } from '@app/models';
import { PlansService } from '@app/services/plans/plans.service';
import { Subscription } from 'rxjs';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'plan-add',
    templateUrl: './plan-add.component.pug',
    styleUrls: ['./plan-add.component.scss']
})

export class PlanAddComponent implements OnInit {
    states: any = LoadStates;

    currentState: Set < LoadStates > = new Set();

    planYaml: string = '';
    response: PlanResponse.Response;

    constructor(
        private plansService: PlansService,
        private location: Location
    ) {
        this.currentState.add(LoadStates.loading);
    }

    ngOnInit() {
        this.currentState.delete(LoadStates.loading);
    }

    cancel() {
        this.location.back();
    }

    create() {
        this.currentState.add(LoadStates.loading);
        this.response = null;
        const subscribe: Subscription = this.plansService.plan
            .add(this.planYaml)
            .subscribe(
                (data: PlanResponse.Response) => {
                    this.response = { ...data };
                },
                () => {},
                () => {
                    this.currentState.delete(LoadStates.loading);
                    subscribe.unsubscribe();
                    if (this.response.status.toLowerCase() === 'ok') {
                        this.cancel();
                    }
                }
            );
    }
}