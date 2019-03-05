import { Component, OnInit, ViewChild } from '@angular/core';
import { MatPaginator, MatTableDataSource } from '@angular/material';
import { Experiment, ExperimentsService } from '@app/services/experiments/experiments.service'

@Component({
    selector: 'experiments-view',
    templateUrl: './experiments.component.html',
    styleUrls: ['./experiments.component.scss']
})

export class ExperimentsComponent implements OnInit {
    dataSource = new MatTableDataSource < Experiment > ([]);
    columnsToDisplay = ['id', 'name', 'createdOn', 'bts'];
    constructor(private experimentsService: ExperimentsService) {}

    @ViewChild(MatPaginator) paginator: MatPaginator;

    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase();
    }

    ngOnInit() {
        this.dataSource = new MatTableDataSource < Experiment > (this.experimentsService.getExperiments());
        this.dataSource.paginator = this.paginator;
    }
}