import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtSectionFooterComponent } from './ct-section-footer.component';

describe('CtSectionFooterComponent', () => {
  let component: CtSectionFooterComponent;
  let fixture: ComponentFixture<CtSectionFooterComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtSectionFooterComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtSectionFooterComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
