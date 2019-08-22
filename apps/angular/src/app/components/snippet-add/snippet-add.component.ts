import { Location } from '@angular/common';
import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { LoadStates } from '@app/enums/LoadStates';
import { DefaultResponse } from '@app/models/';
import { SnippetsService } from '@app/services/snippets/snippets.service';
import { CtFileUploadComponent } from '@src/app/ct';
import { Subscription } from 'rxjs';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'snippet-add',
    templateUrl: './snippet-add.component.pug',
    styleUrls: ['./snippet-add.component.scss']
})

export class SnippetAddComponent implements OnInit {
    readonly states = LoadStates;

    response: DefaultResponse;

    @ViewChild('fileUpload') fileUpload: CtFileUploadComponent;

    constructor(
        private snippetsService: SnippetsService,
        private location: Location,
        private router: Router,

    ) {}

    ngOnInit() {}

    cancel() {
        this.location.back();
    }

    upload() {
        const formData: FormData = new FormData();
        formData.append('file', this.fileUpload.fileInput.nativeElement.files[0]);

        const subscribe: Subscription = this.snippetsService.snippet.upload(formData)
            .subscribe(
                (response: DefaultResponse) => {
                    this.response = response;
                    if (response.status.toLowerCase() === 'ok') {
                        this.router.navigate(['/launchpad', 'snippets']);
                    }
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }

}