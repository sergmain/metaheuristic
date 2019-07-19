import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { EditExperimentComponent } from './edit-experiment.component';

describe('EditExperimentComponent', () => {
  let component: EditExperimentComponent;
  let fixture: ComponentFixture<EditExperimentComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ EditExperimentComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(EditExperimentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
