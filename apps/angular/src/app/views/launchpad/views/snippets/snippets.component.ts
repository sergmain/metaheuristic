
import { Component, OnInit, ViewChild } from '@angular/core';
import { Snippet, SnippetsService } from '@app/services/snippets/snippets.service';
import { MatPaginator, MatTableDataSource } from '@angular/material';

@Component({
    selector: 'snippets-view',
    templateUrl: './snippets.component.html',
    styleUrls: ['./snippets.component.scss']
})

export class SnippetsComponent implements OnInit {
    dataSource = new MatTableDataSource<Snippet>([]);
    columnsToDisplay = [
        "name",
        "version",
        "type",
        "environment",
        "params",
        "isSigned"
    ];
    constructor(private snippetsService: SnippetsService) { }

    @ViewChild(MatPaginator) paginator: MatPaginator;

    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase();
    }

    ngOnInit() {
        this.dataSource = new MatTableDataSource<Snippet>(this.snippetsService.getSnippets());
        this.dataSource.paginator = this.paginator;
    }
}


