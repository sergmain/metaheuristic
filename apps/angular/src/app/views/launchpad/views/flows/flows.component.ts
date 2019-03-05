import { Component, OnInit, ViewChild } from '@angular/core';
import { Flow, FlowsService } from '@app/services/flows/flows.service';
import { MatPaginator, MatTableDataSource } from '@angular/material';

@Component({
    selector: 'flows-view',
    templateUrl: './flows.component.html',
    styleUrls: ['./flows.component.scss']
})

export class FlowsComponent implements OnInit {
    dataSource = new MatTableDataSource < Flow > ([]);
    columnsToDisplay = ['id', 'codeOfFlow', 'createdOn', 'isValid', 'isLocked', 'bts'];
    constructor(private flowsService: FlowsService) {}

    @ViewChild(MatPaginator) paginator: MatPaginator;

    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase();
    }

    ngOnInit() {
        this.dataSource = new MatTableDataSource < Flow > (this.flowsService.getFlows());
        this.dataSource.paginator = this.paginator;
    }
}