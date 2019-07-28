import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AtlasExperimentsComponent } from './atlas-experiments.component';

describe('AtlasExperimentsComponent', () => {
  let component: AtlasExperimentsComponent;
  let fixture: ComponentFixture<AtlasExperimentsComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AtlasExperimentsComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AtlasExperimentsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
