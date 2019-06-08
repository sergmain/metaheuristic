import { TestBed } from '@angular/core/testing';

import { FlowsService } from './flows.service';

describe('FlowsService', () => {
  beforeEach(() => TestBed.configureTestingModule({}));

  it('should be created', () => {
    const service: FlowsService = TestBed.get(FlowsService);
    expect(service).toBeTruthy();
  });
});
