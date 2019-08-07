import { Component, ElementRef, OnInit, ViewChild, Input, OnChanges, EventEmitter, Output } from '@angular/core';
import { marker } from '@biesbjerg/ngx-translate-extract-marker';
import { TranslateService } from '@ngx-translate/core';
import { SettingsService } from '@services/settings/settings.service';

@Component({
    selector: 'ct-file-upload',
    templateUrl: './ct-file-upload.component.pug',
    styleUrls: ['./ct-file-upload.component.scss']
})
export class CtFileUploadComponent implements OnInit, OnChanges {
    @Output() changed = new EventEmitter < string > ();

    @ViewChild('fileInput') fileInput: ElementRef;
    @Input('buttonTitle') buttonTitle: string;
    @Input('acceptTypes') acceptTypes: string;


    value: string = '';
    buttonTitleString: string;
    accept: string;

    constructor() {
        this.accept = this.acceptTypes || '';
    }

    ngOnInit() {
        this.buttonTitleString = this.buttonTitle || 'Select File';
    }

    ngOnChanges() {
        this.buttonTitleString = this.buttonTitle || 'Select File';
    }

    fileChanged() {
        this.value = this.fileInput.nativeElement.value;
        this.changed.emit('fileChanged');
    }

    removeFile() {
        this.fileInput.nativeElement.value = '';
        this.value = '';
        this.changed.emit('fileChanged');
    }
}