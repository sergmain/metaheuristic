import { Component, OnInit, ViewChild } from '@angular/core';
import { Flow, Instance, FlowsService } from '@app/services/flows/flows.service';
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
        "flowCode",
        "inputPoolCodes",
        "createdOn",
        "isFlowValid",
        "isFlowInstanceValid",
        "execState",
        "completedOn"
    ]

    constructor(
        private route: ActivatedRoute,
        private flowService: FlowsService
    ) {}

    @ViewChild(MatPaginator) paginator: MatPaginator;

    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase()
    }

    ngOnInit() {
        this.getInstances()
        this.dataSource.paginator = this.paginator
        this.id = this.route.snapshot.paramMap.get('flowId');
    }
    getInstances() {
        const id = this.route.snapshot.paramMap.get('flowId');
        this.dataSource = new MatTableDataSource < Instance > (this.flowService.getInstancesByFlowId(id))
        console.log(this.flowService.getInstancesByFlowId(id))
    }
}