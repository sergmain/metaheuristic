import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtHintComponent } from './ct-hint.component';

describe('CtHintComponent', () => {
  let component: CtHintComponent;
  let fixture: ComponentFixture<CtHintComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtHintComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtHintComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
