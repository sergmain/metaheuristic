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
    columnsToDisplay: string[] = ['values', 'params'];

    constructor() {}

    ngOnInit() {
        if (this.metricsResult) { this.update(); }
    }

    ngOnChanges() {
        if (this.metricsResult) { this.update(); }
    }
    private update() {
        const columns: string[] = [].concat(this.metricsResult.metricNames, ['params']);
        const data = [];
        this.metricsResult.metrics.forEach((item) => {
            const values = [].concat(item.values, [item.params]);
            const row = {}
            values.forEach((elem, index) => {
                row[columns[index]] = elem
            });
            data.push(row);
        });

        this.columnsToDisplay = columns;
        console.log(data, columns);
        this.dataSource = new MatTableDataSource(data);
    }

    drawPlot() {
        this.draw.emit('draw');
    }
}