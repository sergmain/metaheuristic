import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { NavLaunchpadComponent } from './nav-launchpad.component';

describe('NavLaunchpadComponent', () => {
  let component: NavLaunchpadComponent;
  let fixture: ComponentFixture<NavLaunchpadComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ NavLaunchpadComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(NavLaunchpadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
