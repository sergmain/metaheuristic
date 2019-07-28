import { Component, ElementRef, OnInit, ViewChild } from '@angular/core';

@Component({
    selector: 'ct-file-upload',
    templateUrl: './ct-file-upload.component.pug',
    styleUrls: ['./ct-file-upload.component.scss']
})
export class CtFileUploadComponent implements OnInit {

    value: string = '';
    @ViewChild('fileInput') fileInput: ElementRef;

    constructor() {}

    ngOnInit() {}

    fileChanged() {
        this.value = this.fileInput.nativeElement.value;
        console.log(this)
    }
    removeFile() {
        this.fileInput.nativeElement.value = '';
        this.value = '';
    }
}