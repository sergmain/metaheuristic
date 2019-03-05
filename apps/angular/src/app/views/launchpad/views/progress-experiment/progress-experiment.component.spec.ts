import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ProgressExperimentComponent } from './progress-experiment.component';

describe('ProgressExperimentComponent', () => {
  let component: ProgressExperimentComponent;
  let fixture: ComponentFixture<ProgressExperimentComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ProgressExperimentComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ProgressExperimentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
