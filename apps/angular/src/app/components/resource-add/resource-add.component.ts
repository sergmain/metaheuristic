import { Location } from '@angular/common';
import { Component, OnInit, ViewChild } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { DefaultResponse } from '@app/models';
import { ResourcesService } from '@app/services/resources/resources.service';
import { CtFileUploadComponent } from '@src/app/ct';
import { Subscription } from 'rxjs';

@Component({
    selector: 'resource-add',
    templateUrl: './resource-add.component.pug',
    styleUrls: ['./resource-add.component.scss']
})

export class ResourceAddComponent implements OnInit {

    urlResponse: DefaultResponse;
    fileResponse: DefaultResponse;

    uploadForm = new FormGroup({
        code: new FormControl('', []),
        poolCode: new FormControl('', []),
    });

    urlForm = new FormGroup({
        storageUrl: new FormControl('', [Validators.required, Validators.minLength(1)]),
        poolCode: new FormControl('', [Validators.required, Validators.minLength(1)]),
    });

    @ViewChild('fileUpload') fileUpload: CtFileUploadComponent;

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

        formData.append('file', this.fileUpload.fileInput.nativeElement.files[0]);
        formData.append('code', this.uploadForm.value.code);
        formData.append('poolCode', this.uploadForm.value.poolCode);

        const subscribe: Subscription = this.resourcesService.resource
            .upload(formData)
            .subscribe((response: DefaultResponse) => {
                    this.fileResponse = response;
                    if (response.errorMessages || response.infoMessages) {
                        //  ???
                    } else {
                        this.router.navigate(['/launchpad', 'resources']);
                    }
                },
                () => {},
                () => subscribe.unsubscribe());
    }

    create() {
        const formData: FormData = new FormData();
        formData.append('code', this.urlForm.value.storageUrl);
        formData.append('poolCode', this.urlForm.value.poolCode);
        const subscribe: Subscription = this.resourcesService.resource
            .external(formData)
            .subscribe(
                (response: DefaultResponse) => {
                    this.urlResponse = response;
                    if (response.errorMessages || response.infoMessages) {
                        //  ???
                    } else {
                        this.router.navigate(['/launchpad', 'resources']);
                    }
                },
                () => {},
                () => subscribe.unsubscribe()
            );

    }
}