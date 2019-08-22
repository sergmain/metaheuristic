import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AtlasExperimentTasksComponent } from './atlas-experiment-tasks.component';

describe('AtlasExperimentTasksComponent', () => {
  let component: AtlasExperimentTasksComponent;
  let fixture: ComponentFixture<AtlasExperimentTasksComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AtlasExperimentTasksComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AtlasExperimentTasksComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
