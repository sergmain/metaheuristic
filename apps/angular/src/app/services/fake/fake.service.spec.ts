import { TestBed } from '@angular/core/testing';

import { FakeService } from './fake.service';

describe('FakeService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: FakeService = TestBed.get(FakeService);
    expect(service).toBeTruthy();
  });
});
