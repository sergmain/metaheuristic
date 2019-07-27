import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { CtFileUploadComponent } from './ct-file-upload.component';

describe('CtFileUploadComponent', () => {
  let component: CtFileUploadComponent;
  let fixture: ComponentFixture<CtFileUploadComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ CtFileUploadComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CtFileUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
