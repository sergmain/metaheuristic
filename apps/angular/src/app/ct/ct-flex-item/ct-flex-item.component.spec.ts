import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtFlexItemComponent } from './ct-flex-item.component';

describe('CtFlexItemComponent', () => {
  let component: CtFlexItemComponent;
  let fixture: ComponentFixture<CtFlexItemComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtFlexItemComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtFlexItemComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
