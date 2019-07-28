import { Location } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { LoadStates } from '@app/enums/LoadStates';
import { StationsService, station, Station } from '@app/services/stations/stations.service';
import { Subscription } from 'rxjs';
import { DefaultResponse } from '@src/app/models';

@Component({
    selector: 'station-edit',
    templateUrl: './station-edit.component.pug',
    styleUrls: ['./station-edit.component.scss']
})

export class StationEditComponent implements OnInit {
    readonly states = LoadStates;
    currentState: LoadStates = LoadStates.firstLoading;

    station: Station;
    // TODO: нетипичный ответ от сервера - нотификейшена не будет
    response: station.get.Response;
    formResponse: DefaultResponse;
    constructor(
        private location: Location,
        private route: ActivatedRoute,
        private stationsService: StationsService,
        private router: Router,
    ) {}

    ngOnInit() {
        const subscribe: Subscription = this.stationsService.station
            .get(this.route.snapshot.paramMap.get('id'))
            .subscribe(
                (data: station.get.Response) => {
                    this.response = data;
                    this.station = data.station;
                    this.currentState = this.states.show;
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }
    save() {
        this.currentState = this.states.wait;
        const subscribe: Subscription = this.stationsService.station
            .form(this.station)
            .subscribe(
                (response: DefaultResponse) => {
                    this.formResponse = response;
                    this.router.navigate(['/launchpad', 'stations']);
                },
                () => {},
                () => subscribe.unsubscribe()
            );
    }

    cancel() {
        this.location.back();
    }
}