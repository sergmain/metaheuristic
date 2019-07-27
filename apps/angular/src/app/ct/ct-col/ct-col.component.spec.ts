import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtColComponent } from './ct-col.component';

describe('CtColComponent', () => {
  let component: CtColComponent;
  let fixture: ComponentFixture<CtColComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtColComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtColComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
