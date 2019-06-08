import {
    Component,
    OnInit,
    OnDestroy,
    ChangeDetectorRef
} from '@angular/core';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'ct-wrap-block',
    templateUrl: './ct-wrap-block.component.pug',
    styleUrls: ['./ct-wrap-block.component.scss']
})
export class CtWrapBlockComponent implements OnInit, OnDestroy {

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