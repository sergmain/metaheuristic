import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TestViewRoutingModule } from './testview.routing.module';
import { TestViewComponent } from './testview.component';

@NgModule({
    imports: [
        CommonModule,
        TestViewRoutingModule
    ],
    declarations: [TestViewComponent]
})
export class TestViewModule {}