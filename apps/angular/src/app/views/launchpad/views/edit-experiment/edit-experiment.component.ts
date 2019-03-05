import { Component, OnInit, ViewChild } from '@angular/core';
import { Location } from '@angular/common'
import { MatTableDataSource } from '@angular/material';
import { Metadata, Experiment, ExperimentsService } from '@app/services/experiments/experiments.service'
import { ActivatedRoute } from '@angular/router'
import { animate, state, style, transition, trigger } from '@angular/animations';

@Component({
    selector: 'edit-experiment',
    templateUrl: './edit-experiment.component.html',
    styleUrls: ['./edit-experiment.component.scss'],
    animations: [
        trigger('editMetadataValue', [
            state('collapsed', style({ height: '0px', minHeight: '0', display: 'none', opacity: '0' })),
            state('expanded', style({ height: '*', opacity: '1' })),
            transition('expanded <=> collapsed', animate('225ms cubic-bezier(0.4, 0.0, 0.2, 1)')),
        ])
    ]
})
export class EditExperimentComponent implements OnInit {
    dataSource = new MatTableDataSource([]);

    columnsToDisplay = ['key', 'value', 'bts'];

    snippets: any = false;
    experiment: Experiment;
    currentEditMetadata: Metadata | null;

    newMetadata: Metadata = new Metadata('', '')

    constructor(
        private route: ActivatedRoute,
        private experimentsService: ExperimentsService,
        private location: Location
    ) {}

    ngOnInit() {
        const id = this.route.snapshot.paramMap.get('id');
        this.experiment = this.experimentsService.getExperiment(id)
        this.dataSource = new MatTableDataSource(this.experiment.metadatas);
    }
    save() {
        this.location.back();
    }
    cancel() {
        this.location.back();
    }

    create() {

    }

    add() {
        let key = this.newMetadata.key.trim()
        let value = this.newMetadata.value.trim()
        if (key == '' || value == '') return false;
        this.experiment.metadatas.unshift(new Metadata(key, value))
        this.dataSource = new MatTableDataSource(this.experiment.metadatas);
        this.newMetadata = new Metadata('', '')

    }

    fill() {
        this.experiment.metadatas = this.experimentsService.setDefault(this.experiment.metadatas)
        this.dataSource = new MatTableDataSource(this.experiment.metadatas);
    }

    edit(el) {
        if (this.currentEditMetadata == el) return false;
        el.newValue = el.value
        this.currentEditMetadata = el
    }

    change(metadata, event) {
        event.stopPropagation()
        this.currentEditMetadata = null
        metadata.value = metadata.newValue
        delete metadata.newValue
    }

    delete(metadata, event) {
        event.stopPropagation()
        const i = this.experiment.metadatas.indexOf(metadata)
        this.experiment.metadatas.splice(i, 1)
        this.dataSource = new MatTableDataSource(this.experiment.metadatas);
    }
}