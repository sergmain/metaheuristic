import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { LoadStates } from '@app/enums/LoadStates';
import { DefaultResponse } from '@app/models';
import { ExperimentsService } from '@app/services/experiments/experiments.service';
import { Subscription } from 'rxjs';
@Component({
    selector: 'experiment-add',
    templateUrl: './experiment-add.component.pug',
    styleUrls: ['./experiment-add.component.scss']
})

export class ExperimentAddComponent implements OnInit {
    readonly states = LoadStates;
    currentState = LoadStates.show;
    response: DefaultResponse;

    form = new FormGroup({
        name: new FormControl('', [Validators.required, Validators.minLength(3)]),
        description: new FormControl('', [Validators.required, Validators.minLength(3)]),
        code: new FormControl('', [Validators.required, Validators.minLength(3)]),
        seed: new FormControl('1', [Validators.required, Validators.minLength(1)]),
    });

    constructor(
        private experimentsService: ExperimentsService,
        private location: Location,
        private router: Router,
    ) {}

    ngOnInit() {}

    cancel() {
        this.location.back();
    }

    create() {
        this.currentState = this.states.wait;
        const subscribe: Subscription = this.experimentsService.experiment
            .addCommit(this.form.value)
            .subscribe(
                (response: DefaultResponse) => {
                    this.response = response;
                    if (response.errorMessages || response.infoMessages) {

                    } else {
                        this.router.navigate(['/launchpad', 'experiments']);
                    }
                },
                () => {},
                () => {
                    this.currentState = this.states.show;
                    subscribe.unsubscribe();
                }
            );
    }
}