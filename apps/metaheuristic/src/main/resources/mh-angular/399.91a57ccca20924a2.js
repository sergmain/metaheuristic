"use strict";(self.webpackChunkmetaheuristic_app=self.webpackChunkmetaheuristic_app||[]).push([[399],{9272:(w,C,a)=>{a.d(C,{K:()=>T});var c=a(1180),e=a(5938),l=a(4128),s=a(4650),U=a(4859);let x=(()=>{class g{constructor(Z,E){(0,c.Z)(this,"dialogRef",void 0),(0,c.Z)(this,"data",void 0),this.dialogRef=Z,this.data=E}onNoClick(){this.dialogRef.close(0)}onYesClick(){this.dialogRef.close(1)}}return(0,c.Z)(g,"\u0275fac",function(Z){return new(Z||g)(s.Y36(e.so),s.Y36(e.WI))}),(0,c.Z)(g,"\u0275cmp",s.Xpm({type:g,selectors:[["app-dialog-confirmation"]],decls:19,vars:3,consts:[[1,"mat-dialog-content"],[1,"mat-dialog-actions"],[1,"mat-dialog-action"],["mat-stroked-button","mat-stroked-button",3,"click"],["mat-flat-button","mat-flat-button","color","warn",3,"click"]],template:function(Z,E){1&Z&&(s.TgZ(0,"div",0),s._uU(1),s.qZA(),s._UZ(2,"br"),s._uU(3,"\n"),s.TgZ(4,"div",1),s._uU(5,"\n  "),s.TgZ(6,"div",2),s._uU(7,"\n    "),s.TgZ(8,"button",3),s.NdJ("click",function(){return E.onNoClick()}),s._uU(9),s.qZA(),s._uU(10,"\n  "),s.qZA(),s._uU(11,"\n  "),s.TgZ(12,"div",2),s._uU(13,"\n    "),s.TgZ(14,"button",4),s.NdJ("click",function(){return E.onYesClick()}),s._uU(15),s.qZA(),s._uU(16,"\n  "),s.qZA(),s._uU(17,"\n"),s.qZA(),s._uU(18," ")),2&Z&&(s.xp6(1),s.Oqu(E.data.question),s.xp6(8),s.Oqu(E.data.rejectTitle),s.xp6(6),s.Oqu(E.data.resolveTitle))},dependencies:[U.lW],styles:[".mat-dialog-actions[_ngcontent-%COMP%]{display:flex;justify-content:space-between;margin:0 -8px}.mat-dialog-action[_ngcontent-%COMP%]{flex:1;margin:0 8px}.mat-dialog-action[_ngcontent-%COMP%] > *[_ngcontent-%COMP%]{width:100%}.mat-dialog-content[_ngcontent-%COMP%]{max-height:auto!important;overflow:hidden!important}"]})),g})();function T(g){return function(Z,E,A){const b=A.value;return A.value=function(..._){let d={text:"",params:!1};"string"==typeof g.question(..._)?d.text=g.question(..._):d=g.question(..._),this.dialog||function f(){throw new Error("\ncomponent require MatDialog\n\nimport { MatDialog } from '@angular/material';\n...\nconstructor(\n    ...\n    private dialog: MatDialog\n    ...\n) {}\n                ")}(),d.params&&!this.translate&&function t(){throw new Error("\ncomponent require TranslateService\n\nimport { TranslateService } from '@ngx-translate/core';\n...\nconstructor(\n    ...\n    private translate: TranslateService\n    ...\n) {}\n                ")}(),this.translate?(0,l.D)(this.translate.get(d.text,d.params),this.translate.get(g.resolveTitle),this.translate.get(g.rejectTitle)).subscribe(u=>{this.dialog.open(x,{width:"500px",data:{question:u[0],resolveTitle:u[1],rejectTitle:u[2]}}).afterClosed().subscribe(M=>{M&&b.apply(this,_)})}):this.dialog.open(x,{width:"500px",data:{question:d.text,resolveTitle:g.resolveTitle,rejectTitle:g.rejectTitle}}).afterClosed().subscribe(u=>{u&&b.apply(this,_)})},A}}},2177:(w,C,a)=>{a.d(C,{B:()=>c});var c=(()=>{return(e=c||(c={})).ERROR="ERROR",e.UNKNOWN="UNKNOWN",e.NONE="NONE",e.PRODUCING="PRODUCING",e.PRODUCED="PRODUCED",e.STARTED="STARTED",e.STOPPED="STOPPED",e.FINISHED="FINISHED",e.DOESNT_EXIST="DOESNT_EXIST",c;var e})()},4853:(w,C,a)=>{a.d(C,{Q:()=>s});var c=a(1180),e=a(4650);const l=["*"];let s=(()=>{class U{constructor(){}ngOnInit(){}}return(0,c.Z)(U,"\u0275fac",function(T){return new(T||U)}),(0,c.Z)(U,"\u0275cmp",e.Xpm({type:U,selectors:[["ct-pre"]],ngContentSelectors:l,decls:1,vars:0,template:function(T,f){1&T&&(e.F$t(),e.Hsn(0))},styles:["[_nghost-%COMP%]{display:block;font-family:sans-serif;padding:0;font-size:12px;line-height:1.6;word-break:break-word;white-space:pre-wrap}[overflow-x=auto][_nghost-%COMP%]{overflow-x:auto}"]})),U})()},1023:(w,C,a)=>{a.d(C,{R:()=>b});var c=a(1180),e=a(4650),l=a(6895),s=a(1439),U=a(9185),x=a(305),T=a(9863);function f(_,d){if(1&_&&(e.TgZ(0,"ct-section-body-row"),e._uU(1,"\n            "),e.TgZ(2,"div",1),e._uU(3,"STATUS:"),e.qZA(),e._uU(4,"\n            "),e.TgZ(5,"ct-heading"),e._uU(6),e.qZA(),e._uU(7,"\n        "),e.qZA()),2&_){const u=e.oxw(2);e.xp6(6),e.Oqu(u.content.status)}}function t(_,d){if(1&_&&(e.TgZ(0,"ct-section-body-row"),e._uU(1,"\n            "),e.TgZ(2,"div",1),e._uU(3,"STATUS:"),e.qZA(),e._uU(4,"\n            "),e.TgZ(5,"ct-heading"),e._uU(6),e.qZA(),e._uU(7,"\n        "),e.qZA()),2&_){const u=e.oxw(2);e.xp6(6),e.Oqu(u.content.validationResult.status)}}function g(_,d){if(1&_&&(e.TgZ(0,"li"),e._uU(1),e.qZA()),2&_){const u=d.$implicit;e.xp6(1),e.Oqu(u)}}function h(_,d){if(1&_&&(e.TgZ(0,"ct-section-body-row"),e._uU(1,"\n            "),e.TgZ(2,"div",1),e._uU(3,"ERRORS:"),e.qZA(),e._uU(4,"\n            "),e.TgZ(5,"ul",2),e._uU(6,"\n                "),e.YNc(7,g,2,1,"li",3),e._uU(8,"\n            "),e.qZA(),e._uU(9,"\n        "),e.qZA()),2&_){const u=e.oxw(2);e.xp6(7),e.Q6J("ngForOf",u.content.errorMessages)}}function Z(_,d){if(1&_&&(e.TgZ(0,"li"),e._uU(1),e.qZA()),2&_){const u=d.$implicit;e.xp6(1),e.Oqu(u)}}function E(_,d){if(1&_&&(e.TgZ(0,"ct-section-body-row"),e._uU(1,"\n            "),e.TgZ(2,"div",1),e._uU(3,"INFO:"),e.qZA(),e._uU(4,"\n            "),e.TgZ(5,"ul",2),e._uU(6,"\n                "),e.YNc(7,Z,2,1,"li",3),e._uU(8,"\n            "),e.qZA(),e._uU(9,"\n        "),e.qZA()),2&_){const u=e.oxw(2);e.xp6(7),e.Q6J("ngForOf",u.content.infoMessages)}}function A(_,d){if(1&_&&(e.TgZ(0,"ct-section"),e._uU(1,"\n    "),e.TgZ(2,"ct-section-body"),e._uU(3,"\n        "),e.YNc(4,f,8,1,"ct-section-body-row",0),e._uU(5,"\n\n        "),e.YNc(6,t,8,1,"ct-section-body-row",0),e._uU(7,"\n\n        "),e.YNc(8,h,10,1,"ct-section-body-row",0),e._uU(9,"\n\n        "),e.YNc(10,E,10,1,"ct-section-body-row",0),e._uU(11,"\n    "),e.qZA(),e._uU(12,"\n"),e.qZA()),2&_){const u=e.oxw();e.xp6(4),e.Q6J("ngIf",u.content.status),e.xp6(2),e.Q6J("ngIf",u.content.validationResult),e.xp6(2),e.Q6J("ngIf",null==u.content.errorMessages?null:u.content.errorMessages.length),e.xp6(2),e.Q6J("ngIf",null==u.content.infoMessages?null:u.content.infoMessages.length)}}let b=(()=>{class _{constructor(){(0,c.Z)(this,"content",void 0)}}return(0,c.Z)(_,"\u0275fac",function(u){return new(u||_)}),(0,c.Z)(_,"\u0275cmp",e.Xpm({type:_,selectors:[["ct-rest-status"]],inputs:{content:"content"},decls:1,vars:1,consts:[[4,"ngIf"],[2,"font-size","75%","opacity","0.75"],[1,"code"],[4,"ngFor","ngForOf"]],template:function(u,M){1&u&&e.YNc(0,A,13,4,"ct-section",0),2&u&&e.Q6J("ngIf",M.content)},dependencies:[l.sg,l.O5,s.U,U.n,x.Z,T._],styles:[".code[_ngcontent-%COMP%]{font-size:75%;line-height:1.8;font-family:Courier New,Courier,monospace}"]})),_})()},7530:(w,C,a)=>{a.d(C,{a:()=>U});var c=a(1180),e=a(4650),l=a(1572);const s=["*"];let U=(()=>{class x{constructor(f){(0,c.Z)(this,"changeDetector",void 0),(0,c.Z)(this,"isWaiting",void 0),(0,c.Z)(this,"state",{wait:!1}),(0,c.Z)(this,"isFnMode",void 0),this.changeDetector=f}ngOnInit(){void 0===this.isWaiting?this.isFnMode=!0:(this.isFnMode=!1,this.state.wait=this.isWaiting)}ngOnDestroy(){this.changeDetector.detach()}ngOnChanges(){this.isFnMode||(this.state.wait=this.isWaiting)}wait(){this.isFnMode&&(this.state.wait=!0,this.changeDetector.destroyed||this.changeDetector.detectChanges())}show(){this.isFnMode&&(this.state.wait=!1,this.changeDetector.destroyed||this.changeDetector.detectChanges())}}return(0,c.Z)(x,"\u0275fac",function(f){return new(f||x)(e.Y36(e.sBO))}),(0,c.Z)(x,"\u0275cmp",e.Xpm({type:x,selectors:[["ct-table"]],inputs:{isWaiting:"isWaiting"},features:[e.TTD],ngContentSelectors:s,decls:12,vars:2,consts:[[1,"ct-table"],[1,"ct-table__body"],[1,"ct-table__wait"]],template:function(f,t){1&f&&(e.F$t(),e.TgZ(0,"div",0),e._uU(1,"\n    "),e.TgZ(2,"div",1),e._uU(3,"\n        "),e.Hsn(4),e._uU(5,"\n    "),e.qZA(),e._uU(6,"\n    "),e.TgZ(7,"div",2),e._uU(8,"\n        "),e._UZ(9,"mat-spinner"),e._uU(10,"\n    "),e.qZA(),e._uU(11,"\n"),e.qZA()),2&f&&e.ekj("ct-table--wait",t.state.wait)},dependencies:[l.Ou],styles:["[_nghost-%COMP%]{display:block;position:relative;margin:0;overflow-y:auto}[_nghost-%COMP%]     .mat-table{width:100%;border-collapse:collapse;background:none}[_nghost-%COMP%]     .mat-header-row{height:auto}[_nghost-%COMP%]     .mat-header-cell, [_nghost-%COMP%]     .mat-cell, [_nghost-%COMP%]     .mat-footer-cell{border-bottom-width:1px;border-bottom-style:solid;border-top-width:1px;border-top-style:solid;padding:9px;font-family:sans-serif;font-size:14.94px;line-height:18px}[_nghost-%COMP%]     .mat-header-cell{white-space:nowrap;font-weight:700;vertical-align:baseline;color:inherit}[_nghost-%COMP%]     .mat-cell{vertical-align:baseline}[_nghost-%COMP%]     .mat-header-cell:first-child, [_nghost-%COMP%]     .mat-cell:first-child{padding-left:9px}[_nghost-%COMP%]     .mat-header-cell:last-child, [_nghost-%COMP%]     .mat-cell:last-child{padding-right:9px}[_nghost-%COMP%]     .mat-row{height:auto}.light-theme[_nghost-%COMP%]     .mat-header-cell, .light-theme   [_nghost-%COMP%]     .mat-header-cell, .light-theme[_nghost-%COMP%]     .mat-cell, .light-theme   [_nghost-%COMP%]     .mat-cell, .light-theme[_nghost-%COMP%]     .mat-footer-cell, .light-theme   [_nghost-%COMP%]     .mat-footer-cell{border-top-color:#f0f0f0;border-bottom-color:#f0f0f0}.dark-theme[_nghost-%COMP%]     .mat-header-cell, .dark-theme   [_nghost-%COMP%]     .mat-header-cell, .dark-theme[_nghost-%COMP%]     .mat-cell, .dark-theme   [_nghost-%COMP%]     .mat-cell, .dark-theme[_nghost-%COMP%]     .mat-footer-cell, .dark-theme   [_nghost-%COMP%]     .mat-footer-cell{border-top-color:#474747;border-bottom-color:#474747}.ct-table[_ngcontent-%COMP%]{position:relative}.ct-table__wait[_ngcontent-%COMP%]{position:absolute;top:0;left:0;width:100%;height:100%;display:none;align-items:center;justify-content:center;background-color:#ffffff1a;overflow:hidden}.ct-table--wait[_ngcontent-%COMP%]   .ct-table__body[_ngcontent-%COMP%]{opacity:.5}.ct-table--wait[_ngcontent-%COMP%]   .ct-table__wait[_ngcontent-%COMP%]{display:flex}"]})),x})()},7998:(w,C,a)=>{a.r(C),a.d(C,{ExperimentsModule:()=>It,ExperimentsRoutes:()=>W,ExperimentsRoutingModule:()=>z});var c=a(1180),e=a(6895),l=a(4006),s=a(9299),U=a(3081),x=a(6423),T=a(2340),f=a(7528),t=a(4650),g=a(529);const h=o=>`${T.N.baseUrl}dispatcher/experiment${o}`;let Z=(()=>{class o{constructor(n){(0,c.Z)(this,"http",void 0),this.http=n}getExperiments(n){return this.http.get(h("/experiments"),{params:{page:n}})}getExperiment(n){return this.http.get(h(`/experiment/${n}`))}experimentAdd(){return this.http.get(h("/experiment-add"))}edit(n){return this.http.get(h(`/experiment-edit/${n}`))}addFormCommit(n,r,m,p){return this.http.post(h("/experiment-add-commit"),(0,f.P)({sourceCodeUid:n,name:r,code:m,description:p}))}editFormCommit(n){return this.http.post(h("/experiment-edit-commit"),n)}deleteCommit(n){return this.http.post(h("/experiment-delete-commit"),(0,f.P)({id:n}))}experimentCloneCommit(n){return this.http.post(h("/experiment-clone-commit"),(0,f.P)({id:n}))}execContextTargetExecState(n,r){return this.http.post(h(`/experiment-target-state/${r}/${n}`),{})}}return(0,c.Z)(o,"\u0275fac",function(n){return new(n||o)(t.LFG(g.eN))}),(0,c.Z)(o,"\u0275prov",t.Yz7({token:o,factory:o.\u0275fac,providedIn:"root"})),o})();var E=a(2199),A=a(1439),b=a(9185),_=a(6890),d=a(2555),u=a(2379),M=a(5510),D=a(6264),I=a(305),S=a(5014),N=a(8868),J=a(3370),F=a(9863),Q=a(1023),$=a(3238),q=a(4859),L=a(284),P=a(9549),B=a(9349),H=a(4385);function G(o,i){if(1&o&&(t.TgZ(0,"div"),t._uU(1,"\n                                    "),t.TgZ(2,"mat-option",12),t._uU(3),t.qZA(),t._uU(4,"\n                                "),t.qZA()),2&o){const n=i.$implicit;t.xp6(2),t.Q6J("value",n.uid),t.xp6(1),t.Oqu(n.uid)}}let X=(()=>{class o{constructor(n,r,m){(0,c.Z)(this,"experimentsService",void 0),(0,c.Z)(this,"router",void 0),(0,c.Z)(this,"activatedRoute",void 0),(0,c.Z)(this,"form",new l.cw({sourceCodeUID:new l.NI("",[l.kI.required,l.kI.minLength(1)]),name:new l.NI("",[l.kI.required,l.kI.minLength(3)]),description:new l.NI("",[l.kI.required,l.kI.minLength(3)]),experimentCode:new l.NI("",[l.kI.required,l.kI.minLength(3)])})),(0,c.Z)(this,"operationStatusRest",void 0),(0,c.Z)(this,"sourceCodeUidsForCompany",void 0),this.experimentsService=n,this.router=r,this.activatedRoute=m}ngOnInit(){this.experimentsService.experimentAdd().subscribe({next:n=>{this.sourceCodeUidsForCompany=n}})}cancel(){this.router.navigate(["../"],{relativeTo:this.activatedRoute})}create(){this.experimentsService.addFormCommit(this.form.value.sourceCodeUID,this.form.value.name,this.form.value.description,this.form.value.experimentCode).subscribe({next:n=>{this.operationStatusRest=n,n.status===E.o.OK&&this.form.reset()}})}}return(0,c.Z)(o,"\u0275fac",function(n){return new(n||o)(t.Y36(Z),t.Y36(s.F0),t.Y36(s.gz))}),(0,c.Z)(o,"\u0275cmp",t.Xpm({type:o,selectors:[["experiment-add"]],decls:111,vars:4,consts:[["size","6"],["novalidate","novalidate",3,"formGroup"],["appearance","outline"],["formControlName","sourceCodeUID"],[4,"ngFor","ngForOf"],["matInput","matInput","formControlName","name"],["matInput","matInput","formControlName","description","cdkTextareaAutosize","cdkTextareaAutosize","cdkAutosizeMinRows","5"],["matInput","matInput","formControlName","experimentCode","cdkTextareaAutosize","cdkTextareaAutosize","cdkAutosizeMinRows","5"],["justify-content","flex-end","gap","8px"],["mat-stroked-button","mat-stroked-button",3,"click"],["mat-flat-button","mat-flat-button","color","primary",3,"disabled","click"],[3,"content"],[3,"value"]],template:function(n,r){1&n&&(t.TgZ(0,"ct-cols"),t._uU(1,"\n    "),t.TgZ(2,"ct-col",0),t._uU(3,"\n        "),t.TgZ(4,"ct-section"),t._uU(5,"\n            "),t.TgZ(6,"ct-section-header"),t._uU(7,"\n                "),t.TgZ(8,"ct-section-header-row"),t._uU(9,"\n                    "),t.TgZ(10,"ct-heading"),t._uU(11,"New Experiment"),t.qZA(),t._uU(12,"\n                "),t.qZA(),t._uU(13,"\n            "),t.qZA(),t._uU(14,"\n            "),t.TgZ(15,"ct-section-body"),t._uU(16,"\n                "),t.TgZ(17,"ct-section-body-row"),t._uU(18,"\n                    "),t.TgZ(19,"form",1),t._uU(20,"\n                        "),t.TgZ(21,"mat-form-field",2),t._uU(22,"\n                            "),t.TgZ(23,"mat-label"),t._uU(24,"List of Uids of source codes"),t.qZA(),t._uU(25,"\n                            "),t.TgZ(26,"mat-select",3),t._uU(27,"\n                                "),t.YNc(28,G,5,2,"div",4),t._uU(29,"\n                            "),t.qZA(),t._uU(30,"\n                            "),t.TgZ(31,"mat-hint"),t._uU(32,"This is a required field."),t.qZA(),t._uU(33,"\n                        "),t.qZA(),t._uU(34,"\n                        "),t._UZ(35,"br"),t._uU(36,"\n                        "),t._UZ(37,"br"),t._uU(38,"\n                        "),t.TgZ(39,"mat-form-field",2),t._uU(40,"\n                            "),t.TgZ(41,"mat-label"),t._uU(42,"Name"),t.qZA(),t._uU(43,"\n                            "),t._UZ(44,"input",5),t._uU(45,"\n                            "),t.TgZ(46,"mat-hint"),t._uU(47,"This is a required field."),t.qZA(),t._uU(48,"\n                        "),t.qZA(),t._uU(49,"\n                        "),t._UZ(50,"br"),t._uU(51,"\n                        "),t._UZ(52,"br"),t._uU(53,"\n                        "),t.TgZ(54,"mat-form-field",2),t._uU(55,"\n                            "),t.TgZ(56,"mat-label"),t._uU(57,"Description"),t.qZA(),t._uU(58,"\n                            "),t._UZ(59,"textarea",6),t._uU(60,"\n                            "),t.TgZ(61,"mat-hint"),t._uU(62,"This is a required field."),t.qZA(),t._uU(63,"\n                        "),t.qZA(),t._uU(64,"\n                        "),t._UZ(65,"br"),t._uU(66,"\n                        "),t._UZ(67,"br"),t._uU(68,"\n                        "),t.TgZ(69,"mat-form-field",2),t._uU(70,"\n                            "),t.TgZ(71,"mat-label"),t._uU(72,"Experiment code"),t.qZA(),t._uU(73,"\n                            "),t._UZ(74,"textarea",7),t._uU(75,"\n                            "),t.TgZ(76,"mat-hint"),t._uU(77,"This is a required field."),t.qZA(),t._uU(78,"\n                        "),t.qZA(),t._uU(79,"\n                    "),t.qZA(),t._uU(80,"\n                "),t.qZA(),t._uU(81,"\n            "),t.qZA(),t._uU(82,"\n            "),t.TgZ(83,"ct-section-footer"),t._uU(84,"\n                "),t.TgZ(85,"ct-section-footer-row"),t._uU(86,"\n                    "),t.TgZ(87,"ct-flex",8),t._uU(88,"\n                        "),t.TgZ(89,"ct-flex-item"),t._uU(90,"\n                            "),t.TgZ(91,"button",9),t.NdJ("click",function(){return r.cancel()}),t._uU(92,"Cancel"),t.qZA(),t._uU(93,"\n                        "),t.qZA(),t._uU(94,"\n                        "),t.TgZ(95,"ct-flex-item"),t._uU(96,"\n                            "),t.TgZ(97,"button",10),t.NdJ("click",function(){return r.create()}),t._uU(98,"Create"),t.qZA(),t._uU(99,"\n                        "),t.qZA(),t._uU(100,"\n                    "),t.qZA(),t._uU(101,"\n                "),t.qZA(),t._uU(102,"\n            "),t.qZA(),t._uU(103,"\n        "),t.qZA(),t._uU(104,"\n    "),t.qZA(),t._uU(105,"\n    "),t.TgZ(106,"ct-col",0),t._uU(107,"\n        "),t._UZ(108,"ct-rest-status",11),t._uU(109,"\n    "),t.qZA(),t._uU(110,"\n"),t.qZA()),2&n&&(t.xp6(19),t.Q6J("formGroup",r.form),t.xp6(9),t.Q6J("ngForOf",null==r.sourceCodeUidsForCompany?null:r.sourceCodeUidsForCompany.items),t.xp6(69),t.Q6J("disabled",r.form.invalid),t.xp6(11),t.Q6J("content",r.operationStatusRest))},dependencies:[e.sg,A.U,b.n,_.V,d.R,u.t,M.W,D.i,I.Z,S.t,N.B,J.i,F._,Q.R,$.ey,q.lW,L.Nt,P.KE,P.hX,P.bx,B.IC,H.gD,l._Y,l.Fj,l.JJ,l.JL,l.sg,l.u],styles:["mat-form-field[_ngcontent-%COMP%]{width:100%}"]})),o})();var K=a(8318),j=a(3788);function V(o,i){if(1&o){const n=t.EpF();t.TgZ(0,"ct-cols"),t._uU(1,"\n    "),t.TgZ(2,"ct-col",1),t._uU(3,"\n        "),t.TgZ(4,"ct-section"),t._uU(5,"\n\n            "),t.TgZ(6,"ct-section-header"),t._uU(7,"\n                "),t.TgZ(8,"ct-section-header-row"),t._uU(9,"\n                    "),t.TgZ(10,"ct-heading"),t._uU(11),t.qZA(),t._uU(12,"\n                "),t.qZA(),t._uU(13,"\n            "),t.qZA(),t._uU(14,"\n\n            "),t.TgZ(15,"ct-section-body"),t._uU(16,"\n                "),t.TgZ(17,"ct-section-body-row"),t._uU(18,"\n                    "),t.TgZ(19,"mat-form-field",2),t._uU(20,"\n                        "),t.TgZ(21,"mat-label"),t._uU(22,"Name"),t.qZA(),t._uU(23,"\n                        "),t.TgZ(24,"input",3),t.NdJ("ngModelChange",function(m){t.CHM(n);const p=t.oxw();return t.KtG(p.simpleExperiment.name=m)}),t.qZA(),t._uU(25,"\n                        "),t.TgZ(26,"mat-hint"),t._uU(27,"This is a required field. "),t.qZA(),t._uU(28,"\n                    "),t.qZA(),t._uU(29,"\n                "),t.qZA(),t._uU(30,"\n                "),t.TgZ(31,"ct-section-body-row"),t._uU(32,"\n                    "),t.TgZ(33,"mat-form-field",2),t._uU(34,"\n                        "),t.TgZ(35,"mat-label"),t._uU(36,"Description"),t.qZA(),t._uU(37,"\n                        "),t.TgZ(38,"textarea",4),t.NdJ("ngModelChange",function(m){t.CHM(n);const p=t.oxw();return t.KtG(p.simpleExperiment.description=m)}),t.qZA(),t._uU(39,"\n                        "),t.TgZ(40,"mat-hint"),t._uU(41,"This is a required field. "),t.qZA(),t._uU(42,"\n                    "),t.qZA(),t._uU(43,"\n                "),t.qZA(),t._uU(44,"\n                "),t.TgZ(45,"ct-section-body-row"),t._uU(46,"\n                    "),t.TgZ(47,"mat-form-field",2),t._uU(48,"\n                        "),t.TgZ(49,"mat-label"),t._uU(50,"Experiment code"),t.qZA(),t._uU(51,"\n                        "),t.TgZ(52,"textarea",4),t.NdJ("ngModelChange",function(m){t.CHM(n);const p=t.oxw();return t.KtG(p.simpleExperiment.code=m)}),t.qZA(),t._uU(53,"\n                        "),t.TgZ(54,"mat-hint"),t._uU(55,"This is a required field. "),t.qZA(),t._uU(56,"\n                    "),t.qZA(),t._uU(57,"\n                "),t.qZA(),t._uU(58,"\n            "),t.qZA(),t._uU(59,"\n\n            "),t.TgZ(60,"ct-section-footer"),t._uU(61,"\n                "),t.TgZ(62,"ct-section-footer-row"),t._uU(63,"\n                    "),t.TgZ(64,"ct-flex",5),t._uU(65,"\n                        "),t.TgZ(66,"ct-flex-item"),t._uU(67,"\n                            "),t.TgZ(68,"button",6),t.NdJ("click",function(){t.CHM(n);const m=t.oxw();return t.KtG(m.back())}),t._uU(69,"\n                                Cancel"),t.qZA(),t._uU(70,"\n                        "),t.qZA(),t._uU(71,"\n                        "),t.TgZ(72,"ct-flex-item"),t._uU(73,"\n                            "),t.TgZ(74,"button",7),t.NdJ("click",function(){t.CHM(n);const m=t.oxw();return t.KtG(m.save())}),t._uU(75,"Save"),t.qZA(),t._uU(76,"\n                        "),t.qZA(),t._uU(77,"\n                    "),t.qZA(),t._uU(78,"\n                "),t.qZA(),t._uU(79,"\n            "),t.qZA(),t._uU(80,"\n\n        "),t.qZA(),t._uU(81,"\n    "),t.qZA(),t._uU(82,"\n    "),t.TgZ(83,"ct-col",1),t._uU(84,"\n        "),t._UZ(85,"ct-rest-status",8),t._uU(86,"\n    "),t.qZA(),t._uU(87,"\n"),t.qZA()}if(2&o){const n=t.oxw();t.xp6(11),t.hij("Experiment #",n.simpleExperiment.id,""),t.xp6(13),t.Q6J("ngModel",n.simpleExperiment.name),t.xp6(14),t.Q6J("ngModel",n.simpleExperiment.description),t.xp6(14),t.Q6J("ngModel",n.simpleExperiment.code),t.xp6(22),t.Q6J("disabled",n.isLoading),t.xp6(11),t.Q6J("content",n.operationStatusRest)}}let tt=(()=>{class o extends K.S{constructor(n,r,m,p,O){super(O),(0,c.Z)(this,"route",void 0),(0,c.Z)(this,"experimentsService",void 0),(0,c.Z)(this,"router",void 0),(0,c.Z)(this,"activatedRoute",void 0),(0,c.Z)(this,"authenticationService",void 0),(0,c.Z)(this,"experimentsEditResult",void 0),(0,c.Z)(this,"operationStatusRest",void 0),(0,c.Z)(this,"simpleExperiment",{name:null,description:null,code:null,id:null}),this.route=n,this.experimentsService=r,this.router=m,this.activatedRoute=p,this.authenticationService=O}ngOnInit(){this.setIsLoadingStart(),this.simpleExperiment.id=this.route.snapshot.paramMap.get("experimentId"),this.experimentsService.edit(this.route.snapshot.paramMap.get("experimentId")).subscribe({next:n=>{this.experimentsEditResult=n,this.simpleExperiment.code=n.simpleExperiment.code,this.simpleExperiment.description=n.simpleExperiment.description,this.simpleExperiment.name=n.simpleExperiment.name},complete:()=>{this.setIsLoadingEnd()}})}save(){this.setIsLoadingStart(),this.experimentsService.editFormCommit(this.simpleExperiment).subscribe({next:n=>this.operationStatusRest=n,complete:()=>this.setIsLoadingEnd()})}back(){this.router.navigate(["../../"],{relativeTo:this.activatedRoute})}}return(0,c.Z)(o,"\u0275fac",function(n){return new(n||o)(t.Y36(s.gz),t.Y36(Z),t.Y36(s.F0),t.Y36(s.gz),t.Y36(j.$h))}),(0,c.Z)(o,"\u0275cmp",t.Xpm({type:o,selectors:[["experiment-edit"]],features:[t.qOj],decls:1,vars:1,consts:[[4,"ngIf"],["size","6"],["appearance","outline"],["matInput","matInput","autocomplete","off",3,"ngModel","ngModelChange"],["matInput","matInput","cdkTextareaAutosize","cdkTextareaAutosize","cdkAutosizeMinRows","5",3,"ngModel","ngModelChange"],["justify-content","flex-end","gap","8px"],["mat-stroked-button","mat-stroked-button","title","Cancel",3,"click"],["mat-flat-button","mat-flat-button","color","primary","title","Save",3,"disabled","click"],[3,"content"]],template:function(n,r){1&n&&t.YNc(0,V,88,6,"ct-cols",0),2&n&&t.Q6J("ngIf",r.experimentsEditResult)},dependencies:[e.O5,A.U,b.n,_.V,d.R,u.t,M.W,D.i,I.Z,S.t,N.B,J.i,F._,Q.R,q.lW,L.Nt,P.KE,P.hX,P.bx,B.IC,l.Fj,l.JJ,l.On],styles:["mat-form-field[_ngcontent-%COMP%]{width:100%}.metadata-sections[_ngcontent-%COMP%]   .mat-cell[_ngcontent-%COMP%], .metadata-sections[_ngcontent-%COMP%]   .mat-header-cell[_ngcontent-%COMP%]{border:none!important}.metadata-sections[_ngcontent-%COMP%]   .mat-column-key[_ngcontent-%COMP%], .metadata-sections[_ngcontent-%COMP%]   .mat-column-empty[_ngcontent-%COMP%]{flex:none;width:250px;padding-top:4px;padding-bottom:4px}.metadata-sections[_ngcontent-%COMP%]   .mat-column-values[_ngcontent-%COMP%], .metadata-sections[_ngcontent-%COMP%]   .mat-column-edit[_ngcontent-%COMP%]{flex-grow:1}.metadata-sections[_ngcontent-%COMP%]   .mat-column-delete[_ngcontent-%COMP%], .metadata-sections[_ngcontent-%COMP%]   .mat-column-done[_ngcontent-%COMP%], .metadata-sections[_ngcontent-%COMP%]   .mat-column-bts[_ngcontent-%COMP%]{flex:none}.metadata-sections[_ngcontent-%COMP%]   div[_ngcontent-%COMP%]{width:100%}.metadata-sections[_ngcontent-%COMP%]   .mat-row[_ngcontent-%COMP%]{align-items:baseline}.metadata-sections[_ngcontent-%COMP%]   .mat-row[_ngcontent-%COMP%]:after{content:none}.metadata-sections[_ngcontent-%COMP%]   mat-form-field[_ngcontent-%COMP%]{margin-top:1.34375em}.metadata-sections[_ngcontent-%COMP%]   .metadata-params[_ngcontent-%COMP%]   .caption-row[_ngcontent-%COMP%]:hover{background:rgba(0,0,0,.033)}.metadata-sections[_ngcontent-%COMP%]   .metadata-params[_ngcontent-%COMP%]   .caption-row.picked-row[_ngcontent-%COMP%]:hover{background:rgba(0,0,0,0)}.metadata-sections[_ngcontent-%COMP%]   .metadata-params[_ngcontent-%COMP%]   .caption-row.picked-row[_ngcontent-%COMP%] + .edit-row[_ngcontent-%COMP%]{display:flex}"]})),o})();var v=a(3626),et=a(9272),R=a(2177),nt=a(5938),ot=a(7530),it=a(7824),at=a(7392),Y=function(o,i){if("object"==typeof Reflect&&"function"==typeof Reflect.metadata)return Reflect.metadata(o,i)};function st(o,i){1&o&&(t.TgZ(0,"th",19),t._uU(1,"Id"),t.qZA())}function rt(o,i){if(1&o&&(t.TgZ(0,"td",20),t._uU(1),t.qZA()),2&o){const n=i.$implicit;t.xp6(1),t.hij("",n.experiment.id," ")}}function _t(o,i){1&o&&(t.TgZ(0,"th",19),t._uU(1,"Name"),t.qZA())}function mt(o,i){if(1&o&&(t.TgZ(0,"td",20),t._uU(1),t.qZA()),2&o){const n=i.$implicit;t.xp6(1),t.hij("",n.experiment.name," ")}}function lt(o,i){1&o&&(t.TgZ(0,"th",19),t._uU(1,"Created on"),t.qZA())}function ut(o,i){if(1&o&&(t.TgZ(0,"td",20),t._uU(1),t.ALo(2,"date"),t.qZA()),2&o){const n=i.$implicit;t.xp6(1),t.hij(" ",t.xi3(2,1,n.experiment.createdOn,"HH:mm:ss, MMM d, yyyy"),"\n                        ")}}function pt(o,i){1&o&&(t.TgZ(0,"th",19),t._uU(1,"Exec State"),t.qZA())}function dt(o,i){if(1&o&&(t.TgZ(0,"td",20),t._uU(1),t.qZA()),2&o){const n=i.$implicit;t.xp6(1),t.hij(" ",n.experiment.execState," ")}}function gt(o,i){1&o&&(t.TgZ(0,"th",19),t._uU(1,"Code"),t.qZA())}function xt(o,i){if(1&o&&(t.TgZ(0,"td",20),t._uU(1),t.qZA()),2&o){const n=i.$implicit;t.xp6(1),t.hij(" ",n.experiment.code," ")}}function ft(o,i){1&o&&(t.TgZ(0,"th",19),t._uU(1,"Desc"),t.qZA())}function ht(o,i){if(1&o&&(t.TgZ(0,"td",20),t._uU(1),t.qZA()),2&o){const n=i.$implicit;t.xp6(1),t.hij(" ",n.experiment.description," ")}}function Ut(o,i){1&o&&(t.TgZ(0,"th",19),t._uU(1," "),t.qZA())}const Zt=function(o){return{element:o}};function Ct(o,i){if(1&o&&(t.TgZ(0,"td",20),t._uU(1,"\n                            "),t.ynx(2,21),t._uU(3,"\n                            "),t.BQk(),t._uU(4,"\n                        "),t.qZA()),2&o){const n=i.$implicit;t.oxw(2);const r=t.MAs(3);t.xp6(2),t.Q6J("ngTemplateOutlet",r)("ngTemplateOutletContext",t.VKq(2,Zt,n))}}function Tt(o,i){1&o&&t._UZ(0,"tr",22)}function Et(o,i){1&o&&t._UZ(0,"tr",23)}function vt(o,i){1&o&&t.GkF(0)}function At(o,i){if(1&o){const n=t.EpF();t.TgZ(0,"ct-section"),t._uU(1,"\n    "),t.TgZ(2,"ct-section-header"),t._uU(3,"\n        "),t.TgZ(4,"ct-section-header-row"),t._uU(5,"\n            "),t.TgZ(6,"ct-heading"),t._uU(7,"Experiments"),t.qZA(),t._uU(8,"\n        "),t.qZA(),t._uU(9,"\n    "),t.qZA(),t._uU(10,"\n    "),t.TgZ(11,"ct-section-body"),t._uU(12,"\n        "),t.TgZ(13,"ct-section-body-row"),t._uU(14,"\n            "),t.TgZ(15,"ct-table",3),t._uU(16,"\n                "),t.TgZ(17,"table",4),t._uU(18,"\n                    "),t.ynx(19,5),t._uU(20,"\n                        "),t.YNc(21,st,2,0,"th",6),t._uU(22,"\n                        "),t.YNc(23,rt,2,1,"td",7),t._uU(24,"\n                    "),t.BQk(),t._uU(25,"\n\n                    "),t.ynx(26,8),t._uU(27,"\n                        "),t.YNc(28,_t,2,0,"th",6),t._uU(29,"\n                        "),t.YNc(30,mt,2,1,"td",7),t._uU(31,"\n                    "),t.BQk(),t._uU(32,"\n\n                    "),t.ynx(33,9),t._uU(34,"\n                        "),t.YNc(35,lt,2,0,"th",6),t._uU(36,"\n                        "),t.YNc(37,ut,3,4,"td",7),t._uU(38,"\n                    "),t.BQk(),t._uU(39,"\n\n                    "),t.ynx(40,10),t._uU(41,"\n                        "),t.YNc(42,pt,2,0,"th",6),t._uU(43,"\n                        "),t.YNc(44,dt,2,1,"td",7),t._uU(45,"\n                    "),t.BQk(),t._uU(46,"\n\n                    "),t.ynx(47,11),t._uU(48,"\n                        "),t.YNc(49,gt,2,0,"th",6),t._uU(50,"\n                        "),t.YNc(51,xt,2,1,"td",7),t._uU(52,"\n                    "),t.BQk(),t._uU(53,"\n\n                    "),t.ynx(54,12),t._uU(55,"\n                        "),t.YNc(56,ft,2,0,"th",6),t._uU(57,"\n                        "),t.YNc(58,ht,2,1,"td",7),t._uU(59,"\n                    "),t.BQk(),t._uU(60,"\n\n                    "),t.ynx(61,13),t._uU(62,"\n                        "),t.YNc(63,Ut,2,0,"th",6),t._uU(64,"\n                        "),t.YNc(65,Ct,5,4,"td",7),t._uU(66,"\n                    "),t.BQk(),t._uU(67,"\n\n                    "),t.YNc(68,Tt,1,0,"tr",14),t._uU(69,"\n                    "),t.YNc(70,Et,1,0,"tr",15),t._uU(71,"\n                "),t.qZA(),t._uU(72,"\n            "),t.qZA(),t._uU(73,"\n        "),t.qZA(),t._uU(74,"\n    "),t.qZA(),t._uU(75,"\n    "),t.TgZ(76,"ct-section-footer"),t._uU(77,"\n        "),t.TgZ(78,"ct-section-footer-row"),t._uU(79,"\n            "),t.TgZ(80,"ct-flex",16),t._uU(81,"\n                "),t.TgZ(82,"ct-flex-item"),t._uU(83,"\n                    "),t.TgZ(84,"ct-table-pagination",17),t.NdJ("next",function(){t.CHM(n);const m=t.oxw();return t.KtG(m.nextPage())})("prev",function(){t.CHM(n);const m=t.oxw();return t.KtG(m.prevPage())}),t.qZA(),t._uU(85,"\n                "),t.qZA(),t._uU(86,"\n                "),t.TgZ(87,"ct-flex-item"),t._uU(88,"\n                    "),t.YNc(89,vt,1,0,"ng-container",18),t._uU(90,"\n                "),t.qZA(),t._uU(91,"\n            "),t.qZA(),t._uU(92,"\n        "),t.qZA(),t._uU(93,"\n    "),t.qZA(),t._uU(94,"\n"),t.qZA()}if(2&o){const n=t.oxw(),r=t.MAs(6);t.xp6(15),t.Q6J("isWaiting",n.isLoading),t.xp6(2),t.Q6J("dataSource",n.dataSource),t.xp6(51),t.Q6J("matHeaderRowDef",n.columnsToDisplay),t.xp6(2),t.Q6J("matRowDefColumns",n.columnsToDisplay),t.xp6(14),t.Q6J("pageableDefault",n.experimentsResult.items)("globalDisable",!1),t.xp6(5),t.Q6J("ngTemplateOutlet",r)}}function Ot(o,i){if(1&o){const n=t.EpF();t.TgZ(0,"ct-flex-item"),t._uU(1,"\n            "),t.TgZ(2,"button",28),t.NdJ("click",function(){t.CHM(n);const m=t.oxw().element,p=t.oxw();return t.KtG(p.produce(m))}),t._uU(3,"Produce"),t.qZA(),t._uU(4,"\n        "),t.qZA()}}function bt(o,i){if(1&o){const n=t.EpF();t.TgZ(0,"ct-flex-item"),t._uU(1,"\n            "),t.TgZ(2,"button",28),t.NdJ("click",function(){t.CHM(n);const m=t.oxw().element,p=t.oxw();return t.KtG(p.start(m))}),t._uU(3,"Start"),t.qZA(),t._uU(4,"\n        "),t.qZA()}}function Mt(o,i){if(1&o){const n=t.EpF();t.TgZ(0,"ct-flex-item"),t._uU(1,"\n            "),t.TgZ(2,"button",28),t.NdJ("click",function(){t.CHM(n);const m=t.oxw().element,p=t.oxw();return t.KtG(p.stop(m))}),t._uU(3,"Stop"),t.qZA(),t._uU(4,"\n        "),t.qZA()}}const Pt=function(o){return[o,"edit"]},yt=function(o,i,n){return[o,"source-code",i,"exec-context",n,"state"]};function wt(o,i){if(1&o){const n=t.EpF();t._uU(0,"\n    "),t.TgZ(1,"ct-flex",24),t._uU(2,"\n        "),t.YNc(3,Ot,5,0,"ct-flex-item",0),t._uU(4,"\n        "),t.YNc(5,bt,5,0,"ct-flex-item",0),t._uU(6,"\n        "),t.YNc(7,Mt,5,0,"ct-flex-item",0),t._uU(8,"\n        "),t.TgZ(9,"ct-flex-item"),t._uU(10,"\n            "),t.TgZ(11,"a",25),t._uU(12,"Edit"),t.qZA(),t._uU(13,"\n        "),t.qZA(),t._uU(14,"\n        "),t.TgZ(15,"ct-flex-item"),t._uU(16,"\n            "),t.TgZ(17,"a",25),t._uU(18,"Task\n                State"),t.qZA(),t._uU(19,"\n        "),t.qZA(),t._uU(20,"\n        "),t.TgZ(21,"ct-flex-item"),t._uU(22,"\n            "),t.TgZ(23,"button",26),t.NdJ("click",function(){const p=t.CHM(n).element,O=t.oxw();return t.KtG(O.clone(p))}),t._uU(24,"Clone"),t.qZA(),t._uU(25,"\n        "),t.qZA(),t._uU(26,"\n        "),t.TgZ(27,"ct-flex-item"),t._uU(28,"\n            "),t.TgZ(29,"button",27),t.NdJ("click",function(){const p=t.CHM(n).element,O=t.oxw();return t.KtG(O.delete(p))}),t._uU(30,"\n                "),t.TgZ(31,"mat-icon"),t._uU(32,"delete"),t.qZA(),t._uU(33,"\n            "),t.qZA(),t._uU(34,"\n        "),t.qZA(),t._uU(35,"\n    "),t.qZA(),t._uU(36,"\n")}if(2&o){const n=i.element,r=t.oxw();t.xp6(3),t.Q6J("ngIf",n.experiment.execState===r.ExecContextState.NONE),t.xp6(2),t.Q6J("ngIf",n.experiment.execState===r.ExecContextState.PRODUCED||n.experiment.execState===r.ExecContextState.STOPPED),t.xp6(2),t.Q6J("ngIf",n.experiment.execState===r.ExecContextState.STARTED),t.xp6(4),t.Q6J("routerLink",t.VKq(5,Pt,n.experiment.id)),t.xp6(6),t.Q6J("routerLink",t.kEZ(7,yt,n.experiment.id,n.experiment.sourceCodeId,n.experiment.execContextId))}}function qt(o,i){if(1&o&&(t._uU(0,"\n    "),t.TgZ(1,"button",29),t._uU(2,"Add\n        Experiment"),t.qZA(),t._uU(3,"\n")),2&o){const n=t.oxw();t.xp6(1),t.Q6J("disabled",n.isLoading)}}class y extends K.S{constructor(i,n,r){super(r),(0,c.Z)(this,"dialog",void 0),(0,c.Z)(this,"experimentsService",void 0),(0,c.Z)(this,"authenticationService",void 0),(0,c.Z)(this,"ExecContextState",R.B),(0,c.Z)(this,"experimentsResult",void 0),(0,c.Z)(this,"dataSource",new v.by([])),(0,c.Z)(this,"columnsToDisplay",["id","name","createdOn","code","description","execState","bts"]),this.dialog=i,this.experimentsService=n,this.authenticationService=r}ngOnInit(){this.updateTable(0)}updateTable(i){this.setIsLoadingStart(),this.experimentsService.getExperiments(i.toString()).subscribe({next:n=>{this.experimentsResult=n,this.dataSource=new v.by(n.items.content||[])},complete:()=>{this.setIsLoadingEnd()}})}delete(i){this.experimentsService.deleteCommit(i.experiment.id.toString()).subscribe({complete:()=>this.updateTable(this.experimentsResult.items.number)})}clone(i){var n;this.experimentsService.experimentCloneCommit(null===(n=i.experiment.id)||void 0===n?void 0:n.toString()).subscribe({complete:()=>this.updateTable(this.experimentsResult.items.number)})}produce(i){this.execContextTargetExecState(i.experiment.id.toString(),R.B.PRODUCED.toLowerCase())}start(i){this.execContextTargetExecState(i.experiment.id.toString(),R.B.STARTED.toLowerCase())}stop(i){this.execContextTargetExecState(i.experiment.id.toString(),R.B.STOPPED.toLowerCase())}execContextTargetExecState(i,n){this.experimentsService.execContextTargetExecState(i,n).subscribe({complete:()=>this.updateTable(this.experimentsResult.items.number)})}nextPage(){this.updateTable(this.experimentsResult.items.number+1)}prevPage(){this.updateTable(this.experimentsResult.items.number-1)}}(0,c.Z)(y,"\u0275fac",function(i){return new(i||y)(t.Y36(nt.uw),t.Y36(Z),t.Y36(j.$h))}),(0,c.Z)(y,"\u0275cmp",t.Xpm({type:y,selectors:[["experiments-view"]],features:[t.qOj],decls:7,vars:1,consts:[[4,"ngIf"],["actionsTemplate",""],["addButtonTemplate",""],[3,"isWaiting"],["mat-table","mat-table",1,"mat-table",3,"dataSource"],["matColumnDef","id"],["mat-header-cell","",4,"matHeaderCellDef"],["mat-cell","",4,"matCellDef"],["matColumnDef","name"],["matColumnDef","createdOn"],["matColumnDef","execState"],["matColumnDef","code"],["matColumnDef","description"],["matColumnDef","bts"],["mat-header-row","mat-header-row",4,"matHeaderRowDef"],["mat-row","mat-row",4,"matRowDef","matRowDefColumns"],["justify-content","space-between"],[3,"pageableDefault","globalDisable","next","prev"],[4,"ngTemplateOutlet"],["mat-header-cell",""],["mat-cell",""],[3,"ngTemplateOutlet","ngTemplateOutletContext"],["mat-header-row","mat-header-row"],["mat-row","mat-row"],["justify-content","flex-end","gap","9px"],["mat-flat-button","","color","primary","size","forTableRow",3,"routerLink"],["mat-flat-button","","size","forTableRow","color","primary",3,"click"],["mat-flat-button","","size","forTableRow","color","warn",3,"click"],["mat-flat-button","","color","primary","size","forTableRow",3,"click"],["mat-flat-button","","color","primary","wide","wide","routerLink","add",3,"disabled"]],template:function(i,n){1&i&&(t.YNc(0,At,95,7,"ct-section",0),t._uU(1,"\n\n\n\n\n\n"),t.YNc(2,wt,37,11,"ng-template",null,1,t.W1O),t._uU(4,"\n\n\n\n\n\n"),t.YNc(5,qt,4,1,"ng-template",null,2,t.W1O)),2&i&&t.Q6J("ngIf",n.experimentsResult)},dependencies:[e.O5,e.tP,s.rH,A.U,b.n,_.V,d.R,ot.a,D.i,I.Z,S.t,N.B,J.i,F._,it.C,q.zs,q.lW,at.Hw,v.BZ,v.fO,v.as,v.w1,v.Dz,v.nj,v.ge,v.ev,v.XQ,v.Gk,e.uU]})),function(o,i,n,r){var O,m=arguments.length,p=m<3?i:null===r?r=Object.getOwnPropertyDescriptor(i,n):r;if("object"==typeof Reflect&&"function"==typeof Reflect.decorate)p=Reflect.decorate(o,i,n,r);else for(var k=o.length-1;k>=0;k--)(O=o[k])&&(p=(m<3?O(p):m>3?O(i,n,p):O(i,n))||p);m>3&&p&&Object.defineProperty(i,n,p)}([(0,et.K)({question:o=>`Do you want to delete Experiment\xa0#${o.experiment.id}`,rejectTitle:"Cancel",resolveTitle:"Delete"}),Y("design:type",Function),Y("design:paramtypes",[Object]),Y("design:returntype",void 0)],y.prototype,"delete",null);var Rt=a(1623),Dt=a(653);const W=[{path:"",component:y},{path:"add",component:X,data:{backConfig:["../"]}},{path:":experimentId/edit",component:tt,data:{backConfig:["../","../"]}},{path:":experimentId/source-code/:sourceCodeId/exec-context/:execContextId/state",component:(()=>{class o{constructor(n){(0,c.Z)(this,"activatedRoute",void 0),(0,c.Z)(this,"sourceCodeId",void 0),(0,c.Z)(this,"execContextId",void 0),this.activatedRoute=n}ngOnInit(){this.sourceCodeId=this.activatedRoute.snapshot.paramMap.get("sourceCodeId"),this.execContextId=this.activatedRoute.snapshot.paramMap.get("execContextId")}}return(0,c.Z)(o,"\u0275fac",function(n){return new(n||o)(t.Y36(s.gz))}),(0,c.Z)(o,"\u0275cmp",t.Xpm({type:o,selectors:[["experiment-state"]],decls:1,vars:2,consts:[[3,"sourceCodeId","execContextId"]],template:function(n,r){1&n&&t._UZ(0,"ct-state-of-tasks",0),2&n&&t.Q6J("sourceCodeId",r.sourceCodeId)("execContextId",r.execContextId)},dependencies:[Dt.$]})),o})(),data:{backConfig:["../","../","../","../","../","../"]}}];let z=(()=>{class o{}return(0,c.Z)(o,"\u0275fac",function(n){return new(n||o)}),(0,c.Z)(o,"\u0275mod",t.oAB({type:o})),(0,c.Z)(o,"\u0275inj",t.cJS({imports:[s.Bz.forChild(W),s.Bz]})),o})(),It=(()=>{class o{}return(0,c.Z)(o,"\u0275fac",function(n){return new(n||o)}),(0,c.Z)(o,"\u0275mod",t.oAB({type:o})),(0,c.Z)(o,"\u0275inj",t.cJS({imports:[e.ez,z,Rt.E,x.$,l.u5,l.UX,U.aw.forChild({})]})),o})()}}]);