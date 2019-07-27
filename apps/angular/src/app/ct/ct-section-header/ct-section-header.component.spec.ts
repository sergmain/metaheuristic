import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtSectionHeaderComponent } from './ct-section-header.component';

describe('CtSectionHeaderComponent', () => {
  let component: CtSectionHeaderComponent;
  let fixture: ComponentFixture<CtSectionHeaderComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtSectionHeaderComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtSectionHeaderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
