import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtSectionContentComponent } from './ct-section-content.component';

describe('CtSectionContentComponent', () => {
  let component: CtSectionContentComponent;
  let fixture: ComponentFixture<CtSectionContentComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtSectionContentComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtSectionContentComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
