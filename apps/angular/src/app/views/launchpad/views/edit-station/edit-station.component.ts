import {
    Component,
    OnInit
} from '@angular/core';
import {
    Location
} from '@angular/common';
import {
    Router,
    ActivatedRoute
} from '@angular/router';
import {
    StationsService
} from '@app/services/stations/stations.service';
import {
    StationResponse
} from '@app/models';
import {
    Subscription
} from 'rxjs';
import {
    LoadStates
} from '@app/enums/LoadStates';
@Component({
    // tslint:disable-next-line: component-selector
    selector: 'edit-station',
    templateUrl: './edit-station.component.pug',
    styleUrls: ['./edit-station.component.scss']
})

export class EditStationComponent implements OnInit {
    readonly states = LoadStates;
    currentState: LoadStates = LoadStates.firstLoading;

    station: StationResponse.Station;
    //TODO: нетипичный ответ от сервера - нотификейшена не будет
    response: StationResponse.Response;

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
            .subscribe((data: StationResponse.Response) => {
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