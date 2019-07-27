import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtFlexComponent } from './ct-flex.component';

describe('CtFlexComponent', () => {
  let component: CtFlexComponent;
  let fixture: ComponentFixture<CtFlexComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtFlexComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtFlexComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
