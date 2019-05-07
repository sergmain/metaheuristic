import { Component, OnInit, ViewChild } from '@angular/core';
import { Plan, PlansService } from '@app/services/plans/plans.service';
import { MatPaginator, MatTableDataSource } from '@angular/material';

@Component({
    selector: 'plans-view',
    templateUrl: './plans.component.html',
    styleUrls: ['./plans.component.scss']
})

export class PlansComponent implements OnInit {
    dataSource = new MatTableDataSource < Plan > ([]);
    columnsToDisplay = ['id', 'codeOfPlan', 'createdOn', 'isValid', 'isLocked', 'bts'];
    constructor(private plansService: PlansService) {}

    @ViewChild(MatPaginator) paginator: MatPaginator;

    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase();
    }

    ngOnInit() {
        this.dataSource = new MatTableDataSource < Plan > (this.plansService.getPlans());
        this.dataSource.paginator = this.paginator;
    }
}