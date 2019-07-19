import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LoadStates } from '@app/enums/LoadStates';
import { StationsService, station, Station } from '@app/services/stations/stations.service';
import { Subscription } from 'rxjs';

@Component({
    selector: 'edit-station',
    templateUrl: './edit-station.component.pug',
    styleUrls: ['./edit-station.component.scss']
})

export class EditStationComponent implements OnInit {
    readonly states = LoadStates;
    currentState: LoadStates = LoadStates.firstLoading;

    station: Station;
    // TODO: нетипичный ответ от сервера - нотификейшена не будет
    response: station.get.Response;

    constructor(
        private location: Location,
        private route: ActivatedRoute,
        private stationsService: StationsService,
        private router: Router,
    ) {}

    // tslint:disable-next-line: typedef
    ngOnInit() {
        const subscribe: Subscription = this.stationsService.station
            .get(this.route.snapshot.paramMap.get('id'))
            .subscribe((data: station.get.Response) => {
                this.response = data;
                this.station = data.station;
                this.currentState = this.states.show;
                subscribe.unsubscribe();
            });
    }
    // tslint:disable-next-line: typedef
    save() {
        this.currentState = this.states.wait;
        const subscribe: Subscription = this.stationsService.station
            .form(this.station)
            .subscribe(() => {
                this.router.navigate(['/launchpad', 'stations']);
                subscribe.unsubscribe();
            });
    }
    // tslint:disable-next-line: typedef
    cancel() {
        this.location.back();
    }

}