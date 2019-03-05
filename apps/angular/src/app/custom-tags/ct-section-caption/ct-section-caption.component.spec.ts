import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtSectionCaptionComponent } from './ct-section-caption.component';

describe('CtSectionCaptionComponent', () => {
  let component: CtSectionCaptionComponent;
  let fixture: ComponentFixture<CtSectionCaptionComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtSectionCaptionComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtSectionCaptionComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
