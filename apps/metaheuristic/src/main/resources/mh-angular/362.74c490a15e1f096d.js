"use strict";(self.webpackChunkmetaheuristic_app=self.webpackChunkmetaheuristic_app||[]).push([[362],{2177:(E,x,c)=>{c.d(x,{B:()=>s});var s=(()=>{return(_=s||(s={})).ERROR="ERROR",_.UNKNOWN="UNKNOWN",_.NONE="NONE",_.PRODUCING="PRODUCING",_.PRODUCED="PRODUCED",_.STARTED="STARTED",_.STOPPED="STOPPED",_.FINISHED="FINISHED",_.DOESNT_EXIST="DOESNT_EXIST",s;var _})()},535:(E,x,c)=>{c.d(x,{C:()=>s});var s=(()=>{return(_=s||(s={}))[_.show=0]="show",_[_.loading=1]="loading",_[_.empty=2]="empty",_[_.wait=3]="wait",_[_.firstLoading=4]="firstLoading",s;var _})()},4620:(E,x,c)=>{c.d(x,{I:()=>C});var s=c(1180),_=c(3626),g=c(9272),m=c(2177),T=c(2231),h=c(4293),t=c(4650),f=c(9299),u=c(5938),Z=c(6895),r=c(4859),p=c(7392),U=c(266),D=c(1439),v=c(9185),A=c(6890),y=c(2555),P=c(7530),w=c(6264),M=c(305),I=c(5014),R=c(8868),N=c(3370),S=c(9863),q=c(653),O=function(n,o){if("object"==typeof Reflect&&"function"==typeof Reflect.metadata)return Reflect.metadata(n,o)};const K=["stateOfTasksTemplate"],W=["errorDialogTemplate"],Y=["nextTable"],L=["prevTable"];function Q(n,o){if(1&n&&(t.TgZ(0,"ct-section-header-row"),t._uU(1,"\n            "),t.TgZ(2,"table"),t._uU(3,"\n                "),t.TgZ(4,"tr"),t._uU(5,"\n                    "),t.TgZ(6,"td"),t._uU(7,"UID: "),t.qZA(),t._uU(8,"\n                    "),t.TgZ(9,"td"),t._uU(10),t.qZA(),t._uU(11,"\n                "),t.qZA(),t._uU(12,"\n                "),t.TgZ(13,"tr"),t._uU(14,"\n                    "),t.TgZ(15,"td"),t._uU(16,"Is valid:"),t.qZA(),t._uU(17,"\n                    "),t.TgZ(18,"td"),t._uU(19),t.qZA(),t._uU(20,"\n                "),t.qZA(),t._uU(21,"\n                "),t.TgZ(22,"tr"),t._uU(23,"\n                    "),t.TgZ(24,"td"),t._uU(25,"Type: "),t.qZA(),t._uU(26,"\n                    "),t.TgZ(27,"td"),t._uU(28),t.qZA(),t._uU(29,"\n                "),t.qZA(),t._uU(30,"\n            "),t.qZA(),t._uU(31,"\n        "),t.qZA()),2&n){const e=t.oxw();t.xp6(10),t.Oqu(e.response.sourceCodeUid),t.xp6(9),t.Oqu(e.response.sourceCodeValid),t.xp6(9),t.Oqu(e.response.sourceCodeType)}}function $(n,o){1&n&&(t.TgZ(0,"th",21),t._uU(1,"Id"),t.qZA())}function H(n,o){if(1&n&&(t.TgZ(0,"td",22),t._uU(1),t.qZA()),2&n){const e=o.$implicit;t.xp6(1),t.Oqu(e.id)}}function J(n,o){1&n&&(t.TgZ(0,"th",21),t._uU(1,"Created On"),t.qZA())}function G(n,o){if(1&n&&(t.TgZ(0,"td",22),t._uU(1),t.ALo(2,"date"),t.qZA()),2&n){const e=o.$implicit;t.xp6(1),t.hij("",t.xi3(2,1,e.createdOn,"MMM d, yyyy HH:mm:ss"),"\n                        ")}}function k(n,o){1&n&&(t.TgZ(0,"th",21),t._uU(1,"Is execContext"),t._UZ(2,"br"),t._uU(3,"Valid"),t.qZA())}function j(n,o){if(1&n&&(t.TgZ(0,"td",22),t._uU(1),t.qZA()),2&n){const e=o.$implicit;t.xp6(1),t.Oqu(e.valid)}}function F(n,o){1&n&&(t.TgZ(0,"th",21),t._uU(1,"ExecState"),t.qZA())}function z(n,o){if(1&n&&(t.TgZ(0,"td",22),t._uU(1),t.qZA()),2&n){const e=o.$implicit,i=t.oxw(2);t.xp6(1),t.Oqu(i.execState[e.execState])}}function V(n,o){1&n&&(t.TgZ(0,"th",21),t._uU(1,"Completed on"),t.qZA())}function X(n,o){if(1&n&&(t.TgZ(0,"span"),t._uU(1),t.ALo(2,"date"),t.qZA()),2&n){const e=t.oxw().$implicit;t.xp6(1),t.hij("\n                                ",t.xi3(2,1,e.completedOn,"MMM d, yyyy HH:mm:ss"),"\n                            ")}}function tt(n,o){if(1&n&&(t.TgZ(0,"td",22),t._uU(1,"\n                            "),t.YNc(2,X,3,4,"span",0),t._uU(3,"\n                        "),t.qZA()),2&n){const e=o.$implicit;t.xp6(2),t.Q6J("ngIf",null!==e.completedOn)}}function et(n,o){1&n&&t._UZ(0,"th",21)}function nt(n,o){if(1&n){const e=t.EpF();t.TgZ(0,"ct-flex-item"),t._uU(1,"\n                                    "),t.TgZ(2,"button",26),t.NdJ("click",function(l){t.CHM(e);const a=t.oxw().$implicit,d=t.oxw(2);return t.KtG(d.produce(a,l))}),t._uU(3,"Produce"),t.qZA(),t._uU(4,"\n                                "),t.qZA()}}function ot(n,o){if(1&n){const e=t.EpF();t.TgZ(0,"ct-flex-item"),t._uU(1,"\n                                    "),t.TgZ(2,"button",27),t.NdJ("click",function(l){t.CHM(e);const a=t.oxw().$implicit,d=t.oxw(2);return t.KtG(d.start(a,l))}),t._uU(3,"\n                                        "),t.TgZ(4,"mat-icon"),t._uU(5,"play_arrow"),t.qZA(),t._uU(6,"\n                                    "),t.qZA(),t._uU(7,"\n                                "),t.qZA()}}function _t(n,o){if(1&n){const e=t.EpF();t.TgZ(0,"ct-flex-item"),t._uU(1,"\n                                    "),t.TgZ(2,"button",27),t.NdJ("click",function(l){t.CHM(e);const a=t.oxw().$implicit,d=t.oxw(2);return t.KtG(d.stop(a,l))}),t._uU(3,"\n                                        "),t.TgZ(4,"mat-icon"),t._uU(5,"stop"),t.qZA(),t._uU(6,"\n                                    "),t.qZA(),t._uU(7,"\n                                "),t.qZA()}}function ct(n,o){if(1&n){const e=t.EpF();t.TgZ(0,"td",22),t._uU(1,"\n                            "),t.TgZ(2,"ct-flex",23),t._uU(3,"\n                                "),t.YNc(4,nt,5,0,"ct-flex-item",0),t._uU(5,"\n                                "),t.YNc(6,ot,8,0,"ct-flex-item",0),t._uU(7,"\n                                "),t.YNc(8,_t,8,0,"ct-flex-item",0),t._uU(9,"\n                                "),t.TgZ(10,"ct-flex-item"),t._uU(11,"\n                                    "),t.TgZ(12,"button",24),t.NdJ("click",function(){const a=t.CHM(e).$implicit,d=t.oxw(2);return t.KtG(d.stateOfTasks(a))}),t._uU(13,"\n                                        "),t.TgZ(14,"mat-icon"),t._uU(15,"list"),t.qZA(),t._uU(16,"\n                                    "),t.qZA(),t._uU(17,"\n                                "),t.qZA(),t._uU(18,"\n                                "),t.TgZ(19,"ct-flex-item"),t._uU(20,"\n                                    "),t.TgZ(21,"button",25),t.NdJ("click",function(){const a=t.CHM(e).$implicit,d=t.oxw(2);return t.KtG(d.delete(a))}),t._uU(22,"\n                                        "),t.TgZ(23,"mat-icon"),t._uU(24,"delete_forever"),t.qZA(),t._uU(25,"\n                                    "),t.qZA(),t._uU(26,"\n                                "),t.qZA(),t._uU(27,"\n                            "),t.qZA(),t._uU(28,"\n                        "),t.qZA()}if(2&n){const e=o.$implicit,i=t.oxw(2);t.xp6(4),t.Q6J("ngIf",e.execState==i.execState.NONE&&e.valid&&i.response.sourceCodeValid),t.xp6(2),t.Q6J("ngIf",e.execState==i.execState.PRODUCED||e.execState==i.execState.STOPPED),t.xp6(2),t.Q6J("ngIf",e.execState==i.execState.STARTED),t.xp6(13),t.Q6J("disabled",e.__deleted)}}function it(n,o){1&n&&t._UZ(0,"tr",28)}function st(n,o){1&n&&t._UZ(0,"tr",29),2&n&&t.ekj("mat-row--deleted",o.$implicit.__deleted)}function rt(n,o){if(1&n&&(t.TgZ(0,"ct-section-body-row"),t._uU(1,"\n            "),t.TgZ(2,"ct-table"),t._uU(3,"\n                "),t.TgZ(4,"table",10),t._uU(5,"\n                    "),t.ynx(6,11),t._uU(7,"\n                        "),t.YNc(8,$,2,0,"th",12),t._uU(9,"\n                        "),t.YNc(10,H,2,1,"td",13),t._uU(11,"\n                    "),t.BQk(),t._uU(12,"\n                    "),t.ynx(13,14),t._uU(14,"\n                        "),t.YNc(15,J,2,0,"th",12),t._uU(16,"\n                        "),t.YNc(17,G,3,4,"td",13),t._uU(18,"\n                    "),t.BQk(),t._uU(19,"\n                    "),t.ynx(20,15),t._uU(21,"\n                        "),t.YNc(22,k,4,0,"th",12),t._uU(23,"\n                        "),t.YNc(24,j,2,1,"td",13),t._uU(25,"\n                    "),t.BQk(),t._uU(26,"\n                    "),t.ynx(27,16),t._uU(28,"\n                        "),t.YNc(29,F,2,0,"th",12),t._uU(30,"\n                        "),t.YNc(31,z,2,1,"td",13),t._uU(32,"\n                    "),t.BQk(),t._uU(33,"\n                    "),t.ynx(34,17),t._uU(35,"\n                        "),t.YNc(36,V,2,0,"th",12),t._uU(37,"\n                        "),t.YNc(38,tt,4,1,"td",13),t._uU(39,"\n                    "),t.BQk(),t._uU(40,"\n                    "),t.ynx(41,18),t._uU(42,"\n                        "),t.YNc(43,et,1,0,"th",12),t._uU(44,"\n                        "),t.YNc(45,ct,29,4,"td",13),t._uU(46,"\n                    "),t.BQk(),t._uU(47,"\n                    "),t.YNc(48,it,1,0,"tr",19),t._uU(49,"\n                    "),t.YNc(50,st,1,2,"tr",20),t._uU(51,"\n                "),t.qZA(),t._uU(52,"\n            "),t.qZA(),t._uU(53,"\n        "),t.qZA()),2&n){const e=t.oxw();t.xp6(4),t.Q6J("dataSource",e.execContextTableSource),t.xp6(44),t.Q6J("matHeaderRowDef",e.execContextColumnsToDisplay),t.xp6(2),t.Q6J("matRowDefColumns",e.execContextColumnsToDisplay)}}function at(n,o){if(1&n&&(t._uU(0,"\n    "),t.ynx(1),t._uU(2,"\n        "),t._UZ(3,"ct-state-of-tasks",30),t._uU(4,"\n    "),t.BQk(),t._uU(5,"\n")),2&n){const e=t.oxw();t.xp6(3),t.Q6J("sourceCodeId",e.sourceCodeId)("execContextId",e.execContextId)}}const ut=function(n){return["/dispatcher","source-codes",n,"exec-context","add"]};class C{constructor(o,e,i,l,a){(0,s.Z)(this,"route",void 0),(0,s.Z)(this,"dialog",void 0),(0,s.Z)(this,"execContextService",void 0),(0,s.Z)(this,"sourceCodesService",void 0),(0,s.Z)(this,"router",void 0),(0,s.Z)(this,"stateOfTasksTemplate",void 0),(0,s.Z)(this,"errorDialogTemplate",void 0),(0,s.Z)(this,"sourceCodeId",void 0),(0,s.Z)(this,"nextTable",void 0),(0,s.Z)(this,"prevTable",void 0),(0,s.Z)(this,"execState",m.B),(0,s.Z)(this,"response",void 0),(0,s.Z)(this,"execContextTableSource",new _.by([])),(0,s.Z)(this,"execContextColumnsToDisplay",["id","createdOn","isExecContextValid","execState","completedOn","bts"]),(0,s.Z)(this,"execContextId",void 0),this.route=o,this.dialog=e,this.execContextService=i,this.sourceCodesService=l,this.router=a}ngOnInit(){this.getExecContexts(0)}getExecContexts(o){this.execContextService.execContexts(this.sourceCodeId,o.toString()).subscribe(e=>{this.response=e,e&&(this.execContextTableSource=new _.by(e.instances.content),this.prevTable.disabled=e.instances.first,this.nextTable.disabled=e.instances.last)})}delete(o){var e,i;this.execContextService.execContextDeleteCommit(this.sourceCodeId,null===(e=o.id)||void 0===e||null===(i=e.toString)||void 0===i?void 0:i.call(e)).subscribe(l=>this.getExecContexts(this.response.instances.number))}next(){this.getExecContexts(this.response.instances.number+1)}prev(){this.getExecContexts(this.response.instances.number-1)}runExecState(o,e){this.execContextService.execContextTargetState(this.sourceCodeId,e,o).subscribe(i=>this.getExecContexts(this.response.instances.number))}stop(o,e){e.target.disabled=!0,this.runExecState(o.id,"STOPPED")}start(o,e){e.target.disabled=!0,this.runExecState(o.id,"STARTED")}produce(o,e){e.target.disabled=!0,this.runExecState(o.id,"PRODUCING")}stateOfTasks(o){this.dialog.closeAll(),this.execContextId=o.id,this.dialog.open(this.stateOfTasksTemplate,{width:"90%"})}}(0,s.Z)(C,"\u0275fac",function(o){return new(o||C)(t.Y36(f.gz),t.Y36(u.uw),t.Y36(T.w),t.Y36(h.l),t.Y36(f.F0))}),(0,s.Z)(C,"\u0275cmp",t.Xpm({type:C,selectors:[["ct-exec-contexts"]],viewQuery:function(o,e){if(1&o&&(t.Gf(K,5),t.Gf(W,5),t.Gf(Y,7),t.Gf(L,7)),2&o){let i;t.iGM(i=t.CRH())&&(e.stateOfTasksTemplate=i.first),t.iGM(i=t.CRH())&&(e.errorDialogTemplate=i.first),t.iGM(i=t.CRH())&&(e.nextTable=i.first),t.iGM(i=t.CRH())&&(e.prevTable=i.first)}},inputs:{sourceCodeId:"sourceCodeId"},decls:71,vars:7,consts:[[4,"ngIf"],["justify-content","space-between"],["justify-content","flex-start","gap","8px"],["mat-flat-button","mat-flat-button","disabled","disabled",3,"click"],["prevTable",""],["nextTable",""],["mat-flat-button","mat-flat-button","color","primary",3,"disabled","routerLink"],["align","end"],["mat-flat-button","","color","primary","mat-dialog-close",""],["stateOfTasksTemplate",""],["mat-table","mat-table",1,"mat-table",3,"dataSource"],["matColumnDef","id"],["mat-header-cell","mat-header-cell",4,"matHeaderCellDef"],["mat-cell","mat-cell",4,"matCellDef"],["matColumnDef","createdOn"],["matColumnDef","isExecContextValid"],["matColumnDef","execState"],["matColumnDef","completedOn"],["matColumnDef","bts"],["mat-header-row","mat-header-row",4,"matHeaderRowDef"],["mat-row","mat-row",3,"mat-row--deleted",4,"matRowDef","matRowDefColumns"],["mat-header-cell","mat-header-cell"],["mat-cell","mat-cell"],["justify-content","flex-end","gap","9px"],["mat-icon-button","","matTooltip","Tasks","color","primary",3,"click"],["mat-icon-button","","color","warn","size","forTableRow","matTooltip","Delete ExecContext",3,"disabled","click"],["mat-flat-button","mat-flat-button","size","forTableRow","color","primary",3,"click"],["mat-icon-button","","size","forTableRow","color","primary",3,"click"],["mat-header-row","mat-header-row"],["mat-row","mat-row"],[3,"sourceCodeId","execContextId"]],template:function(o,e){1&o&&(t.TgZ(0,"ct-section"),t._uU(1,"\n    "),t.TgZ(2,"ct-section-header"),t._uU(3,"\n        "),t.TgZ(4,"ct-section-header-row"),t._uU(5,"\n            "),t.TgZ(6,"ct-heading"),t._uU(7),t.qZA(),t._uU(8,"\n        "),t.qZA(),t._uU(9,"\n        "),t.YNc(10,Q,32,3,"ct-section-header-row",0),t._uU(11,"\n    "),t.qZA(),t._uU(12,"\n    "),t.TgZ(13,"ct-section-body"),t._uU(14,"\n        "),t.YNc(15,rt,54,3,"ct-section-body-row",0),t._uU(16,"\n    "),t.qZA(),t._uU(17,"\n    "),t.TgZ(18,"ct-section-footer"),t._uU(19,"\n        "),t.TgZ(20,"ct-section-footer-row"),t._uU(21,"\n            "),t.TgZ(22,"ct-flex",1),t._uU(23,"\n                "),t.TgZ(24,"ct-flex-item"),t._uU(25,"\n                    "),t.TgZ(26,"ct-flex",2),t._uU(27,"\n                        "),t.TgZ(28,"ct-flex-item"),t._uU(29,"\n                            "),t.TgZ(30,"button",3,4),t.NdJ("click",function(){return e.prev()}),t._uU(32,"\n                                "),t.TgZ(33,"mat-icon"),t._uU(34,"arrow_back_ios"),t.qZA(),t._uU(35,"\n                            "),t.qZA(),t._uU(36,"\n                        "),t.qZA(),t._uU(37,"\n                        "),t.TgZ(38,"ct-flex-item"),t._uU(39,"\n                            "),t.TgZ(40,"button",3,5),t.NdJ("click",function(){return e.next()}),t._uU(42,"\n                                "),t.TgZ(43,"mat-icon"),t._uU(44,"arrow_forward_ios"),t.qZA(),t._uU(45,"\n                            "),t.qZA(),t._uU(46,"\n                        "),t.qZA(),t._uU(47,"\n                    "),t.qZA(),t._uU(48,"\n                "),t.qZA(),t._uU(49,"\n                "),t.TgZ(50,"ct-flex-item"),t._uU(51,"\n                    "),t.TgZ(52,"a",6),t._uU(53,"Add Exec\n                        Context"),t.qZA(),t._uU(54,"\n                "),t.qZA(),t._uU(55,"\n            "),t.qZA(),t._uU(56,"\n        "),t.qZA(),t._uU(57,"\n        "),t.TgZ(58,"ct-section-footer-row"),t._uU(59,"\n            "),t.TgZ(60,"mat-dialog-actions",7),t._uU(61,"\n                "),t.TgZ(62,"button",8),t._uU(63,"Close"),t.qZA(),t._uU(64,"\n            "),t.qZA(),t._uU(65,"\n        "),t.qZA(),t._uU(66,"\n    "),t.qZA(),t._uU(67,"\n"),t.qZA(),t._uU(68,"\n\n\n"),t.YNc(69,at,6,2,"ng-template",null,9,t.W1O)),2&o&&(t.xp6(7),t.hij("Exec Contexts in Source Code #",e.sourceCodeId,""),t.xp6(3),t.Q6J("ngIf",e.response),t.xp6(5),t.Q6J("ngIf",e.response),t.xp6(37),t.Q6J("disabled",!e.response)("routerLink",t.VKq(5,ut,e.sourceCodeId)))},dependencies:[Z.O5,r.zs,r.lW,r.RK,u.ZT,u.H8,p.Hw,_.BZ,_.fO,_.as,_.w1,_.Dz,_.nj,_.ge,_.ev,_.XQ,_.Gk,U.gM,f.rH,D.U,v.n,A.V,y.R,P.a,w.i,M.Z,I.t,R.B,N.i,S._,q.$,Z.uU],styles:[".mat-row--deleted[_ngcontent-%COMP%]{filter:grayscale(1);opacity:.33;pointer-events:none}ct-section-header[_ngcontent-%COMP%]   table[_ngcontent-%COMP%]   td[_ngcontent-%COMP%]{padding-right:1em}"]})),function(n,o,e,i){var d,l=arguments.length,a=l<3?o:null===i?i=Object.getOwnPropertyDescriptor(o,e):i;if("object"==typeof Reflect&&"function"==typeof Reflect.decorate)a=Reflect.decorate(n,o,e,i);else for(var b=n.length-1;b>=0;b--)(d=n[b])&&(a=(l<3?d(a):l>3?d(o,e,a):d(o,e))||a);l>3&&a&&Object.defineProperty(o,e,a)}([(0,g.K)({question:n=>`Do you want to delete ExecContext\xa0#${n.id}`,rejectTitle:"Cancel",resolveTitle:"Delete"}),O("design:type",Function),O("design:paramtypes",[Object]),O("design:returntype",void 0)],C.prototype,"delete",null)},4293:(E,x,c)=>{c.d(x,{l:()=>f});var s=c(1180),_=c(7528),g=c(2340),m=(()=>{return(u=m||(m={})).not_exist="not_exist",u.common="common",u.experiment="experiment",u.batch="batch",m;var u})(),T=c(4650),h=c(529);const t=u=>`${g.N.baseUrl}dispatcher/source-code/${u}`;let f=(()=>{class u{constructor(r){(0,s.Z)(this,"http",void 0),this.http=r}sourceCodes(r){return this.http.get(t("source-codes"),{params:{page:r}})}sourceCodeArchivedOnly(r){return this.http.get(t("source-codes-archived-only"),{params:{page:r}})}edit(r){return this.http.get(t(`source-code/${r}`))}validate(r){return this.http.get(t(`source-code-validate/${r}`))}addFormCommit(r){return this.http.post(t("source-code-add-commit"),(0,_.P)({source:r}))}editFormCommit(r,p){return this.http.post(t("source-code-edit-commit"),(0,_.P)({sourceCodeId:r,source:p}))}deleteCommit(r){return this.http.post(t("source-code-delete-commit"),(0,_.P)({id:r}))}archiveCommit(r){return this.http.post(t("source-code-archive-commit"),(0,_.P)({id:r}))}uploadSourceCode(r){return this.http.post(t("source-code-upload-from-file"),(0,_.P)({file:r}))}getSourceCodeType(r,p){let U=m.common;return p.batches.includes(r)&&(U=m.batch),p.experiments.includes(r)&&(U=m.experiment),U}}return(0,s.Z)(u,"\u0275fac",function(r){return new(r||u)(T.LFG(h.eN))}),(0,s.Z)(u,"\u0275prov",T.Yz7({token:u,factory:u.\u0275fac,providedIn:"root"})),u})()}}]);