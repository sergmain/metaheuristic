import { Component, OnInit, ViewChild } from '@angular/core';
import { Station, StationsService } from '@app/services/stations/stations.service';
import { MatPaginator, MatTableDataSource } from '@angular/material';

@Component({
    selector: 'stations-view',
    templateUrl: './stations.component.html',
    styleUrls: ['./stations.component.scss']
})

export class StationsComponent implements OnInit {
    dataSource = new MatTableDataSource<Station>([]);
    columnsToDisplay = ['id', 'ipAddress', 'description', 'environment', 'activeTime', 'bts'];
    constructor(private stationsService: StationsService) { }

    @ViewChild(MatPaginator) paginator: MatPaginator;

    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase();
    }

    ngOnInit() {
        this.dataSource = new MatTableDataSource<Station>(this.stationsService.getStations());
        this.dataSource.paginator = this.paginator;
    }
}