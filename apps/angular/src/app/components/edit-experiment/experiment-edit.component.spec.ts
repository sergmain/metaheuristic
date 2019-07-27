import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ExperimentEditComponent } from './experiment-edit.component';

describe('ExperimentEditComponent', () => {
  let component: ExperimentEditComponent;
  let fixture: ComponentFixture<ExperimentEditComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ExperimentEditComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ExperimentEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
