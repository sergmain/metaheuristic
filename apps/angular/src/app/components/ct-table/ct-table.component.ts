import {
    Component,
    OnInit,
    OnDestroy,
    ChangeDetectorRef
} from '@angular/core';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'ct-table',
    templateUrl: './ct-table.component.pug',
    styleUrls: ['./ct-table.component.scss']
})
export class CtTableComponent implements OnInit, OnDestroy {
    state = {
        wait: false
    };
    constructor(private changeDetector: ChangeDetectorRef) {}

    ngOnInit() {}

    ngOnDestroy() {
        this.changeDetector.detach();
    }


    wait() {
        this.state.wait = true;
        // tslint:disable-next-line: no-string-literal
        if (!this.changeDetector['destroyed']) {
            this.changeDetector.detectChanges();
        }
    }
    show() {
        this.state.wait = false;
        // tslint:disable-next-line: no-string-literal
        if (!this.changeDetector['destroyed']) {
            this.changeDetector.detectChanges();
        }
    }
}