import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AtlasExperimentInfoComponent } from './atlas-experiment-info.component';

describe('AtlasInfoComponent', () => {
  let component: AtlasExperimentInfoComponent;
  let fixture: ComponentFixture<AtlasExperimentInfoComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AtlasExperimentInfoComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AtlasExperimentInfoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
