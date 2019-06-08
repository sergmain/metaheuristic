import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { WorkbooksComponent } from './workbooks.component';

describe('WorkbooksComponent', () => {
  let component: WorkbooksComponent;
  let fixture: ComponentFixture<WorkbooksComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ WorkbooksComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(WorkbooksComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
