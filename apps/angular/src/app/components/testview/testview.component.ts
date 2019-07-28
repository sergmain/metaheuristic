import { Component, OnInit } from '@angular/core';
import { PlotComponent } from 'angular-plotly.js';

@Component({
    selector: 'testview',
    templateUrl: './testview.component.pug',
    styleUrls: ['./testview.component.scss']
})
export class TestViewComponent implements OnInit {

    plotly: PlotComponent;
    constructor() {}

    ngOnInit() {}

}