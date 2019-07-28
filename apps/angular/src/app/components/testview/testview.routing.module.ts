import { NgModule } from '@angular/core';
import { Routes, RouterModule } from '@angular/router';

import { TestViewComponent } from './testview.component';

const routes: Routes = [
  {
    path: '',
    component: TestViewComponent
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})

export class TestViewRoutingModule { }
