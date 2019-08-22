import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AccountEditPassComponent } from './account-edit-pass.component';

describe('AccountEditPassComponent', () => {
  let component: AccountEditPassComponent;
  let fixture: ComponentFixture<AccountEditPassComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AccountEditPassComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AccountEditPassComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
