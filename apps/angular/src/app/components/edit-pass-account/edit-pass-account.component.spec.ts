import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { EditPassAccountComponent } from './edit-pass-account.component';

describe('EditPassAccountComponent', () => {
  let component: EditPassAccountComponent;
  let fixture: ComponentFixture<EditPassAccountComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ EditPassAccountComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(EditPassAccountComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
