import { Component, OnInit, ViewChild } from '@angular/core';
import { Plan, Instance, PlansService } from '@app/services/plans/plans.service';
import { MatPaginator, MatTableDataSource } from '@angular/material';
import { ActivatedRoute } from '@angular/router';


@Component({
    selector: 'instances-view',
    templateUrl: './instances.component.html',
    styleUrls: ['./instances.component.scss']
})

export class InstancesComponent implements OnInit {
    dataSource = new MatTableDataSource < Instance > ([])
    id: string = ''
    columnsToDisplay = [
        "id",
        "planCode",
        "inputPoolCodes",
        "createdOn",
        "isPlanValid",
        "isWorkbookValid",
        "execState",
        "completedOn"
    ]

    constructor(
        private route: ActivatedRoute,
        private planService: PlansService
    ) {}

    @ViewChild(MatPaginator) paginator: MatPaginator;

    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase()
    }

    ngOnInit() {
        this.getInstances()
        this.dataSource.paginator = this.paginator
        this.id = this.route.snapshot.paramMap.get('planId');
    }
    getInstances() {
        const id = this.route.snapshot.paramMap.get('planId');
        this.dataSource = new MatTableDataSource < Instance > (this.planService.getInstancesByPlanId(id))
        console.log(this.planService.getInstancesByPlanId(id))
    }
}