import { TestBed } from '@angular/core/testing';

import { ArchivePlansService } from './archive-plans.service';

describe('ArchivePlansService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: ArchivePlansService = TestBed.get(ArchivePlansService);
    expect(service).toBeTruthy();
  });
});
