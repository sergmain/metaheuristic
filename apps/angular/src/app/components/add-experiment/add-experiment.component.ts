import {
    CdkTextareaAutosize
} from '@angular/cdk/text-field';
import {
    Component,
    OnInit,
    ViewChild,
    ElementRef
} from '@angular/core';
import {
    Location
} from '@angular/common';
import {
    ExperimentsService
} from '@app/services/experiments/experiments.service';
import {
    DefaultResponse
} from '@app/models';
import {
    LoadStates
} from '@app/enums/LoadStates';
import {
    FormControl,
    FormGroup,
    Validators
} from '@angular/forms';
import {
    Router
} from '@angular/router';
@Component({
    // tslint:disable-next-line: component-selector
    selector: 'add-experiment',
    templateUrl: './add-experiment.component.pug',
    styleUrls: ['./add-experiment.component.scss']
})

export class AddExperimentComponent implements OnInit {
    readonly states = LoadStates
    currentState = LoadStates.show
    response: DefaultResponse;
    experiment = {
        name: '',
        description: '',
        code: '',
        seed: '',
    };

    form = new FormGroup({
        name: new FormControl('', [Validators.required, Validators.minLength(3)]),
        description: new FormControl('', [Validators.required, Validators.minLength(3)]),
        code: new FormControl('', [Validators.required, Validators.minLength(3)]),
        seed: new FormControl('', [Validators.required, Validators.minLength(1)]),
    });


    constructor(
        private experimentsService: ExperimentsService,
        private location: Location,
        private router: Router,

    ) {}

    ngOnInit() {

    }

    cancel() {
        this.location.back();
    }

    create() {
        this.currentState = this.states.wait
        const subscribe = this.experimentsService.experiment
            .addCommit(this.form.value)
            .subscribe((response: DefaultResponse) => {
                this.response = response;
                this.currentState = this.states.show
                subscribe.unsubscribe()
                if (response.errorMessages || response.infoMessages) {

                } else {
                    this.router.navigate(['/launchpad', 'experiments']);
                }
            }, () => {
                this.currentState = this.states.show
            });
    }
}