import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common'

@Component({
  selector: 'add-flow',
  templateUrl: './add-flow.component.html',
  styleUrls: ['./add-flow.component.scss']
})
export class AddFlowComponent implements OnInit {

  ngOnInit() {}

  constructor(private location: Location) {}

  cancel() {
    this.location.back();
  }
  create() {

  }
}
