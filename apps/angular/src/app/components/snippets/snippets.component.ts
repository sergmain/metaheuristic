import { Component, OnInit, ViewChild } from '@angular/core';
import { MatButton, MatTableDataSource, MatDialog } from '@angular/material';
import { CtTableComponent } from '@src/app/ct/ct-table/ct-table.component';
import { LoadStates } from '@app/enums/LoadStates';
import { SnippetsService, snippets, Snippet } from '@app/services/snippets/snippets.service';
import { Subscription } from 'rxjs';
import { ConfirmationDialogMethod } from '@app/components/app-dialog-confirmation/app-dialog-confirmation.component';

@Component({
    // tslint:disable-next-line: component-selector
    selector: 'snippets-view',
    templateUrl: './snippets.component.pug',
    styleUrls: ['./snippets.component.scss']
})

export class SnippetsComponent implements OnInit {
    readonly states: any = LoadStates;
    currentState: LoadStates = this.states.loading;
    response: snippets.get.Response;
    dataSource = new MatTableDataSource < Snippet > ([]);
    columnsToDisplay: string[] = ['code', 'type', 'params', 'bts'];
    deletedSnippets: (Snippet)[] = [];

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;

    constructor(
        private snippetsService: SnippetsService,
        private dialog: MatDialog
    ) {}

    // TODO: add pageale
    // TODO: переделать вид загрузки

    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase();
    }
    ngOnInit() {
        this.getSnippets(0);
    }

    getSnippets(page) {
        // TODO: response не содержит pageable
        // TODO: листание
        this.snippetsService.snippets.get(page)
            .subscribe((response: snippets.get.Response) => {
                this.response = response;
                const items: Snippet[] = response.snippets || [];
                if (items.length) {
                    this.dataSource = new MatTableDataSource(items);
                    this.currentState = this.states.show;
                } else {
                    this.currentState = this.states.empty;
                }
            });
    }

    @ConfirmationDialogMethod({
        question: (snippet: Snippet): string =>
            `Do you want to delete Snippet\xa0#${snippet.id}`,
        rejectTitle: 'Cancel',
        resolveTitle: 'Delete'
    })
    delete(snippet: Snippet) {
        this.deletedSnippets.push(snippet);
        const subscribe: Subscription = this.snippetsService.snippet
            .delete(snippet.id)
            .subscribe(
                () => {
                    // this.getSnippets(0);
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                });
    }

    next() {
        // this.updateTable(this...items.number + 1);
    }

    prev() {
        // this.updateTable(this...items.number - 1);
    }
}