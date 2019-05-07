import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common'
import { Plan, Instance, PlansService } from '@app/services/plans/plans.service';
import { ActivatedRoute } from '@angular/router'

@Component({
    selector: 'edit-plan',
    templateUrl: './edit-plan.component.html',
    styleUrls: ['./edit-plan.component.scss']
})

export class EditPlanComponent implements OnInit {

    id
    plan: Plan = new Plan({})

    constructor(
        private location: Location,
        private route: ActivatedRoute,
        private plansService: PlansService
    ) {}

    ngOnInit() {
        this.id = this.route.snapshot.paramMap.get('planId');
        this.plan = this.plansService.getPlan(this.id)
    }

    cancel() {
        this.location.back();
    }

    save() {
        this.plan = this.plansService.getPlan(this.id)
    }

    validate() {

    }
}