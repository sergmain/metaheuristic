import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtTableComponent } from './ct-table.component';

describe('CtTableComponent', () => {
  let component: CtTableComponent;
  let fixture: ComponentFixture<CtTableComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtTableComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtTableComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
