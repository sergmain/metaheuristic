import { Component, Inject } from '@angular/core';
import { MatDialog, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material';
import { forkJoin } from 'rxjs';

export interface QuestionData {
    text: string;
    params: any;
}

export interface DialogData {
    resolveTitle: string;
    rejectTitle: string;
    question ?(...data: any[]) : QuestionData | string;
}

export interface ConfirmationDialogInterface {
    readonly dialog: MatDialog;
}


@Component({
    selector: 'app-dialog-confirmation',
    templateUrl: './app-dialog-confirmation.component.pug',
    styleUrls: ['./app-dialog-confirmation.component.scss']
})


export class AppDialogConfirmationComponent {
    constructor(
        public dialogRef: MatDialogRef < AppDialogConfirmationComponent > ,
        @Inject(MAT_DIALOG_DATA) public data: any
    ) {
        // console.log(data);
    }

    onNoClick(): void {
        this.dialogRef.close(0);
    }

    onYesClick(): void {
        this.dialogRef.close(1);
    }
}



/**
 * require MatDialog
 *
 * constructor(
 *     private dialog: MatDialog
 * ) {}
 *
 */
export function ConfirmationDialogMethod(dialogData: DialogData) {
    return function fn(
        target: object,
        propertyName: string,
        propertyDesciptor: PropertyDescriptor,
    ): PropertyDescriptor {
        const method: any = propertyDesciptor.value;
        propertyDesciptor.value = function(...args: any[]) {

            let questionData: QuestionData = {
                text: '',
                params: false
            };

            if (typeof dialogData.question(...args) === 'string') {
                questionData.text = dialogData.question(...args) as string;
            } else {
                questionData = dialogData.question(...args) as QuestionData;
            }

            if (!this.dialog) { dialogError(); }
            if (questionData.params && !this.translate) { translateError(); }

            if (this.translate) {
                forkJoin(
                        this.translate.get(questionData.text, questionData.params),
                        this.translate.get(dialogData.resolveTitle),
                        this.translate.get(dialogData.rejectTitle)
                    )
                    .subscribe(
                        (response: any) => {
                            this.dialog.open(AppDialogConfirmationComponent, {
                                    width: '300px',
                                    data: {
                                        question: response[0],
                                        resolveTitle: response[1],
                                        rejectTitle: response[2]
                                    }
                                })
                                .afterClosed()
                                .subscribe((result: boolean) => {
                                    if (result) {
                                        method.apply(this, args);
                                    }
                                });
                        }
                    );
            } else {
                this.dialog.open(AppDialogConfirmationComponent, {
                        width: '300px',
                        data: {
                            question: questionData.text,
                            resolveTitle: dialogData.resolveTitle,
                            rejectTitle: dialogData.rejectTitle
                        }
                    })
                    .afterClosed()
                    .subscribe((result: boolean) => {
                        if (result) {
                            method.apply(this, args);
                        }
                    });
            }
        };

        return propertyDesciptor;
    };
}

function dialogError() {
    throw new Error(`
component require MatDialog

import { MatDialog } from '@angular/material';
...
constructor(
    ...
    private dialog: MatDialog
    ...
) {}
                `);
}

function translateError() {
    throw new Error(`
component require TranslateService

import { TranslateService } from '@ngx-translate/core';
...
constructor(
    ...
    private translate: TranslateService
    ...
) {}
                `);
}