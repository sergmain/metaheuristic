import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common'

@Component({
  selector: 'add-plan',
  templateUrl: './add-plan.component.html',
  styleUrls: ['./add-plan.component.scss']
})
export class AddPlanComponent implements OnInit {

  ngOnInit() {}

  constructor(private location: Location) {}

  cancel() {
    this.location.back();
  }
  create() {

  }
}
