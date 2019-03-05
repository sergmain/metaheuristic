import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AddFlowComponent } from './add-flow.component';

describe('AddFlowComponent', () => {
  let component: AddFlowComponent;
  let fixture: ComponentFixture<AddFlowComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AddFlowComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AddFlowComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
