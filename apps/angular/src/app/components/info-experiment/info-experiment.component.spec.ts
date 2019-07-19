import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { InfoExperimentComponent } from './info-experiment.component';

describe('InfoExperimentComponent', () => {
  let component: InfoExperimentComponent;
  let fixture: ComponentFixture<InfoExperimentComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ InfoExperimentComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(InfoExperimentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
