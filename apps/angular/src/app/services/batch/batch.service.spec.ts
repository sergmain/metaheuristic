import { TestBed } from '@angular/core/testing';

import { BatchService } from './batch.service';

describe('BatchService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: BatchService = TestBed.get(BatchService);
    expect(service).toBeTruthy();
  });
});
