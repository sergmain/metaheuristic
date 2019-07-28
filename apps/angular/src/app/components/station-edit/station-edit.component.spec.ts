import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { StationEditComponent } from './station-edit.component';

describe('StationEditComponent', () => {
  let component: StationEditComponent;
  let fixture: ComponentFixture<StationEditComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ StationEditComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(StationEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
