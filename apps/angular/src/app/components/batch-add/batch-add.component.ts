import { Component, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { LoadStates } from '@app/enums/LoadStates';
import { Plan } from '@app/models/Plan';
import { batch, BatchService } from '@app/services/batch/batch.service';
import { CtFileUploadComponent } from '@src/app/ct';
import { Subscription } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { SettingsService } from '@services/settings/settings.service';

@Component({
    selector: 'batch-add',
    templateUrl: './batch-add.component.pug',
    styleUrls: ['./batch-add.component.scss']
})

export class BatchAddComponent implements OnInit {
    readonly states = LoadStates;

    currentStates = new Set();
    response: batch.add.Response;
    uploadResponse: batch.upload.Response;

    plan: Plan;
    file: any;
    listOfPlans: Plan[] = [];
    @ViewChild('fileUpload') fileUpload: CtFileUploadComponent;

    constructor(
        private batchService: BatchService,
        private router: Router,
        private translate: TranslateService,
        private settingsService: SettingsService
    ) {
        this.currentStates.add(this.states.firstLoading);
        this.settingsService.languageObserver.subscribe((lang: string) => this.translate.use(lang));
    }

    ngOnInit() { this.updateResponse(); }

    updateResponse() {
        const subscribe: Subscription = this.batchService.batch
            .add()
            .subscribe(
                (response: batch.add.Response) => {
                    this.response = response;
                    this.listOfPlans = this.response.items;
                },
                () => {},
                () => {
                    this.currentStates.delete(this.states.firstLoading);
                    this.currentStates.delete(this.states.loading);
                    this.currentStates.add(this.states.show);
                    subscribe.unsubscribe();
                }
            );
    }

    back() {
        this.router.navigate(['/launchpad', 'batch']);
    }

    upload() {
        const formData: FormData = new FormData();
        formData.append('file', this.fileUpload.fileInput.nativeElement.files[0]);
        formData.append('planId', this.plan.id);
        const subscribe: Subscription = this.batchService.batch
            .upload(formData)
            .subscribe(
                (response: batch.upload.Response) => {
                    if (response.status.toLowerCase() === 'ok') {
                        this.router.navigate(['/launchpad', 'batch']);
                    }
                    this.uploadResponse = response;
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }
    fileUploadChanged() {
        this.file = this.fileUpload.fileInput.nativeElement.files[0] || false;
    }
}