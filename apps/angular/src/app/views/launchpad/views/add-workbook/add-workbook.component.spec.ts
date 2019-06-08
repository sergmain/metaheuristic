import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AddWorkbookComponent } from './add-workbook.component';

describe('AddWorkbookComponent', () => {
  let component: AddWorkbookComponent;
  let fixture: ComponentFixture<AddWorkbookComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AddWorkbookComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AddWorkbookComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
