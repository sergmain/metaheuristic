import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { MetricsResult } from '@services/atlas';
@Component({
    selector: 'atlas-experiment-metrics',
    templateUrl: './atlas-experiment-metrics.component.pug',
    styleUrls: ['./atlas-experiment-metrics.component.scss']
})
export class AtlasExperimentMetricsComponent implements OnInit, OnChanges {
    @Output() draw = new EventEmitter < string > ();

    @Input() metricsResult: MetricsResult;
    @Input() dataGraph: any;
    @Input() canDraw: boolean;

    dataSource = new MatTableDataSource < any > ([]);
    columnsToDisplay: string[] = ['values', 'params'];

    constructor() {}

    ngOnInit() {
        if (this.metricsResult) { this.update(); }
    }

    ngOnChanges() {
        if (this.metricsResult) { this.update(); }
    }
    private update() {
        this.dataSource = new MatTableDataSource(this.metricsResult.metrics || []);
    }

    drawPlot() {
        this.draw.emit('draw');
    }
}