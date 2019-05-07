import { TestBed } from '@angular/core/testing';

import { PlansService } from './plans.service';

describe('PlansService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: PlansService = TestBed.get(PlansService);
    expect(service).toBeTruthy();
  });
});
