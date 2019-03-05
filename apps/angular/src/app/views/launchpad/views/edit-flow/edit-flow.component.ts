import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common'
import { Flow, Instance, FlowsService } from '@app/services/flows/flows.service';
import { ActivatedRoute } from '@angular/router'

@Component({
    selector: 'edit-flow',
    templateUrl: './edit-flow.component.html',
    styleUrls: ['./edit-flow.component.scss']
})

export class EditFlowComponent implements OnInit {

    id
    flow: Flow = new Flow({})

    constructor(
        private location: Location,
        private route: ActivatedRoute,
        private flowsService: FlowsService
    ) {}

    ngOnInit() {
        this.id = this.route.snapshot.paramMap.get('flowId');
        this.flow = this.flowsService.getFlow(this.id)
    }

    cancel() {
        this.location.back();
    }

    save() {
        this.flow = this.flowsService.getFlow(this.id)
    }

    validate() {

    }
}