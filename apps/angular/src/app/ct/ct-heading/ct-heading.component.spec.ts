import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtHeadingComponent } from './ct-heading.component';

describe('CtHeadingComponent', () => {
  let component: CtHeadingComponent;
  let fixture: ComponentFixture<CtHeadingComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtHeadingComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtHeadingComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
