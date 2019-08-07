import { Component, EventEmitter, Input, OnChanges, OnInit, Output } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { MetricsResult, MetricsEntity } from '@services/atlas';
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
    columnsToDisplay: string[] = [];

    constructor() {}

    ngOnInit() {
        if (this.metricsResult) { this.update(); }
    }

    ngOnChanges() {
        if (this.metricsResult) { this.update(); }
    }
    private update() {
        const newColumnsToDisplay: string[] = [].concat(this.metricsResult.metricNames, ['params']);
        const newDataSource: any = [];

        this.metricsResult.metrics.forEach((item: MetricsEntity) => {
            const values: string[] = [].concat(item.values, [item.params]);
            const row: any = {};
            values.forEach((elem: string, index: number) => {
                row[newColumnsToDisplay[index]] = elem;
            });
            newDataSource.push(row);
        });

        this.columnsToDisplay = newColumnsToDisplay;
        this.dataSource = new MatTableDataSource(newDataSource);
    }

    drawPlot() {
        this.draw.emit('draw');
    }
}