import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ExperimentMetricsComponent } from './experiment-metrics.component';

describe('ExperimentMetricsComponent', () => {
  let component: ExperimentMetricsComponent;
  let fixture: ComponentFixture<ExperimentMetricsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ExperimentMetricsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ExperimentMetricsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
