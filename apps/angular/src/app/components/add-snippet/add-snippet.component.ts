import {
    Component,
    OnInit,
    ElementRef,
    ViewChild
} from '@angular/core';
import {
    Router
} from '@angular/router';
import {
    Location
} from '@angular/common';
import {
    SnippetsService
} from '@app/services/snippets/snippets.service';
import {
    DefaultResponse
} from '@app/models/';
import {
    LoadStates
} from '@app/enums/LoadStates';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'add-snippet',
    templateUrl: './add-snippet.component.pug',
    styleUrls: ['./add-snippet.component.scss']
})

export class AddSnippetComponent implements OnInit {
    readonly states = LoadStates;

    currentState = this.states.loading;
    uploadState = this.states.empty;

    response: DefaultResponse;

    @ViewChild('form') form: ElementRef;
    @ViewChild('fileInput') fileInput: ElementRef;


    constructor(
        private snippetsService: SnippetsService,
        private location: Location,
        private router: Router,

    ) {}

    // tslint:disable-next-line: typedef
    ngOnInit() {
        this.currentState = this.states.show;
    }
    // tslint:disable-next-line: typedef
    cancel() {
        this.location.back();

    }
    // tslint:disable-next-line: typedef
    upload() {
        this.uploadState = this.states.loading;
        const formData = new FormData(this.form.nativeElement);
        const subscribe = this.snippetsService.snippet.upload(formData)
            .subscribe((response: DefaultResponse) => {
                this.response = response;
                if (response.status.toLowerCase() === 'ok') {
                    this.router.navigate(['/launchpad', 'snippets']);
                } else {
                    this.uploadState = this.states.show;
                }
                subscribe.unsubscribe();
            });
    }
    // tslint:disable-next-line: typedef
    fileChanged(event) {
        if (this.fileInput.nativeElement.files[0].name) {
            this.uploadState = this.states.show;
        } else {
            this.uploadState = this.states.empty;
        }
    }
}