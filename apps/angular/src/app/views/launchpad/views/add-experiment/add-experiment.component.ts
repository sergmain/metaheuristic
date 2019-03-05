import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { Component, OnInit } from '@angular/core';
import { Location } from '@angular/common'

@Component({
  selector: 'add-experiment',
  templateUrl: './add-experiment.component.html',
  styleUrls: ['./add-experiment.component.scss']
})
export class AddExperimentComponent implements OnInit {

  constructor(private location: Location) {}

  ngOnInit() {
  }
  cancel() {
    this.location.back();
  }
  create() {

  }
}
