import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtSectionHeaderRowComponent } from './ct-section-header-row.component';

describe('CtSectionHeaderRowComponent', () => {
  let component: CtSectionHeaderRowComponent;
  let fixture: ComponentFixture<CtSectionHeaderRowComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtSectionHeaderRowComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtSectionHeaderRowComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
