import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AtlasExperimentFeatureProgressComponent } from './atlas-experiment-feature-progress.component';

describe('AtlasExperimentFeatureProgressComponent', () => {
  let component: AtlasExperimentFeatureProgressComponent;
  let fixture: ComponentFixture<AtlasExperimentFeatureProgressComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AtlasExperimentFeatureProgressComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AtlasExperimentFeatureProgressComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
