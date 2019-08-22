import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtSectionComponent } from './ct-section.component';

describe('CtSectionComponent', () => {
  let component: CtSectionComponent;
  let fixture: ComponentFixture<CtSectionComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtSectionComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtSectionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
