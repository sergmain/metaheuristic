import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { PlanResponse } from '@app/models';
import { PlansService } from '@app/services/plans/plans.service';
import { Subscription } from 'rxjs';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { MatButton } from '@angular/material';
import { response } from '@services/plans/response';
@Component({
    selector: 'plan-add',
    templateUrl: './plan-add.component.pug',
    styleUrls: ['./plan-add.component.scss']
})

export class PlanAddComponent implements OnInit {
    planYaml: string = '';
    response: response.plan.Add;
    @ViewChild('submitButton') submitButton: MatButton;

    form = new FormGroup({
        planYaml: new FormControl('', [Validators.required, Validators.minLength(1)]),
    });

    constructor(
        private plansService: PlansService,
        private location: Location
    ) {}

    ngOnInit() {}

    cancel() {
        this.location.back();
    }

    create() {
        this.response = null;
        this.submitButton.disabled = true;
        this.plansService.plan
            .add(this.planYaml)
            .subscribe(
                (response: response.plan.Add) => {
                    this.response = response;
                    if (this.response.status.toLowerCase() === 'ok') {
                        this.cancel();
                    }
                    this.submitButton.disabled = false;
                },
                () => {
                    this.submitButton.disabled = false;
                }
            )
    }
}