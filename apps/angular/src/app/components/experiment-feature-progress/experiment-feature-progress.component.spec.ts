import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ExperimentFeatureProgressComponent } from './experiment-feature-progress.component';

describe('ExperimentFeatureProgressComponent', () => {
  let component: ExperimentFeatureProgressComponent;
  let fixture: ComponentFixture<ExperimentFeatureProgressComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ExperimentFeatureProgressComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ExperimentFeatureProgressComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
