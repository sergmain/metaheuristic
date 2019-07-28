import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { PlansArchiveComponent } from './plans-archive.component';

describe('PlansArchiveComponent', () => {
  let component: PlansArchiveComponent;
  let fixture: ComponentFixture<PlansArchiveComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ PlansArchiveComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(PlansArchiveComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
