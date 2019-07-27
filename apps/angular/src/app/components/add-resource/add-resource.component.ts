import { Location } from '@angular/common';
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { LoadStates } from '@app/enums/LoadStates';
import { DefaultResponse } from '@app/models';
import { ResourcesService } from '@app/services/resources/resources.service';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'add-resource',
    templateUrl: './add-resource.component.pug',
    styleUrls: ['./add-resource.component.scss']
})

export class AddResourceComponent implements OnInit {
    readonly states = LoadStates;

    formUploadState: LoadStates = LoadStates.firstLoading;
    formState: LoadStates = LoadStates.firstLoading;

    urlResponse: DefaultResponse;
    fileResponse: DefaultResponse;

    uploadForm = new FormGroup({
        code: new FormControl('', [Validators.required, Validators.minLength(4)]),
        poolCode: new FormControl('', [Validators.required, Validators.minLength(4)]),
    });
    urlForm = new FormGroup({
        storageUrl: new FormControl('', [Validators.required, Validators.minLength(4)]),
        poolCode: new FormControl('', [Validators.required, Validators.minLength(4)]),
    });

    @ViewChild('fileInput') fileInput: ElementRef;

    constructor(
        private location: Location,
        private resourcesService: ResourcesService,
        private router: Router,
    ) {}

    ngOnInit() {}

    cancel() {
        this.location.back();
    }

    upload() {
        const formData: FormData = new FormData();

        formData.append('file', this.fileInput.nativeElement.files[0]);
        formData.append('code', this.uploadForm.value.code);
        formData.append('poolCode', this.uploadForm.value.poolCode);

        const successFn = (response: DefaultResponse) => {
            this.formUploadState = this.states.show;
            this.fileResponse = response;
            if (response.errorMessages || response.infoMessages) {
                //  ???
            } else {
                this.router.navigate(['/launchpad', 'resources']);
            }
        };

        const errorFn = (err) => {
            this.formUploadState = this.states.show;
        };

        this.formUploadState = this.states.wait;
        this.resourcesService.resource.upload(formData).subscribe(successFn, errorFn);
    }

    create() {
        const formData: FormData = new FormData();
        formData.append('code', this.urlForm.value.storageUrl);
        formData.append('poolCode', this.urlForm.value.poolCode);

        const successFn = (response: DefaultResponse) => {
            this.formState = this.states.show;
            this.urlResponse = response;
            if (response.errorMessages || response.infoMessages) {
                //  ???
            } else {
                this.router.navigate(['/launchpad', 'resources']);
            }
        };
        const errorFn = (err) => {
            this.formState = this.states.show;
        };

        this.formState = this.states.wait;
        this.resourcesService.resource.external(formData).subscribe(successFn, errorFn);
    }

    fileChanged(event) {
        if (this.fileInput.nativeElement.files.length) {
            this.formUploadState = this.states.show;
        } else {
            this.formUploadState = this.states.empty;
        }
    }
}