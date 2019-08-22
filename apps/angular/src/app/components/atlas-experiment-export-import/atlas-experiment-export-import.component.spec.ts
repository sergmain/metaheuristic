import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AtlasExperimentExportImportComponent } from './atlas-experiment-export-import.component';

describe('AtlasExperimentExportImportComponent', () => {
  let component: AtlasExperimentExportImportComponent;
  let fixture: ComponentFixture<AtlasExperimentExportImportComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AtlasExperimentExportImportComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AtlasExperimentExportImportComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
