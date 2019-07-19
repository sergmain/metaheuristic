import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { EditWorkbookComponent } from './edit-workbook.component';

describe('EditWorkbookComponent', () => {
  let component: EditWorkbookComponent;
  let fixture: ComponentFixture<EditWorkbookComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ EditWorkbookComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(EditWorkbookComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
