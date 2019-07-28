import { Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LoadStates } from '@app/enums/LoadStates';
import { CtFileUploadComponent } from '@app/ct';

@Component({
    selector: 'atlas-experiment-export-import',
    templateUrl: './atlas-experiment-export-import.component.pug',
    styleUrls: ['./atlas-experiment-export-import.component.scss']
})
export class AtlasExperimentExportImportComponent implements OnInit {
    readonly states = LoadStates;
    currentStates = new Set();
    atlasDownloadName: string;

    @ViewChild('fileUpload') fileUpload: CtFileUploadComponent;

    constructor(
        private route: ActivatedRoute,
        private router: Router
    ) {
        this.atlasDownloadName = `atlas-${this.route.snapshot.paramMap.get('atlasId')}.yaml`;
    }

    ngOnInit() {}

    back() {
        this.router.navigate(['/launchpad', 'atlas', 'experiments']);
    }

    upload() {
        // const formData: FormData = new FormData();
        // formData.append('file', this.fileUpload.fileInput.nativeElement.files[0]);
        // formData.append('planId', this.plan.id);
        // // TODO what if no planId
        // console.log(this);
        // const subscribe: Subscription = this.batchService.batch
        //     .upload(formData)
        //     .subscribe(
        //         (response: batch.upload.Response) => {
        //             // TODO replace|update conditional
        //             if (response.status.toLowerCase() === 'ok') {
        //                 this.router.navigate(['/launchpad', 'batch']);
        //             } else {
        //                 this.uploadResponse = response;
        //             }
        //         },
        //         () => {},
        //         () => subscribe.unsubscribe()
        //     );
    }
}