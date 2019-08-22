import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { NavPilotComponent } from './nav-pilot.component';

describe('NavPilotComponent', () => {
  let component: NavPilotComponent;
  let fixture: ComponentFixture<NavPilotComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ NavPilotComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(NavPilotComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
