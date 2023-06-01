"use strict";(self.webpackChunkmetaheuristic_app=self.webpackChunkmetaheuristic_app||[]).push([[351],{9272:(R,P,c)=>{c.d(P,{K:()=>v});var r=c(1180),e=c(5938),g=c(4128),i=c(4650),M=c(4859);let h=(()=>{class m{constructor(U,Z){(0,r.Z)(this,"dialogRef",void 0),(0,r.Z)(this,"data",void 0),this.dialogRef=U,this.data=Z}onNoClick(){this.dialogRef.close(0)}onYesClick(){this.dialogRef.close(1)}}return(0,r.Z)(m,"\u0275fac",function(U){return new(U||m)(i.Y36(e.so),i.Y36(e.WI))}),(0,r.Z)(m,"\u0275cmp",i.Xpm({type:m,selectors:[["app-dialog-confirmation"]],decls:19,vars:3,consts:[[1,"mat-dialog-content"],[1,"mat-dialog-actions"],[1,"mat-dialog-action"],["mat-stroked-button","mat-stroked-button",3,"click"],["mat-flat-button","mat-flat-button","color","warn",3,"click"]],template:function(U,Z){1&U&&(i.TgZ(0,"div",0),i._uU(1),i.qZA(),i._UZ(2,"br"),i._uU(3,"\n"),i.TgZ(4,"div",1),i._uU(5,"\n  "),i.TgZ(6,"div",2),i._uU(7,"\n    "),i.TgZ(8,"button",3),i.NdJ("click",function(){return Z.onNoClick()}),i._uU(9),i.qZA(),i._uU(10,"\n  "),i.qZA(),i._uU(11,"\n  "),i.TgZ(12,"div",2),i._uU(13,"\n    "),i.TgZ(14,"button",4),i.NdJ("click",function(){return Z.onYesClick()}),i._uU(15),i.qZA(),i._uU(16,"\n  "),i.qZA(),i._uU(17,"\n"),i.qZA(),i._uU(18," ")),2&U&&(i.xp6(1),i.Oqu(Z.data.question),i.xp6(8),i.Oqu(Z.data.rejectTitle),i.xp6(6),i.Oqu(Z.data.resolveTitle))},dependencies:[M.lW],styles:[".mat-dialog-actions[_ngcontent-%COMP%]{display:flex;justify-content:space-between;margin:0 -8px}.mat-dialog-action[_ngcontent-%COMP%]{flex:1;margin:0 8px}.mat-dialog-action[_ngcontent-%COMP%] > *[_ngcontent-%COMP%]{width:100%}.mat-dialog-content[_ngcontent-%COMP%]{max-height:auto!important;overflow:hidden!important}"]})),m})();function v(m){return function(U,Z,x){const b=x.value;return x.value=function(...a){let d={text:"",params:!1};"string"==typeof m.question(...a)?d.text=m.question(...a):d=m.question(...a),this.dialog||function f(){throw new Error("\ncomponent require MatDialog\n\nimport { MatDialog } from '@angular/material';\n...\nconstructor(\n    ...\n    private dialog: MatDialog\n    ...\n) {}\n                ")}(),d.params&&!this.translate&&function t(){throw new Error("\ncomponent require TranslateService\n\nimport { TranslateService } from '@ngx-translate/core';\n...\nconstructor(\n    ...\n    private translate: TranslateService\n    ...\n) {}\n                ")}(),this.translate?(0,g.D)(this.translate.get(d.text,d.params),this.translate.get(m.resolveTitle),this.translate.get(m.rejectTitle)).subscribe(_=>{this.dialog.open(h,{width:"500px",data:{question:_[0],resolveTitle:_[1],rejectTitle:_[2]}}).afterClosed().subscribe(w=>{w&&b.apply(this,a)})}):this.dialog.open(h,{width:"500px",data:{question:d.text,resolveTitle:m.resolveTitle,rejectTitle:m.rejectTitle}}).afterClosed().subscribe(_=>{_&&b.apply(this,a)})},x}}},1023:(R,P,c)=>{c.d(P,{R:()=>b});var r=c(1180),e=c(4650),g=c(6895),i=c(1439),M=c(9185),h=c(305),v=c(9863);function f(a,d){if(1&a&&(e.TgZ(0,"ct-section-body-row"),e._uU(1,"\n            "),e.TgZ(2,"div",1),e._uU(3,"STATUS:"),e.qZA(),e._uU(4,"\n            "),e.TgZ(5,"ct-heading"),e._uU(6),e.qZA(),e._uU(7,"\n        "),e.qZA()),2&a){const _=e.oxw(2);e.xp6(6),e.Oqu(_.content.status)}}function t(a,d){if(1&a&&(e.TgZ(0,"ct-section-body-row"),e._uU(1,"\n            "),e.TgZ(2,"div",1),e._uU(3,"STATUS:"),e.qZA(),e._uU(4,"\n            "),e.TgZ(5,"ct-heading"),e._uU(6),e.qZA(),e._uU(7,"\n        "),e.qZA()),2&a){const _=e.oxw(2);e.xp6(6),e.Oqu(_.content.validationResult.status)}}function m(a,d){if(1&a&&(e.TgZ(0,"li"),e._uU(1),e.qZA()),2&a){const _=d.$implicit;e.xp6(1),e.Oqu(_)}}function T(a,d){if(1&a&&(e.TgZ(0,"ct-section-body-row"),e._uU(1,"\n            "),e.TgZ(2,"div",1),e._uU(3,"ERRORS:"),e.qZA(),e._uU(4,"\n            "),e.TgZ(5,"ul",2),e._uU(6,"\n                "),e.YNc(7,m,2,1,"li",3),e._uU(8,"\n            "),e.qZA(),e._uU(9,"\n        "),e.qZA()),2&a){const _=e.oxw(2);e.xp6(7),e.Q6J("ngForOf",_.content.errorMessages)}}function U(a,d){if(1&a&&(e.TgZ(0,"li"),e._uU(1),e.qZA()),2&a){const _=d.$implicit;e.xp6(1),e.Oqu(_)}}function Z(a,d){if(1&a&&(e.TgZ(0,"ct-section-body-row"),e._uU(1,"\n            "),e.TgZ(2,"div",1),e._uU(3,"INFO:"),e.qZA(),e._uU(4,"\n            "),e.TgZ(5,"ul",2),e._uU(6,"\n                "),e.YNc(7,U,2,1,"li",3),e._uU(8,"\n            "),e.qZA(),e._uU(9,"\n        "),e.qZA()),2&a){const _=e.oxw(2);e.xp6(7),e.Q6J("ngForOf",_.content.infoMessages)}}function x(a,d){if(1&a&&(e.TgZ(0,"ct-section"),e._uU(1,"\n    "),e.TgZ(2,"ct-section-body"),e._uU(3,"\n        "),e.YNc(4,f,8,1,"ct-section-body-row",0),e._uU(5,"\n\n        "),e.YNc(6,t,8,1,"ct-section-body-row",0),e._uU(7,"\n\n        "),e.YNc(8,T,10,1,"ct-section-body-row",0),e._uU(9,"\n\n        "),e.YNc(10,Z,10,1,"ct-section-body-row",0),e._uU(11,"\n    "),e.qZA(),e._uU(12,"\n"),e.qZA()),2&a){const _=e.oxw();e.xp6(4),e.Q6J("ngIf",_.content.status),e.xp6(2),e.Q6J("ngIf",_.content.validationResult),e.xp6(2),e.Q6J("ngIf",null==_.content.errorMessages?null:_.content.errorMessages.length),e.xp6(2),e.Q6J("ngIf",null==_.content.infoMessages?null:_.content.infoMessages.length)}}let b=(()=>{class a{constructor(){(0,r.Z)(this,"content",void 0)}}return(0,r.Z)(a,"\u0275fac",function(_){return new(_||a)}),(0,r.Z)(a,"\u0275cmp",e.Xpm({type:a,selectors:[["ct-rest-status"]],inputs:{content:"content"},decls:1,vars:1,consts:[[4,"ngIf"],[2,"font-size","75%","opacity","0.75"],[1,"code"],[4,"ngFor","ngForOf"]],template:function(_,w){1&_&&e.YNc(0,x,13,4,"ct-section",0),2&_&&e.Q6J("ngIf",w.content)},dependencies:[g.sg,g.O5,i.U,M.n,h.Z,v._],styles:[".code[_ngcontent-%COMP%]{font-size:75%;line-height:1.8;font-family:Courier New,Courier,monospace}"]})),a})()},7530:(R,P,c)=>{c.d(P,{a:()=>M});var r=c(1180),e=c(4650),g=c(1572);const i=["*"];let M=(()=>{class h{constructor(f){(0,r.Z)(this,"changeDetector",void 0),(0,r.Z)(this,"isWaiting",void 0),(0,r.Z)(this,"state",{wait:!1}),(0,r.Z)(this,"isFnMode",void 0),this.changeDetector=f}ngOnInit(){void 0===this.isWaiting?this.isFnMode=!0:(this.isFnMode=!1,this.state.wait=this.isWaiting)}ngOnDestroy(){this.changeDetector.detach()}ngOnChanges(){this.isFnMode||(this.state.wait=this.isWaiting)}wait(){this.isFnMode&&(this.state.wait=!0,this.changeDetector.destroyed||this.changeDetector.detectChanges())}show(){this.isFnMode&&(this.state.wait=!1,this.changeDetector.destroyed||this.changeDetector.detectChanges())}}return(0,r.Z)(h,"\u0275fac",function(f){return new(f||h)(e.Y36(e.sBO))}),(0,r.Z)(h,"\u0275cmp",e.Xpm({type:h,selectors:[["ct-table"]],inputs:{isWaiting:"isWaiting"},features:[e.TTD],ngContentSelectors:i,decls:12,vars:2,consts:[[1,"ct-table"],[1,"ct-table__body"],[1,"ct-table__wait"]],template:function(f,t){1&f&&(e.F$t(),e.TgZ(0,"div",0),e._uU(1,"\n    "),e.TgZ(2,"div",1),e._uU(3,"\n        "),e.Hsn(4),e._uU(5,"\n    "),e.qZA(),e._uU(6,"\n    "),e.TgZ(7,"div",2),e._uU(8,"\n        "),e._UZ(9,"mat-spinner"),e._uU(10,"\n    "),e.qZA(),e._uU(11,"\n"),e.qZA()),2&f&&e.ekj("ct-table--wait",t.state.wait)},dependencies:[g.Ou],styles:["[_nghost-%COMP%]{display:block;position:relative;margin:0;overflow-y:auto}[_nghost-%COMP%]     .mat-table{width:100%;border-collapse:collapse;background:none}[_nghost-%COMP%]     .mat-header-row{height:auto}[_nghost-%COMP%]     .mat-header-cell, [_nghost-%COMP%]     .mat-cell, [_nghost-%COMP%]     .mat-footer-cell{border-bottom-width:1px;border-bottom-style:solid;border-top-width:1px;border-top-style:solid;padding:9px;font-family:sans-serif;font-size:14.94px;line-height:18px}[_nghost-%COMP%]     .mat-header-cell{white-space:nowrap;font-weight:700;vertical-align:baseline;color:inherit}[_nghost-%COMP%]     .mat-cell{vertical-align:baseline}[_nghost-%COMP%]     .mat-header-cell:first-child, [_nghost-%COMP%]     .mat-cell:first-child{padding-left:9px}[_nghost-%COMP%]     .mat-header-cell:last-child, [_nghost-%COMP%]     .mat-cell:last-child{padding-right:9px}[_nghost-%COMP%]     .mat-row{height:auto}.light-theme[_nghost-%COMP%]     .mat-header-cell, .light-theme   [_nghost-%COMP%]     .mat-header-cell, .light-theme[_nghost-%COMP%]     .mat-cell, .light-theme   [_nghost-%COMP%]     .mat-cell, .light-theme[_nghost-%COMP%]     .mat-footer-cell, .light-theme   [_nghost-%COMP%]     .mat-footer-cell{border-top-color:#f0f0f0;border-bottom-color:#f0f0f0}.dark-theme[_nghost-%COMP%]     .mat-header-cell, .dark-theme   [_nghost-%COMP%]     .mat-header-cell, .dark-theme[_nghost-%COMP%]     .mat-cell, .dark-theme   [_nghost-%COMP%]     .mat-cell, .dark-theme[_nghost-%COMP%]     .mat-footer-cell, .dark-theme   [_nghost-%COMP%]     .mat-footer-cell{border-top-color:#474747;border-bottom-color:#474747}.ct-table[_ngcontent-%COMP%]{position:relative}.ct-table__wait[_ngcontent-%COMP%]{position:absolute;top:0;left:0;width:100%;height:100%;display:none;align-items:center;justify-content:center;background-color:#ffffff1a;overflow:hidden}.ct-table--wait[_ngcontent-%COMP%]   .ct-table__body[_ngcontent-%COMP%]{opacity:.5}.ct-table--wait[_ngcontent-%COMP%]   .ct-table__wait[_ngcontent-%COMP%]{display:flex}"]})),h})()},2351:(R,P,c)=>{c.r(P),c.d(P,{ProcessorsModule:()=>yt,ProcessorsRoutes:()=>Q,ProcessorsRoutingModule:()=>K});var r=c(1180),e=c(6895),g=c(4006),i=c(9299),M=c(3081),h=c(6423),v=c(7528),f=c(2340),t=c(4650),m=c(529);const T=n=>`${f.N.baseUrl}dispatcher${n}`;let U=(()=>{class n{constructor(o){(0,r.Z)(this,"http",void 0),this.http=o}init(o){return this.http.get(T("/processors"),{params:{page:o}})}getProcessor(o){return this.http.get(T(`/processor/${o}`))}formCommit(o){return this.http.post(T("/processor-form-commit/"),o)}deleteProcessorCommit(o){return this.http.post(T("/processor-delete-commit"),(0,v.P)({id:o}))}processProcessorBulkDeleteCommit(o){return this.http.post(T("/processor-bulk-delete-commit"),(0,v.P)({processorIds:o.join()}))}}return(0,r.Z)(n,"\u0275fac",function(o){return new(o||n)(t.LFG(m.eN))}),(0,r.Z)(n,"\u0275prov",t.Yz7({token:n,factory:n.\u0275fac,providedIn:"root"})),n})();var Z=c(3735),x=c(1439),b=c(9185),a=c(6890),d=c(2555),_=c(2379),w=c(5510),E=c(6264),S=c(305),N=c(5014),Y=c(8868),I=c(3370),J=c(9863),k=c(1023),q=c(4859),W=c(284),j=c(9549),L=c(9349);function $(n,s){if(1&n){const o=t.EpF();t.TgZ(0,"ct-cols"),t._uU(1,"\n    "),t.TgZ(2,"ct-col",1),t._uU(3,"\n        "),t.TgZ(4,"ct-section"),t._uU(5,"\n            "),t.TgZ(6,"ct-section-header"),t._uU(7,"\n                "),t.TgZ(8,"ct-section-header-row"),t._uU(9,"\n                    "),t.TgZ(10,"ct-heading"),t._uU(11),t.qZA(),t._uU(12,"\n                "),t.qZA(),t._uU(13,"\n            "),t.qZA(),t._uU(14,"\n            "),t.TgZ(15,"ct-section-body"),t._uU(16,"\n                "),t.TgZ(17,"ct-section-body-row"),t._uU(18,"\n                    "),t.TgZ(19,"ct-section-content"),t._uU(20,"\n                        "),t.TgZ(21,"mat-form-field",2),t._uU(22,"\n                            "),t.TgZ(23,"mat-label"),t._uU(24,"Description "),t.qZA(),t._uU(25,"\n                            "),t.TgZ(26,"textarea",3),t.NdJ("ngModelChange",function(u){t.CHM(o);const p=t.oxw();return t.KtG(p.processor.description=u)}),t._uU(27," "),t.qZA(),t._uU(28,"\n                        "),t.qZA(),t._uU(29,"\n                    "),t.qZA(),t._uU(30,"\n                "),t.qZA(),t._uU(31,"\n            "),t.qZA(),t._uU(32,"\n            "),t.TgZ(33,"ct-section-footer"),t._uU(34,"\n                "),t.TgZ(35,"ct-section-footer-row"),t._uU(36,"\n                    "),t.TgZ(37,"ct-flex",4),t._uU(38,"\n                        "),t.TgZ(39,"ct-flex-item"),t._uU(40,"\n                            "),t.TgZ(41,"button",5),t.NdJ("click",function(){t.CHM(o);const u=t.oxw();return t.KtG(u.back())}),t._uU(42,"Cancel"),t.qZA(),t._uU(43,"\n                        "),t.qZA(),t._uU(44,"\n                        "),t.TgZ(45,"ct-flex-item"),t._uU(46,"\n                            "),t.TgZ(47,"button",6),t.NdJ("click",function(){t.CHM(o);const u=t.oxw();return t.KtG(u.save())}),t._uU(48,"Save"),t.qZA(),t._uU(49,"\n                        "),t.qZA(),t._uU(50,"\n                    "),t.qZA(),t._uU(51,"\n                "),t.qZA(),t._uU(52,"\n            "),t.qZA(),t._uU(53,"\n        "),t.qZA(),t._uU(54,"\n    "),t.qZA(),t._uU(55,"\n    "),t.TgZ(56,"ct-col",1),t._uU(57,"\n        "),t._UZ(58,"ct-rest-status",7),t._uU(59,"\n    "),t.qZA(),t._uU(60,"\n"),t.qZA()}if(2&n){const o=t.oxw();t.xp6(11),t.hij("Edit Processor ",o.processor?o.processor.id:"...",""),t.xp6(15),t.Q6J("ngModel",o.processor.description),t.xp6(32),t.Q6J("content",o.processorResponse)}}let H=(()=>{class n{constructor(o,l,u){(0,r.Z)(this,"route",void 0),(0,r.Z)(this,"processorsService",void 0),(0,r.Z)(this,"router",void 0),(0,r.Z)(this,"processor",void 0),(0,r.Z)(this,"processorResponse",void 0),this.route=o,this.processorsService=l,this.router=u}ngOnInit(){this.processorsService.getProcessor(this.route.snapshot.paramMap.get("id")).subscribe(o=>{this.processorResponse=o,this.processor=o.processor})}save(){this.processorsService.formCommit(this.processor).subscribe(o=>{var l;null!==(l=o.errorMessages)&&void 0!==l&&l.length?this.processorResponse=o:this.back()})}back(){this.router.navigate(["/dispatcher","processors"])}}return(0,r.Z)(n,"\u0275fac",function(o){return new(o||n)(t.Y36(i.gz),t.Y36(U),t.Y36(i.F0))}),(0,r.Z)(n,"\u0275cmp",t.Xpm({type:n,selectors:[["edit-processor"]],decls:1,vars:1,consts:[[4,"ngIf"],["size","6"],["appearance","outline"],["matInput","matInput","value","","cdkTextareaAutosize","cdkTextareaAutosize","cdkAutosizeMinRows","5",3,"ngModel","ngModelChange"],["justify-content","flex-end","gap","8px"],["mat-stroked-button","mat-stroked-button",3,"click"],["mat-flat-button","mat-flat-button","color","primary",3,"click"],[3,"content"]],template:function(o,l){1&o&&t.YNc(0,$,61,3,"ct-cols",0),2&o&&t.Q6J("ngIf",l.processorResponse)},dependencies:[e.O5,Z.a,x.U,b.n,a.V,d.R,_.t,w.W,E.i,S.Z,N.t,Y.B,I.i,J._,k.R,q.lW,W.Nt,j.KE,j.hX,L.IC,g.Fj,g.JJ,g.On],styles:["mat-form-field[_ngcontent-%COMP%]{width:100%}textarea[_ngcontent-%COMP%]{overflow:hidden!important;padding:0!important}"]})),n})();var C=c(3626),F=c(9272),z=c(5017),G=c(8318),X=c(3788),V=c(5938),tt=c(9160),et=c(7530),ot=c(7824),nt=c(6709),st=c(7392),ct=c(455),B=function(n,s,o,l){var A,u=arguments.length,p=u<3?s:null===l?l=Object.getOwnPropertyDescriptor(s,o):l;if("object"==typeof Reflect&&"function"==typeof Reflect.decorate)p=Reflect.decorate(n,s,o,l);else for(var D=n.length-1;D>=0;D--)(A=n[D])&&(p=(u<3?A(p):u>3?A(s,o,p):A(s,o))||p);return u>3&&p&&Object.defineProperty(s,o,p),p},y=function(n,s){if("object"==typeof Reflect&&"function"==typeof Reflect.metadata)return Reflect.metadata(n,s)};function it(n,s){if(1&n){const o=t.EpF();t.TgZ(0,"th",21),t._uU(1,"\n                            "),t.TgZ(2,"mat-checkbox",22),t.NdJ("change",function(u){t.CHM(o);const p=t.oxw(2);return t.KtG(u?p.masterToggle():null)}),t._uU(3,"\n                            "),t.qZA(),t._uU(4,"\n                        "),t.qZA()}if(2&n){const o=t.oxw(2);t.xp6(2),t.Q6J("checked",o.selection.hasValue()&&o.isAllSelected())("indeterminate",o.selection.hasValue()&&!o.isAllSelected())}}function rt(n,s){if(1&n){const o=t.EpF();t.TgZ(0,"td",23),t._uU(1,"\n                            "),t.TgZ(2,"mat-checkbox",24),t.NdJ("click",function(u){return u.stopPropagation()})("change",function(u){const A=t.CHM(o).$implicit,D=t.oxw(2);return t.KtG(u?D.selection.toggle(A):null)}),t._uU(3,"\n                            "),t.qZA(),t._uU(4,"\n                        "),t.qZA()}if(2&n){const o=s.$implicit,l=t.oxw(2);t.xp6(2),t.Q6J("checked",l.selection.isSelected(o))}}function at(n,s){1&n&&(t.TgZ(0,"th",21),t._uU(1,"Id"),t.qZA())}function lt(n,s){if(1&n&&(t.TgZ(0,"td",23),t._uU(1),t.qZA()),2&n){const o=s.$implicit;t.xp6(1),t.Oqu(o.processor.id)}}function _t(n,s){1&n&&(t.TgZ(0,"th",21),t._uU(1,"\n                            "),t.TgZ(2,"div",25),t._uU(3,"Last seen"),t.qZA(),t._uU(4,"\n                            "),t.TgZ(5,"div",25),t._uU(6,"IP"),t.qZA(),t._uU(7,"\n                            "),t.TgZ(8,"div",25),t._uU(9,"Host name"),t.qZA(),t._uU(10,"\n                        "),t.qZA())}function ut(n,s){if(1&n&&(t.TgZ(0,"td",23),t._uU(1,"\n                            "),t.TgZ(2,"div"),t._uU(3),t.ALo(4,"date"),t.qZA(),t._uU(5,"\n                            "),t.TgZ(6,"div"),t._uU(7),t.qZA(),t._uU(8,"\n                            "),t.TgZ(9,"div"),t._uU(10),t.qZA(),t._uU(11,"\n                        "),t.qZA()),2&n){const o=s.$implicit;t.xp6(3),t.Oqu(t.xi3(4,3,o.lastSeen,"MMM d, y, hh:mm")),t.xp6(4),t.Oqu(o.ip||"\u2014"),t.xp6(3),t.Oqu(o.host||"\u2014")}}function dt(n,s){1&n&&(t.TgZ(0,"th",21),t._uU(1,"Description"),t.qZA())}function mt(n,s){if(1&n&&(t.TgZ(0,"td",23),t._uU(1),t.qZA()),2&n){const o=s.$implicit;t.xp6(1),t.hij("",o.processor.description||"\u2014"," ")}}function pt(n,s){1&n&&(t.TgZ(0,"td",23),t._uU(1," "),t.qZA())}function gt(n,s){if(1&n&&(t.TgZ(0,"td",23),t._uU(1,"\n                            "),t.TgZ(2,"b"),t._uU(3,"Status of Processor:"),t.qZA(),t._uU(4,"\n                            "),t.TgZ(5,"ct-pre-10px"),t._uU(6),t.qZA(),t._uU(7,"\n                        "),t.qZA()),2&n){const o=s.$implicit,l=t.oxw(2);t.uIk("colspan",l.columnsToDisplay.length-1),t.xp6(6),t.hij("",o.processor.status||"\u2014"," ")}}function ht(n,s){1&n&&(t.TgZ(0,"th",21),t._uU(1,"\n                            "),t.TgZ(2,"div"),t._uU(3,"Is active?"),t.qZA(),t._uU(4,"\n                            "),t.TgZ(5,"div"),t._uU(6,"problems with functions?"),t.qZA(),t._uU(7,"\n                            "),t.TgZ(8,"div"),t._uU(9,"Is blacklisted?"),t.qZA(),t._uU(10,"\n                            "),t.TgZ(11,"div"),t._uU(12,"Reason"),t.qZA(),t._uU(13,"\n                        "),t.qZA())}function ft(n,s){if(1&n&&(t.TgZ(0,"td",23),t._uU(1,"\n                            "),t.TgZ(2,"div"),t._uU(3),t.qZA(),t._uU(4,"\n                            "),t.TgZ(5,"div"),t._uU(6),t.qZA(),t._uU(7,"\n                            "),t.TgZ(8,"div"),t._uU(9),t.qZA(),t._uU(10,"\n                            "),t.TgZ(11,"div",26),t._uU(12),t.qZA(),t._uU(13,"\n                        "),t.qZA()),2&n){const o=s.$implicit;t.xp6(2),t.Tol(o.active?"alert-success":"alert-danger"),t.xp6(1),t.hij(" \n                                ",o.active?"Yes":"No","\n                            "),t.xp6(2),t.Tol(o.functionProblem?"alert-danger":"alert-success"),t.xp6(1),t.hij("\n                                ",o.functionProblem?"Yes":"No"," "),t.xp6(2),t.Tol(o.blacklisted?"alert-danger":"alert-success"),t.xp6(1),t.hij("\n                                ",o.blacklisted?"Yes":"No"," "),t.xp6(3),t.hij(" \n                                ",o.blacklistReason,"\n                            ")}}function Ut(n,s){1&n&&(t.TgZ(0,"th",21),t._uU(1,"Cores "),t.qZA())}function Zt(n,s){if(1&n&&(t.TgZ(0,"div"),t._uU(1,"\n                                "),t.TgZ(2,"span"),t._uU(3),t.qZA(),t._uU(4,"\n                            "),t.qZA()),2&n){const o=s.$implicit;t.xp6(3),t.Oqu("#"+o.id+":\xa0"+o.code)}}function Ct(n,s){if(1&n&&(t.TgZ(0,"td",23),t._uU(1,"\n                            "),t.YNc(2,Zt,5,1,"div",27),t._uU(3,"\n                        "),t.qZA()),2&n){const o=s.$implicit;t.xp6(2),t.Q6J("ngForOf",o.cores)}}function Tt(n,s){1&n&&(t.TgZ(0,"th",21),t._uU(1," "),t.qZA())}const xt=function(n){return["/dispatcher/processors",n,"edit"]};function vt(n,s){if(1&n){const o=t.EpF();t.TgZ(0,"td",23),t._uU(1,"\n                            "),t.TgZ(2,"ct-flex",28),t._uU(3,"\n                                "),t.TgZ(4,"ct-flex-item"),t._uU(5,"\n                                    "),t.TgZ(6,"button",29),t._uU(7,"\n                                        "),t.TgZ(8,"mat-icon"),t._uU(9,"edit"),t.qZA(),t._uU(10,"\n                                    "),t.qZA(),t._uU(11,"\n                                "),t.qZA(),t._uU(12,"\n                                "),t.TgZ(13,"ct-flex-item"),t._uU(14,"\n                                    "),t.TgZ(15,"button",30),t.NdJ("click",function(){const p=t.CHM(o).$implicit,A=t.oxw(2);return t.KtG(A.delete(p))}),t._uU(16,"\n                                        "),t.TgZ(17,"mat-icon"),t._uU(18,"delete"),t.qZA(),t._uU(19,"\n                                    "),t.qZA(),t._uU(20,"\n                                "),t.qZA(),t._uU(21,"\n                            "),t.qZA(),t._uU(22,"\n                        "),t.qZA()}if(2&n){const o=s.$implicit;t.xp6(6),t.Q6J("routerLink",t.VKq(1,xt,o.processor.id))}}function Pt(n,s){1&n&&t._UZ(0,"tr",31)}function At(n,s){1&n&&t._UZ(0,"tr",32)}function Mt(n,s){if(1&n&&t._UZ(0,"tr",33),2&n){const o=t.oxw(2);t.Q6J("hidden",!o.showStatusOfProcessor)}}function bt(n,s){if(1&n){const o=t.EpF();t.TgZ(0,"ct-section"),t._uU(1,"\n    "),t.TgZ(2,"ct-section-header"),t._uU(3,"\n        "),t.TgZ(4,"ct-section-header-row"),t._uU(5,"\n            "),t.TgZ(6,"ct-flex",1),t._uU(7,"\n                "),t.TgZ(8,"ct-flex-item"),t._uU(9,"\n                    "),t.TgZ(10,"ct-heading"),t._uU(11,"Processors"),t.qZA(),t._uU(12,"\n                "),t.qZA(),t._uU(13,"\n                "),t.TgZ(14,"ct-flex-item"),t._uU(15,"\n                    "),t.TgZ(16,"mat-slide-toggle",2),t.NdJ("ngModelChange",function(u){t.CHM(o);const p=t.oxw();return t.KtG(p.showStatusOfProcessor=u)}),t._uU(17,"\n                        Show current status of processor\n                    "),t.qZA(),t._uU(18,"\n                "),t.qZA(),t._uU(19,"\n            "),t.qZA(),t._uU(20,"\n        "),t.qZA(),t._uU(21,"\n    "),t.qZA(),t._uU(22,"\n    "),t.TgZ(23,"ct-section-body"),t._uU(24,"\n        "),t.TgZ(25,"ct-section-body-row"),t._uU(26,"\n            "),t.TgZ(27,"ct-table",3),t._uU(28,"\n                "),t.TgZ(29,"table",4),t._uU(30,"\n                    "),t.ynx(31,5),t._uU(32,"\n                        "),t.YNc(33,it,5,2,"th",6),t._uU(34,"\n                        "),t.YNc(35,rt,5,1,"td",7),t._uU(36,"\n                    "),t.BQk(),t._uU(37,"\n                    "),t.ynx(38,8),t._uU(39,"\n                        "),t.YNc(40,at,2,0,"th",6),t._uU(41,"\n                        "),t.YNc(42,lt,2,1,"td",7),t._uU(43,"\n                    "),t.BQk(),t._uU(44,"\n                    "),t.ynx(45,9),t._uU(46,"\n                        "),t.YNc(47,_t,11,0,"th",6),t._uU(48,"\n                        "),t.YNc(49,ut,12,6,"td",7),t._uU(50,"\n                    "),t.BQk(),t._uU(51,"\n                    "),t.ynx(52,10),t._uU(53,"\n                        "),t.YNc(54,dt,2,0,"th",6),t._uU(55,"\n                        "),t.YNc(56,mt,2,1,"td",7),t._uU(57,"\n                    "),t.BQk(),t._uU(58,"\n                    "),t.ynx(59,11),t._uU(60,"\n                        "),t.YNc(61,pt,2,0,"td",7),t._uU(62,"\n                    "),t.BQk(),t._uU(63,"\n                    "),t.ynx(64,12),t._uU(65,"\n                        "),t.YNc(66,gt,8,2,"td",7),t._uU(67,"\n                    "),t.BQk(),t._uU(68,"\n                    "),t.ynx(69,13),t._uU(70,"\n                        "),t.YNc(71,ht,14,0,"th",6),t._uU(72,"\n                        "),t.YNc(73,ft,14,10,"td",7),t._uU(74,"\n                    "),t.BQk(),t._uU(75,"\n                    "),t.ynx(76,14),t._uU(77,"\n                        "),t.YNc(78,Ut,2,0,"th",6),t._uU(79,"\n                        "),t.YNc(80,Ct,4,1,"td",7),t._uU(81,"\n                    "),t.BQk(),t._uU(82,"\n                    "),t.ynx(83,15),t._uU(84,"\n                        "),t.YNc(85,Tt,2,0,"th",6),t._uU(86,"\n                        "),t.YNc(87,vt,23,3,"td",7),t._uU(88,"\n                    "),t.BQk(),t._uU(89,"\n                    "),t.YNc(90,Pt,1,0,"tr",16),t._uU(91,"\n                    "),t.YNc(92,At,1,0,"tr",17),t._uU(93,"\n                    "),t.YNc(94,Mt,1,1,"tr",18),t._uU(95,"\n                "),t.qZA(),t._uU(96,"\n            "),t.qZA(),t._uU(97,"\n        "),t.qZA(),t._uU(98,"\n    "),t.qZA(),t._uU(99,"\n    "),t.TgZ(100,"ct-section-footer"),t._uU(101,"\n        "),t.TgZ(102,"ct-section-footer-row"),t._uU(103,"\n            "),t.TgZ(104,"button",19),t.NdJ("click",function(){t.CHM(o);const u=t.oxw();return t.KtG(u.deleteMany())}),t._uU(105,"\n                    Delete Checked\n            "),t.qZA(),t._uU(106,"\n        "),t.qZA(),t._uU(107,"\n        "),t.TgZ(108,"ct-section-footer-row"),t._uU(109,"\n            "),t.TgZ(110,"ct-flex-item"),t._uU(111,"\n                "),t.TgZ(112,"ct-table-pagination",20),t.NdJ("prev",function(){t.CHM(o);const u=t.oxw();return t.KtG(u.prevPage())})("next",function(){t.CHM(o);const u=t.oxw();return t.KtG(u.nextPage())}),t._uU(113,"\n                "),t.qZA(),t._uU(114,"\n            "),t.qZA(),t._uU(115,"\n        "),t.qZA(),t._uU(116,"\n    "),t.qZA(),t._uU(117,"\n"),t.qZA()}if(2&n){const o=t.oxw();t.xp6(16),t.Q6J("ngModel",o.showStatusOfProcessor),t.xp6(11),t.Q6J("isWaiting",o.isLoading),t.xp6(2),t.Q6J("dataSource",o.dataSource),t.xp6(61),t.Q6J("matHeaderRowDef",o.columnsToDisplay),t.xp6(2),t.Q6J("matRowDefColumns",o.columnsToDisplay),t.xp6(2),t.Q6J("matRowDefColumns",o.secondColumnsToDisplay),t.xp6(10),t.Q6J("disabled",o.isLoading||!o.selection.selected.length),t.xp6(8),t.Q6J("pageableDefault",o.processorResult.items)}}class O extends G.S{constructor(s,o,l){super(l),(0,r.Z)(this,"dialog",void 0),(0,r.Z)(this,"processorsService",void 0),(0,r.Z)(this,"authenticationService",void 0),(0,r.Z)(this,"processorResult",void 0),(0,r.Z)(this,"showStatusOfProcessor",!1),(0,r.Z)(this,"dataSource",new C.by([])),(0,r.Z)(this,"selection",new z.Ov(!0,[])),(0,r.Z)(this,"columnsToDisplay",["check","id","ip","description","reason","cores","bts"]),(0,r.Z)(this,"secondColumnsToDisplay",["empty","env"]),this.dialog=s,this.processorsService=o,this.authenticationService=l}ngOnInit(){this.updateTable(0)}updateTable(s){this.isLoading=!0,this.processorsService.init(s.toString()).subscribe(o=>{this.processorResult=o;const l=o.items.content||[];l.length&&(this.dataSource=new C.by(l)),this.isLoading=!1})}applyFilter(s){this.dataSource.filter=s.trim().toLowerCase()}isAllSelected(){return this.selection.selected.length===this.dataSource.data.length}masterToggle(){this.isAllSelected()?this.selection.clear():this.dataSource.data.forEach(s=>this.selection.select(s))}nextPage(){this.updateTable(this.processorResult.items.number+1)}prevPage(){this.updateTable(this.processorResult.items.number-1)}delete(s){this.processorsService.deleteProcessorCommit(s.processor.id.toString()).subscribe(()=>this.updateTable(this.processorResult.items.number))}deleteMany(){this.processorsService.processProcessorBulkDeleteCommit(this.selection.selected.map(s=>s.processor.id.toString())).subscribe(()=>this.updateTable(this.processorResult.items.number))}}(0,r.Z)(O,"\u0275fac",function(s){return new(s||O)(t.Y36(V.uw),t.Y36(U),t.Y36(X.$h))}),(0,r.Z)(O,"\u0275cmp",t.Xpm({type:O,selectors:[["processors"]],features:[t.qOj],decls:1,vars:1,consts:[[4,"ngIf"],["justify-content","space-between","align-items","center"],[3,"ngModel","ngModelChange"],[3,"isWaiting"],["mat-table","","multiTemplateDataRows","multiTemplateDataRows",3,"dataSource"],["matColumnDef","check"],["mat-header-cell","",4,"matHeaderCellDef"],["mat-cell","",4,"matCellDef"],["matColumnDef","id"],["matColumnDef","ip"],["matColumnDef","description"],["matColumnDef","empty"],["matColumnDef","env"],["matColumnDef","reason"],["matColumnDef","cores"],["matColumnDef","bts"],["mat-header-row","",4,"matHeaderRowDef"],["class","first-row","mat-row","",4,"matRowDef","matRowDefColumns"],["class","second-row","mat-row","",3,"hidden",4,"matRowDef","matRowDefColumns"],["mat-flat-button","","color","warn","title","Delete Checked",3,"disabled","click"],[3,"pageableDefault","prev","next"],["mat-header-cell",""],["color","warn",3,"checked","indeterminate","change"],["mat-cell",""],["color","warn",3,"checked","click","change"],[1,"no-wrap"],[1,"alert-danger"],[4,"ngFor","ngForOf"],["justify-content","flex-end","gap","unit(1)"],["mat-icon-button","","size","forTableRow","color","primary",3,"routerLink"],["mat-icon-button","","size","forTableRow","color","warn",3,"click"],["mat-header-row",""],["mat-row","",1,"first-row"],["mat-row","",1,"second-row",3,"hidden"]],template:function(s,o){1&s&&t.YNc(0,bt,118,8,"ct-section",0),2&s&&t.Q6J("ngIf",o.processorResult)},dependencies:[e.sg,e.O5,i.rH,tt.E,x.U,b.n,a.V,d.R,et.a,E.i,S.Z,N.t,Y.B,I.i,J._,ot.C,q.lW,q.RK,nt.oG,st.Hw,ct.Rr,C.BZ,C.fO,C.as,C.w1,C.Dz,C.nj,C.ge,C.ev,C.XQ,C.Gk,g.JJ,g.On,e.uU],styles:[".section[_ngcontent-%COMP%]   .first-row[_ngcontent-%COMP%]   td[_ngcontent-%COMP%]{border-width:0}.first-row[_ngcontent-%COMP%]   td[_ngcontent-%COMP%], .first-row[_ngcontent-%COMP%]   th[_ngcontent-%COMP%]{border-bottom:none}.second-row[_ngcontent-%COMP%]   td[_ngcontent-%COMP%], .second-row[_ngcontent-%COMP%]   th[_ngcontent-%COMP%]{border:none}.alert-success[_ngcontent-%COMP%]{background-color:#00800040;padding:0 2px}.alert-danger[_ngcontent-%COMP%]{background-color:#ff000040;padding:0 2px}mat-checkbox[_ngcontent-%COMP%]{padding-left:10px}"]})),B([(0,F.K)({question:n=>`Do you want to delete Processor\xa0#${n.processor.id}`,rejectTitle:"Cancel",resolveTitle:"Delete"}),y("design:type",Function),y("design:paramtypes",[Object]),y("design:returntype",void 0)],O.prototype,"delete",null),B([(0,F.K)({question:()=>"Do you want to delete Processors",rejectTitle:"Cancel",resolveTitle:"Delete"}),y("design:type",Function),y("design:paramtypes",[]),y("design:returntype",void 0)],O.prototype,"deleteMany",null);var Ot=c(1623);const Q=[{path:"",component:O},{path:":id/edit",component:H,data:{backConfig:["../","../"]}}];let K=(()=>{class n{}return(0,r.Z)(n,"\u0275fac",function(o){return new(o||n)}),(0,r.Z)(n,"\u0275mod",t.oAB({type:n})),(0,r.Z)(n,"\u0275inj",t.cJS({imports:[i.Bz.forChild(Q),i.Bz]})),n})(),yt=(()=>{class n{}return(0,r.Z)(n,"\u0275fac",function(o){return new(o||n)}),(0,r.Z)(n,"\u0275mod",t.oAB({type:n})),(0,r.Z)(n,"\u0275inj",t.cJS({imports:[e.ez,K,Ot.E,h.$,g.u5,g.UX,M.aw.forChild({})]})),n})()}}]);