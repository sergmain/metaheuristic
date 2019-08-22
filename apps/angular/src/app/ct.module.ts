import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import * as components from '@app/ct';
import { MaterialAppModule } from './ngmaterial.module';


const list: any[] = [
    components.CtSectionContentComponent,
    components.CtPreComponent,
    components.CtHeadingComponent,
    components.CtSectionBodyRowComponent,
    components.CtSectionFooterRowComponent,
    components.CtSectionFooterComponent,
    components.CtColComponent,
    components.CtColsComponent,
    components.CtSectionCaptionComponent,
    components.CtTableComponent,
    components.CtWrapBlockComponent,
    components.CtFileUploadComponent,
    components.CtSectionHeaderComponent,
    components.CtSectionComponent,
    components.CtSectionHeaderRowComponent,
    components.CtFlexComponent,
    components.CtFlexItemComponent,
    components.CtSectionBodyComponent,
    components.CtHintComponent
];

@NgModule({
    imports: [
        CommonModule,
        MaterialAppModule
    ],
    declarations: list,
    exports: list
})

export class CtAppModule {}