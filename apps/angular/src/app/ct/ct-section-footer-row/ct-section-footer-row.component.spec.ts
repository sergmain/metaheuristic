import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtSectionFooterRowComponent } from './ct-section-footer-row.component';

describe('CtSectionFooterRowComponent', () => {
  let component: CtSectionFooterRowComponent;
  let fixture: ComponentFixture<CtSectionFooterRowComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtSectionFooterRowComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtSectionFooterRowComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
