import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { ArchivePlansComponent } from './archive-plans.component';

describe('ArchivePlansComponent', () => {
  let component: ArchivePlansComponent;
  let fixture: ComponentFixture<ArchivePlansComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ ArchivePlansComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(ArchivePlansComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
