import {
    Component,
    OnInit,
    ViewChild
} from '@angular/core';
import {
    StationsService
} from '@app/services/stations/stations.service';
import {
    MatTableDataSource,
    MatButton
} from '@angular/material';
import {
    StationResponse,
    DefaultItemsResponse
} from '@app/models';
import {
    LoadStates
} from '@app/enums/LoadStates';
import {
    Subscription
} from 'rxjs';


@Component({
    // tslint:disable-next-line: component-selector
    selector: 'stations-view',
    templateUrl: './stations.component.pug',
    styleUrls: ['./stations.component.scss']
})

export class StationsComponent implements OnInit {
    readonly states = LoadStates;
    currentState: LoadStates = this.states.loading;

    response: DefaultItemsResponse.Response;

    dataSource = new MatTableDataSource < StationResponse.Station > ([]);
    // tslint:disable-next-line: typedef
    columnsToDisplay = ['id', 'ip', 'description', 'activeTime', 'bts'];
    // tslint:disable-next-line: typedef
    secondColumnsToDisplay = ['empty', 'env'];

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;

    constructor(
        private stationsService: StationsService
    ) {}

    // tslint:disable-next-line: typedef
    applyFilter(filterValue: string) {
        this.dataSource.filter = filterValue.trim().toLowerCase();
    }
    // tslint:disable-next-line: typedef
    ngOnInit() {
        this.getStations(0);
    }
    // tslint:disable-next-line: typedef
    next() {
        this.getStations(this.response.items.number + 1);
    }
    // tslint:disable-next-line: typedef
    prev() {
        this.getStations(this.response.items.number - 1);
    }
    // tslint:disable-next-line: typedef
    getStations(page) {
        const subscribe: Subscription = this.stationsService.stations.get(page)
            .subscribe((data: DefaultItemsResponse.Response) => {
                const items: (StationResponse.Station)[] = data.items.content || [];
                if (items.length) {
                    this.dataSource = new MatTableDataSource(items);
                    this.currentState = this.states.show;
                    this.prevTable.disabled = data.items.first;
                    this.nextTable.disabled = data.items.last;
                } else {
                    this.currentState = this.states.empty;
                }
                subscribe.unsubscribe();
            });
    }
    // tslint:disable-next-line: typedef
    // TODO: визуально не удаляются
    delete(id) {
        this.stationsService.station.delete(id)
            .subscribe(() => {
                this.getStations(0);
            });
    }
}