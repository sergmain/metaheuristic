import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtSectionBodyRowComponent } from './ct-section-body-row.component';

describe('CtSectionBodyRowComponent', () => {
  let component: CtSectionBodyRowComponent;
  let fixture: ComponentFixture<CtSectionBodyRowComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtSectionBodyRowComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtSectionBodyRowComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
