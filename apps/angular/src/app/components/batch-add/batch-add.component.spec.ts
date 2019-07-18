import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { BatchAddComponent } from './batch-add.component';

describe('BatchAddComponent', () => {
  let component: BatchAddComponent;
  let fixture: ComponentFixture<BatchAddComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ BatchAddComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(BatchAddComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
