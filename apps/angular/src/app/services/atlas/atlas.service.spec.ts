import { TestBed } from '@angular/core/testing';

import { AtlasService } from './atlas.service';

describe('AtlasService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: AtlasService = TestBed.get(AtlasService);
    expect(service).toBeTruthy();
  });
});
