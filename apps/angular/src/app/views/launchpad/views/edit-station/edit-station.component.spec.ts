import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { EditStationComponent } from './edit-station.component';

describe('EditStationComponent', () => {
  let component: EditStationComponent;
  let fixture: ComponentFixture<EditStationComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ EditStationComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(EditStationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
