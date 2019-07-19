import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtWrapBlockComponent } from './ct-wrap-block.component';

describe('CtWrapBlockComponent', () => {
  let component: CtWrapBlockComponent;
  let fixture: ComponentFixture<CtWrapBlockComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtWrapBlockComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtWrapBlockComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
