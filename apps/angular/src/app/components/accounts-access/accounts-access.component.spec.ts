import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AccountsAccessComponent } from './accounts-access.component';

describe('AccountsAccessComponent', () => {
  let component: AccountsAccessComponent;
  let fixture: ComponentFixture<AccountsAccessComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AccountsAccessComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AccountsAccessComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
