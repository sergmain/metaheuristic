import { async, ComponentFixture, TestBed } from '@angular/core/testing';

import { SnippetAddComponent } from './snippet-add.component';

describe('SnippetAddComponent', () => {
  let component: SnippetAddComponent;
  let fixture: ComponentFixture<SnippetAddComponent>;

  beforeEach(async(() => {
    TestBed.configureTestingModule({
      declarations: [ SnippetAddComponent ]
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(SnippetAddComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
