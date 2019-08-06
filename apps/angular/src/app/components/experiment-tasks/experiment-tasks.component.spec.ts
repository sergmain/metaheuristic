import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ExperimentTasksComponent } from './experiment-tasks.component';

describe('ExperimentTasksComponent', () => {
  let component: ExperimentTasksComponent;
  let fixture: ComponentFixture<ExperimentTasksComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ExperimentTasksComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ExperimentTasksComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
