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
    state
} from '@app/helpers/state';
import {
    LoadStates
} from '@app/enums/LoadStates';
import {
    WorkbookAddCommitResponse
} from '@app/models';
import {
    Subscription
} from 'rxjs';
@Component({
    // tslint:disable-next-line: component-selector
    selector: 'add-workbook',
    templateUrl: './add-workbook.component.pug',
    styleUrls: ['./add-workbook.component.scss']
})


export class AddWorkbookComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();

    state = state;
    currentState = state.show;

    code: string;
    resources: string;

    responseSingle: WorkbookAddCommitResponse.Response;
    responseMulti: WorkbookAddCommitResponse.Response;
    constructor(
        private location: Location,
        private route: ActivatedRoute,
        private router: Router,
        private plansService: PlansService
    ) {}

    ngOnInit() {

    }

    cancel() {
        this.router.navigate(['/launchpad', 'plans', this.route.snapshot.paramMap.get('planId'), 'workbooks']);
    }

    createWithCode() {
        this.currentStates.add(this.states.loading);
        const planId: string = this.route.snapshot.paramMap.get('planId');
        const subscribe: Subscription = this.plansService.workbook
            .addCommit(planId, this.code, this.resources)
            .subscribe(
                (response: WorkbookAddCommitResponse.Response) => {
                    if (response.errorMessages) {
                        this.responseSingle = response;
                    } else {
                        this.router.navigate(['/launchpad', 'plans', response.plan.id, 'workbooks']);
                    }
                },
                () => {},
                () => {
                    this.currentStates.delete(this.states.loading);
                    subscribe.unsubscribe();
                });
    }

    createWithResource() {
        this.currentStates.add(this.states.loading);
        const planId: string = this.route.snapshot.paramMap.get('planId');
        const subscribe: Subscription = this.plansService.workbook
            .addCommit(planId, this.code, this.resources)
            .subscribe(
                (response: WorkbookAddCommitResponse.Response) => {
                    if (response.errorMessages) {
                        this.responseMulti = response;
                    } else {
                        this.router.navigate(['/launchpad', 'plans', response.plan.id, 'workbooks']);
                    }
                },
                () => {},
                () => {
                    this.currentStates.delete(this.states.loading);
                    subscribe.unsubscribe();
                });
    }
}