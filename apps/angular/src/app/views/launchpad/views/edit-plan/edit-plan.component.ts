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
    ActivatedRoute,
    Router
} from '@angular/router';
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
    // tslint:disable-next-line: component-selector
    selector: 'edit-plan',
    templateUrl: './edit-plan.component.pug',
    styleUrls: ['./edit-plan.component.scss']
})

export class EditPlanComponent implements OnInit {
    // tslint:disable-next-line: typedef
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

    // tslint:disable-next-line: typedef
    ngOnInit() {
        this.updateResponse();
    }
    // tslint:disable-next-line: typedef
    updateResponse() {
        const id: string | number = this.route.snapshot.paramMap.get('planId');
        const subscribe: Subscription = this.plansService.plan
            .get(id)
            .subscribe((response: PlanResponse.Response) => {
                this.response = response;
                this.plan = response.plan;
                this.currentState = this.states.show;
                subscribe.unsubscribe();
            });
    }
    // tslint:disable-next-line: typedef
    cancel() {
        this.router.navigate(['/launchpad', 'plans']);
    }
    // tslint:disable-next-line: typedef
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
            });
    }
    // tslint:disable-next-line: typedef
    validate() {
        this.currentState = this.states.wait;
        const id: string | number = this.route.snapshot.paramMap.get('planId');
        const subscribe: Subscription = this.plansService
            .plan.validate(id)
            .subscribe((data: PlanResponse.Response) => {
                this.response = data;
                this.currentState = this.states.show;
                subscribe.unsubscribe();
            });
    }
}