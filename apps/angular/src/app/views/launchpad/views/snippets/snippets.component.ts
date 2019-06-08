import {
    Component,
    OnInit,
    ViewChild
} from '@angular/core';
import {
    SnippetsService
} from '@app/services/snippets/snippets.service';
import {
    MatButton,
    MatTableDataSource
} from '@angular/material';
import {
    SnippetsResponse
} from '@app/models/';
import {
    LoadStates
} from '@app/enums/LoadStates';
import {
    CtTableComponent
} from '@app/custom-tags/ct-table/ct-table.component';
import {
    Subscription
} from 'rxjs';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'snippets-view',
    templateUrl: './snippets.component.pug',
    styleUrls: ['./snippets.component.scss']
})

export class SnippetsComponent implements OnInit {
    readonly states: any = LoadStates;
    currentState: LoadStates = this.states.loading;
    response: SnippetsResponse.Response;
    dataSource = new MatTableDataSource < SnippetsResponse.Snippet > ([]);
    columnsToDisplay: (string)[] = [
        'name',
        'version',
        'type',
        'env',
        'params',
        'isSigned',
        'bts'
    ];
    deletedSnippets: (SnippetsResponse.Snippet)[] = [];

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;


    constructor(
        private snippetsService: SnippetsService
    ) {}

    // TODO: add pageale
    // TODO: переделать вид загрузки

    // tslint:disable-next-line: typedef
    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase();
    }
    // tslint:disable-next-line: typedef
    ngOnInit() {
        this.getSnippets(0);
    }
    // tslint:disable-next-line: typedef
    getSnippets(page) {
        // TODO: response не содержит pageable
        // TODO: листание
        this.snippetsService.snippets.get(page)
            .subscribe((response: SnippetsResponse.Response) => {
                this.response = response;
                const items: (SnippetsResponse.Snippet)[] = response.snippets || [];
                if (items.length) {
                    this.dataSource = new MatTableDataSource(items);
                    this.currentState = this.states.show;
                } else {
                    this.currentState = this.states.empty;
                }
            });
    }
    // tslint:disable-next-line: typedef
    delete(el) {
        this.deletedSnippets.push(el);
        const subscribe: Subscription = this.snippetsService.snippet
            .delete(el.id)
            .subscribe(() => {
                // this.getSnippets(0);
                subscribe.unsubscribe();
            });
    }
    // tslint:disable-next-line: typedef
    next() {
        // this.updateTable(this...items.number + 1);
    }
    // tslint:disable-next-line: typedef
    prev() {
        // this.updateTable(this...items.number - 1);
    }
}