import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { AccountAccessComponent } from './account-access.component';

describe('AccountAccessComponent', () => {
  let component: AccountAccessComponent;
  let fixture: ComponentFixture<AccountAccessComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ AccountAccessComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(AccountAccessComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
