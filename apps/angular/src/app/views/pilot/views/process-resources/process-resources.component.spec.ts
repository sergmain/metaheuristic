import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ProcessResourcesComponent } from './process-resources.component';

describe('ProcessResourcesComponent', () => {
  let component: ProcessResourcesComponent;
  let fixture: ComponentFixture<ProcessResourcesComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ProcessResourcesComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ProcessResourcesComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
