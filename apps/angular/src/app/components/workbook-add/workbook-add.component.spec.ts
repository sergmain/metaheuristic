import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { WorkbookAddComponent } from './workbook-add.component';

describe('WorkbookAddComponent', () => {
  let component: WorkbookAddComponent;
  let fixture: ComponentFixture<WorkbookAddComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ WorkbookAddComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(WorkbookAddComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
