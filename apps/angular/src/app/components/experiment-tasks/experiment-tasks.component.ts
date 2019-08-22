import { Component, OnInit, Input, ViewChild, OnChanges } from '@angular/core';
import { MatTableDataSource, MatButton } from '@angular/material';
import { TasksResult, Task, ExperimentsService, response } from '@app/services/experiments/';
import { Subscription } from 'rxjs';
import { CtWrapBlockComponent } from '@app/ct';
import { CtTableComponent } from '@app/ct';

@Component({
    selector: 'experiment-tasks',
    templateUrl: './experiment-tasks.component.pug',
    styleUrls: ['./experiment-tasks.component.scss']
})
export class ExperimentTasksComponent implements OnInit, OnChanges {

    @Input() tasks: TasksResult;
    @ViewChild('nextTable') nextTable: MatButton;
    @ViewChild('prevTable') prevTable: MatButton;
    @ViewChild('table') table: CtTableComponent;
    @ViewChild('consoleView') consoleView: CtWrapBlockComponent;

    consolePartResponse: response.experiment.FeatureProgressConsolePart;

    currentTask: Task;
    dataSource = new MatTableDataSource < any > ([]);
    columnsToDisplay: string[] = ['id', 'col1', 'col2'];

    constructor(
        private experimentsService: ExperimentsService
    ) {}

    ngOnInit() {}

    ngOnChanges() {
        this.dataSource = new MatTableDataSource(this.tasks.items.content || []);
    }

    featureProgressConsolePart(taskId: string) {
        this.consoleView.wait();
        const subscribe: Subscription = this.experimentsService.experiment
            .featureProgressConsolePart(taskId)
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
}