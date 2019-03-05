import { Component, OnInit, ViewChild } from '@angular/core';
import { MatPaginator, MatTableDataSource } from '@angular/material';
import { Resource, ResourcesService } from '@app/services/resources/resources.service'

@Component({
    selector: 'resources-view',
    templateUrl: './resources.component.html', 
    styleUrls: ['./resources.component.scss'],
    providers: [ResourcesService]
})
export class ResourcesComponent implements OnInit {
    dataSource = new MatTableDataSource < Resource > ([]);
    columnsToDisplay = ["isValid", "uploadDate", "typeOfResource", "checksum", "code", "poolCode", "isManual", "filename", "storageUrl", "bts"];

    constructor(private resourcesService: ResourcesService) {}

    @ViewChild(MatPaginator) paginator: MatPaginator;

    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase();
    }

    ngOnInit() {
        this.dataSource = new MatTableDataSource < Resource > (this.resourcesService.getResources());
        this.dataSource.paginator = this.paginator;
    }
}