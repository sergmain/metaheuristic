import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtPreComponent } from './ct-pre.component';

describe('CtPreComponent', () => {
  let component: CtPreComponent;
  let fixture: ComponentFixture<CtPreComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtPreComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtPreComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
