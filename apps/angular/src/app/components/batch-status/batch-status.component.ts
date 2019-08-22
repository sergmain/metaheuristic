import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { LoadStates } from '@app/enums/LoadStates';
import { BatchService, batch } from '@app/services/batch/batch.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'batch-status',
    templateUrl: './batch-status.component.pug',
    styleUrls: ['./batch-status.component.scss']
})

export class BatchStatusComponent implements OnInit {
    readonly states = LoadStates;
    currentState: LoadStates = LoadStates.firstLoading;

    response: batch.status.Response;
    batchId: string

    constructor(
        private route: ActivatedRoute,
        private batchService: BatchService,
        private router: Router
    ) {}

    ngOnInit() {
        this.batchId = this.route.snapshot.paramMap.get('id');
        this.updateResponse();
    }
    updateResponse() {
        const subscribe: Subscription = this.batchService.batch
            .status(this.batchId )
            .subscribe(
                (response: batch.status.Response) => {
                    this.response = response;
                    this.currentState = this.states.show;
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                },
            );

    }
}