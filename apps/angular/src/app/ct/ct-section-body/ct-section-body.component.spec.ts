import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtSectionBodyComponent } from './ct-section-body.component';

describe('CtSectionBodyComponent', () => {
  let component: CtSectionBodyComponent;
  let fixture: ComponentFixture<CtSectionBodyComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtSectionBodyComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtSectionBodyComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
