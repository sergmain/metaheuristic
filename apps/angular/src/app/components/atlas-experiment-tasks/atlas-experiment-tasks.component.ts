import { Component, OnInit, Input, Output, SimpleChanges, ViewChild, OnChanges, EventEmitter } from '@angular/core';
import { MatTableDataSource, MatButton } from '@angular/material';
import { AtlasService, Tasks, Task, response } from '@app/services/atlas/';
import { Subscription } from 'rxjs';
import { CtWrapBlockComponent } from '@app/ct';
import { CtTableComponent } from '@app/ct';

@Component({
    selector: 'atlas-experiment-tasks',
    templateUrl: './atlas-experiment-tasks.component.pug',
    styleUrls: ['./atlas-experiment-tasks.component.scss']
})
export class AtlasExperimentTasksComponent implements OnInit, OnChanges {

    @Input() tasks: Tasks;
    @Input() atlasId: string;

    @Output() nextPage = new EventEmitter < string > ();
    @Output() prevPage = new EventEmitter < string > ();

    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;
    @ViewChild('consoleView') consoleView: CtWrapBlockComponent;

    consolePartResponse: response.experiment.FeatureProgressConsolePart;
    featureProgressPartResponse: response.experiment.FeatureProgressPart;
    currentTask: Task;
    dataSource = new MatTableDataSource < any > ([]);
    columnsToDisplay: string[] = ['id', 'info', 'bts'];

    constructor(
        private atlasService: AtlasService
    ) {}

    ngOnInit() {
        this.dataSource = new MatTableDataSource(this.tasks.content || []);
    }

    ngOnChanges(changes: SimpleChanges) {
        this.dataSource = new MatTableDataSource(this.tasks.content || []);

        this.prevTable.disabled = this.tasks.first;
        this.nextTable.disabled = this.tasks.last;
    }

    featureProgressConsolePart(taskId: string) {
        this.consoleView.wait();
        const subscribe: Subscription = this.atlasService.experiment
            .featureProgressConsolePart(this.atlasId, taskId)
            .subscribe(
                (response: response.experiment.FeatureProgressConsolePart) => {
                    this.consolePartResponse = response;
                },
                () => {},
                () => {
                    this.consoleView.show();
                    subscribe.unsubscribe();
                },
            );
    }

    next() {
        this.nextPage.emit('next');
    }

    prev() {
        this.prevPage.emit('prev');
    }
}