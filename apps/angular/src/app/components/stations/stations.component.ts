import { Component, OnInit, ViewChild } from '@angular/core';
import { MatButton, MatTableDataSource, MatDialog } from '@angular/material';
import { LoadStates } from '@app/enums/LoadStates';
import { StationsService, stations, Station, ListItemStation } from '@app/services/stations/stations.service';
import { Subscription } from 'rxjs';
import { ConfirmationDialogMethod } from '@app/components/app-dialog-confirmation/app-dialog-confirmation.component';

@Component({
    selector: 'stations-view',
    templateUrl: './stations.component.pug',
    styleUrls: ['./stations.component.scss']
})

export class StationsComponent implements OnInit {
    readonly states = LoadStates;
    currentState: LoadStates = this.states.loading;

    response: stations.get.Response;

    dataSource = new MatTableDataSource < ListItemStation > ([]);
    columnsToDisplay: string[] = ['id', 'ip', 'description', 'isActive', 'isBlacklisted', 'lastSeen', 'bts'];
    secondColumnsToDisplay = ['empty', 'env'];

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;

    constructor(
        private dialog: MatDialog,
        private stationsService: StationsService
    ) {}

    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase();
    }

    ngOnInit() {
        this.getStations(0);
    }

    next() {
        this.getStations(this.response.items.number + 1);
    }

    prev() {
        this.getStations(this.response.items.number - 1);
    }

    getStations(page) {
        const subscribe: Subscription = this.stationsService.stations.get(page)
            .subscribe(
                (response: stations.get.Response) => {
                    const items: ListItemStation[] = response.items.content || [];
                    if (items.length) {
                        this.dataSource = new MatTableDataSource(items);
                        this.currentState = this.states.show;
                        this.prevTable.disabled = response.items.first;
                        this.nextTable.disabled = response.items.last;
                    } else {
                        this.currentState = this.states.empty;
                    }
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }
    // tslint:disable-next-line: typedef
    // TODO: визуально не удаляются

    @ConfirmationDialogMethod({
        question: (station: ListItemStation): string =>
            `Do you want to delete Station\xa0#${station.station.id}`,
        rejectTitle: 'Cancel',
        resolveTitle: 'Delete'
    })
    delete(station: ListItemStation) {
        const subscribe: Subscription = this.stationsService.station.delete(station.station.id)
            .subscribe(
                () => {
                    this.getStations(0);
                },
                () => {},
                () => {
                    subscribe.unsubscribe();
                }
            );
    }
}