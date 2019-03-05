import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtColsComponent } from './ct-cols.component';

describe('CtColsComponent', () => {
  let component: CtColsComponent;
  let fixture: ComponentFixture<CtColsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtColsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtColsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
