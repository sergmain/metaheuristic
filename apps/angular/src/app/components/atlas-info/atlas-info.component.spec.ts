import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AtlasInfoComponent } from './atlas-info.component';

describe('AtlasInfoComponent', () => {
  let component: AtlasInfoComponent;
  let fixture: ComponentFixture<AtlasInfoComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AtlasInfoComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AtlasInfoComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
