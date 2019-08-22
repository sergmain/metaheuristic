import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LoadStates } from '@app/enums/LoadStates';
import { PlanResponse } from '@app/models';
import { PlansService } from '@app/services/plans/plans.service';
import { Subscription } from 'rxjs';


@Component({
    selector: 'plan-edit',
    templateUrl: './plan-edit.component.pug',
    styleUrls: ['./plan-edit.component.scss']
})

export class PlanEditComponent implements OnInit {
    readonly states = LoadStates;
    currentState: LoadStates = LoadStates.firstLoading;

    plan: PlanResponse.Plan;
    response: PlanResponse.Response;

    constructor(
        private location: Location,
        private route: ActivatedRoute,
        private plansService: PlansService,
        private router: Router,
    ) {}

    ngOnInit() {
        this.updateResponse();
    }

    updateResponse() {
        const id: string | number = this.route.snapshot.paramMap.get('planId');
        const subscribe: Subscription = this.plansService.plan
            .get(id)
            .subscribe((response: PlanResponse.Response) => {
                    this.response = response;
                    this.plan = response.plan;
                    this.currentState = this.states.show;
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }

    cancel() {
        this.router.navigate(['/launchpad', 'plans']);
    }

    save() {
        this.currentState = this.states.wait;
        const subscribe: Subscription = this.plansService.plan
            .update(this.plan.id, this.plan.params)
            .subscribe((data: PlanResponse.Response) => {
                    if (data.errorMessages) {
                        this.currentState = this.states.show;
                        this.response = data;
                    } else {
                        this.cancel();
                    }
                    subscribe.unsubscribe();
                },
                () => {
                    this.currentState = this.states.show;
                    subscribe.unsubscribe();
                });
    }

    validate() {
        this.currentState = this.states.wait;
        const id: string | number = this.route.snapshot.paramMap.get('planId');
        const subscribe: Subscription = this.plansService
            .plan.validate(id)
            .subscribe(
                (data: PlanResponse.Response) => {
                    this.response = data;
                    this.currentState = this.states.show;
                    subscribe.unsubscribe();
                },
                () => {
                    this.currentState = this.states.show;
                    subscribe.unsubscribe();
                }
            );
    }
}