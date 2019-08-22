import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AtlasExperimentMetricsComponent } from './atlas-experiment-metrics.component';

describe('AtlasExperimentMetricsComponent', () => {
  let component: AtlasExperimentMetricsComponent;
  let fixture: ComponentFixture<AtlasExperimentMetricsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AtlasExperimentMetricsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AtlasExperimentMetricsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
