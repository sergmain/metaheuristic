"use strict";(self.webpackChunkmetaheuristic_app=self.webpackChunkmetaheuristic_app||[]).push([[438],{1438:(Ct,Z,c)=>{c.r(Z),c.d(Z,{FunctionsModule:()=>Ft,FunctionsRoutes:()=>N,FunctionsRoutingModule:()=>J});var i=c(1180),m=c(6895),f=c(4006),p=c(9299),Q=c(3081),w=c(2199),O=c(7528),D=c(2340),t=c(4650),q=c(529);const r=n=>`${D.N.baseUrl}dispatcher/function/${n}`;let h=(()=>{class n{constructor(e){(0,i.Z)(this,"http",void 0),this.http=e}getFunctions(e){return this.http.get(r("functions"),{params:{page:e}})}deleteCommit(e){return this.http.get(r(`function-delete/${e}`))}uploadFunction(e){return this.http.post(r("function-upload-from-file"),(0,O.P)({file:e}))}}return(0,i.Z)(n,"\u0275fac",function(e){return new(e||n)(t.LFG(q.eN))}),(0,i.Z)(n,"\u0275prov",t.Yz7({token:n,factory:n.\u0275fac,providedIn:"root"})),n})();var T=c(1439),x=c(9185),v=c(6890),A=c(2555),I=c(2379),b=c(5510),j=c(1099),F=c(6264),C=c(305),y=c(5014),R=c(8868),M=c(3370),S=c(9863),P=c(1023),Y=c(4859);const B=["fileUpload"];function $(n,o){if(1&n&&(t.TgZ(0,"ct-col",0),t._uU(1,"\n        "),t._UZ(2,"ct-rest-status",6),t._uU(3,"\n    "),t.qZA()),2&n){const e=t.oxw();t.xp6(2),t.Q6J("content",e.response)}}let z=(()=>{class n{constructor(e,u){(0,i.Z)(this,"functionsService",void 0),(0,i.Z)(this,"router",void 0),(0,i.Z)(this,"response",void 0),(0,i.Z)(this,"fileUpload",void 0),this.functionsService=e,this.router=u}cancel(){this.router.navigate(["/dispatcher","functions"])}upload(){this.functionsService.uploadFunction(this.fileUpload.fileInput.nativeElement.files[0]).subscribe(e=>{this.response=e,e.status===w.o.OK&&this.cancel()})}}return(0,i.Z)(n,"\u0275fac",function(e){return new(e||n)(t.Y36(h),t.Y36(p.F0))}),(0,i.Z)(n,"\u0275cmp",t.Xpm({type:n,selectors:[["add-function"]],viewQuery:function(e,u){if(1&e&&t.Gf(B,7),2&e){let s;t.iGM(s=t.CRH())&&(u.fileUpload=s.first)}},decls:49,vars:2,consts:[["size","6"],["fileUpload",""],["justify-content","flex-end","gap","8px"],["mat-stroked-button","mat-stroked-button",3,"click"],["mat-flat-button","mat-flat-button","color","primary",3,"disabled","click"],["size","6",4,"ngIf"],[3,"content"]],template:function(e,u){if(1&e&&(t.TgZ(0,"ct-cols"),t._uU(1,"\n    "),t.TgZ(2,"ct-col",0),t._uU(3,"\n        "),t.TgZ(4,"ct-section"),t._uU(5,"\n            "),t.TgZ(6,"ct-section-header"),t._uU(7,"\n                "),t.TgZ(8,"ct-section-header-row"),t._uU(9,"\n                    "),t.TgZ(10,"ct-heading"),t._uU(11,"Add Function"),t.qZA(),t._uU(12,"\n                "),t.qZA(),t._uU(13,"\n            "),t.qZA(),t._uU(14,"\n            "),t.TgZ(15,"ct-section-body"),t._uU(16,"\n                "),t.TgZ(17,"ct-section-body-row"),t._uU(18,"\n                    "),t._UZ(19,"ct-file-upload",null,1),t._uU(21,"\n                "),t.qZA(),t._uU(22,"\n            "),t.qZA(),t._uU(23,"\n            "),t.TgZ(24,"ct-section-footer"),t._uU(25,"\n                "),t.TgZ(26,"ct-section-footer-row"),t._uU(27,"\n                    "),t.TgZ(28,"ct-flex",2),t._uU(29,"\n                        "),t.TgZ(30,"ct-flex-item"),t._uU(31,"\n                            "),t.TgZ(32,"button",3),t.NdJ("click",function(){return u.cancel()}),t._uU(33,"Cancel "),t.qZA(),t._uU(34,"\n                        "),t.qZA(),t._uU(35,"\n                        "),t.TgZ(36,"ct-flex-item"),t._uU(37,"\n                            "),t.TgZ(38,"button",4),t.NdJ("click",function(){return u.upload()}),t._uU(39,"Upload\n                                function"),t.qZA(),t._uU(40,"\n                        "),t.qZA(),t._uU(41,"\n                    "),t.qZA(),t._uU(42,"\n                "),t.qZA(),t._uU(43,"\n            "),t.qZA(),t._uU(44,"\n        "),t.qZA(),t._uU(45,"\n    "),t.qZA(),t._uU(46,"\n    "),t.YNc(47,$,4,1,"ct-col",5),t._uU(48,"\n"),t.qZA()),2&e){const s=t.MAs(20);t.xp6(38),t.Q6J("disabled",!(null!=s&&null!=s.fileInput&&null!=s.fileInput.nativeElement&&null!=s.fileInput.nativeElement.files&&s.fileInput.nativeElement.files.length)),t.xp6(9),t.Q6J("ngIf",u.response)}},dependencies:[m.O5,T.U,x.n,v.V,A.R,I.t,b.W,j.E,F.i,C.Z,y.t,R.B,M.i,S._,P.R,Y.lW],styles:[".file-names[_ngcontent-%COMP%]{margin-left:1em;font-size:125%;padding:8px}"]})),n})();var l=c(3626),G=c(9272),E=c(8318),H=c(3788),W=c(4190),L=c(5938),K=c(4853),X=c(7530),V=c(1336),k=c(7392),tt=c(455),g=function(n,o){if("object"==typeof Reflect&&"function"==typeof Reflect.metadata)return Reflect.metadata(n,o)};function et(n,o){1&n&&t.GkF(0)}function ot(n,o){if(1&n&&(t.TgZ(0,"ct-flex-item"),t._uU(1,"\n                            "),t.YNc(2,et,1,0,"ng-container",6),t._uU(3,"\n                        "),t.qZA()),2&n){t.oxw(2);const e=t.MAs(6);t.xp6(2),t.Q6J("ngTemplateOutlet",e)}}function ct(n,o){1&n&&(t.TgZ(0,"ct-section-header-row"),t._uU(1,"\n            "),t.TgZ(2,"ct-alert",7),t._uU(3,"Upload and deletion of functions are disabled, assetMode is 'replicated'."),t.qZA(),t._uU(4,"\n        "),t.qZA())}function it(n,o){1&n&&t.GkF(0)}function ut(n,o){1&n&&t.GkF(0)}function st(n,o){if(1&n&&(t.TgZ(0,"ct-flex-item"),t._uU(1,"\n                    "),t.YNc(2,ut,1,0,"ng-container",6),t._uU(3,"\n                "),t.qZA()),2&n){t.oxw(2);const e=t.MAs(6);t.xp6(2),t.Q6J("ngTemplateOutlet",e)}}function lt(n,o){if(1&n){const e=t.EpF();t.TgZ(0,"ct-section"),t._uU(1,"\n    "),t.TgZ(2,"ct-section-header"),t._uU(3,"\n        "),t.TgZ(4,"ct-section-header-row"),t._uU(5,"\n            "),t.TgZ(6,"ct-flex",3),t._uU(7,"\n                "),t.TgZ(8,"ct-flex-item"),t._uU(9,"\n                    "),t.TgZ(10,"ct-heading"),t._uU(11,"Functions"),t.qZA(),t._uU(12,"\n                "),t.qZA(),t._uU(13,"\n                "),t.TgZ(14,"ct-flex-item"),t._uU(15,"\n                    "),t.TgZ(16,"ct-flex",4),t._uU(17,"\n                        "),t.TgZ(18,"ct-flex-item"),t._uU(19,"\n                            "),t.TgZ(20,"mat-slide-toggle",5),t.NdJ("ngModelChange",function(s){t.CHM(e);const a=t.oxw();return t.KtG(a.showParams=s)}),t._uU(21,"Show Params"),t.qZA(),t._uU(22,"\n                        "),t.qZA(),t._uU(23,"\n                        "),t.YNc(24,ot,4,1,"ct-flex-item",0),t._uU(25,"\n                    "),t.qZA(),t._uU(26,"\n                "),t.qZA(),t._uU(27,"\n            "),t.qZA(),t._uU(28,"\n        "),t.qZA(),t._uU(29,"\n        "),t.YNc(30,ct,5,0,"ct-section-header-row",0),t._uU(31,"\n    "),t.qZA(),t._uU(32,"\n    "),t.TgZ(33,"ct-section-body"),t._uU(34,"\n        "),t.TgZ(35,"ct-section-body-row"),t._uU(36,"\n            "),t.YNc(37,it,1,0,"ng-container",6),t._uU(38,"\n        "),t.qZA(),t._uU(39,"\n    "),t.qZA(),t._uU(40,"\n    "),t.TgZ(41,"ct-section-footer"),t._uU(42,"\n        "),t.TgZ(43,"ct-section-footer-row"),t._uU(44,"\n            "),t.TgZ(45,"ct-flex",3),t._uU(46,"\n                "),t.TgZ(47,"ct-flex-item"),t._uU(48,"\n                    "),t._uU(49,"\n                "),t.qZA(),t._uU(50,"\n                "),t.YNc(51,st,4,1,"ct-flex-item",0),t._uU(52,"\n            "),t.qZA(),t._uU(53,"\n        "),t.qZA(),t._uU(54,"\n    "),t.qZA(),t._uU(55,"\n"),t.qZA()}if(2&n){const e=t.oxw(),u=t.MAs(3);t.xp6(20),t.Q6J("ngModel",e.showParams),t.xp6(4),t.Q6J("ngIf",!e.dispatcherAssetModeService.isReplicated(e.functionsResult.assetMode)),t.xp6(6),t.Q6J("ngIf",e.dispatcherAssetModeService.isReplicated(e.functionsResult.assetMode)),t.xp6(7),t.Q6J("ngTemplateOutlet",u),t.xp6(14),t.Q6J("ngIf",!e.dispatcherAssetModeService.isReplicated(e.functionsResult.assetMode))}}function at(n,o){1&n&&(t.TgZ(0,"th",18),t._uU(1,"\n                    Code\n                "),t.qZA())}function dt(n,o){if(1&n&&(t.TgZ(0,"td",19),t._uU(1),t.qZA()),2&n){const e=o.$implicit;t.xp6(1),t.Oqu(e.code)}}function mt(n,o){1&n&&(t.TgZ(0,"th",18),t._uU(1,"\n                    Type\n                "),t.qZA())}function ft(n,o){if(1&n&&(t.TgZ(0,"td",19),t._uU(1),t.qZA()),2&n){const e=o.$implicit;t.xp6(1),t.Oqu(e.type)}}function pt(n,o){1&n&&(t.TgZ(0,"th",18),t._uU(1,"\n                    Params\n                "),t.qZA())}function _t(n,o){if(1&n&&(t.TgZ(0,"td",19),t._uU(1,"\n                    "),t.TgZ(2,"div",20),t._uU(3,"\n                        "),t.TgZ(4,"ct-pre"),t._uU(5),t.qZA(),t._uU(6,"\n                    "),t.qZA(),t._uU(7,"\n                "),t.qZA()),2&n){const e=o.$implicit,u=t.oxw(2);t.xp6(2),t.Q6J("hidden",!u.showParams),t.xp6(3),t.Oqu(e.params)}}function rt(n,o){1&n&&t._UZ(0,"th",18)}function gt(n,o){if(1&n){const e=t.EpF();t.TgZ(0,"ct-flex",22),t._uU(1,"\n                        "),t.TgZ(2,"ct-flex-item"),t._uU(3,"\n                            "),t.TgZ(4,"button",23),t.NdJ("click",function(){t.CHM(e);const s=t.oxw().$implicit,a=t.oxw(2);return t.KtG(a.delete(s))}),t._uU(5,"\n                                "),t.TgZ(6,"mat-icon"),t._uU(7,"delete"),t.qZA(),t._uU(8,"\n                            "),t.qZA(),t._uU(9,"\n                        "),t.qZA(),t._uU(10,"\n                    "),t.qZA()}}function Ut(n,o){if(1&n&&(t.TgZ(0,"td",19),t._uU(1,"\n                    "),t.YNc(2,gt,11,0,"ct-flex",21),t._uU(3,"\n                "),t.qZA()),2&n){const e=t.oxw(2);t.xp6(2),t.Q6J("ngIf",!e.dispatcherAssetModeService.isReplicated(e.functionsResult.assetMode))}}function Zt(n,o){1&n&&t._UZ(0,"tr",24)}function ht(n,o){if(1&n&&t._UZ(0,"tr",25),2&n){const e=o.$implicit,u=t.oxw(2);t.ekj("deleted-table-row",u.deletedRows.includes(e))}}function Tt(n,o){if(1&n&&(t._uU(0,"\n    "),t.TgZ(1,"ct-table",8),t._uU(2,"\n        "),t.TgZ(3,"table",9),t._uU(4,"\n            "),t.ynx(5,10),t._uU(6,"\n                "),t.YNc(7,at,2,0,"th",11),t._uU(8,"\n                "),t.YNc(9,dt,2,1,"td",12),t._uU(10,"\n            "),t.BQk(),t._uU(11,"\n            "),t.ynx(12,13),t._uU(13,"\n                "),t.YNc(14,mt,2,0,"th",11),t._uU(15,"\n                "),t.YNc(16,ft,2,1,"td",12),t._uU(17,"\n            "),t.BQk(),t._uU(18,"\n            "),t.ynx(19,14),t._uU(20,"\n                "),t.YNc(21,pt,2,0,"th",11),t._uU(22,"\n                "),t.YNc(23,_t,8,2,"td",12),t._uU(24,"\n            "),t.BQk(),t._uU(25,"\n            "),t.ynx(26,15),t._uU(27,"\n                "),t.YNc(28,rt,1,0,"th",11),t._uU(29,"\n                "),t.YNc(30,Ut,4,1,"td",12),t._uU(31,"\n            "),t.BQk(),t._uU(32,"\n            "),t.YNc(33,Zt,1,0,"tr",16),t._uU(34,"\n            "),t.YNc(35,ht,1,2,"tr",17),t._uU(36,"\n        "),t.qZA(),t._uU(37,"\n    "),t.qZA(),t._uU(38,"\n")),2&n){const e=t.oxw();t.xp6(1),t.Q6J("isWaiting",e.isLoading),t.xp6(2),t.Q6J("dataSource",e.dataSource),t.xp6(30),t.Q6J("matHeaderRowDef",e.columnsToDisplay),t.xp6(2),t.Q6J("matRowDefColumns",e.columnsToDisplay)}}function xt(n,o){1&n&&(t._uU(0,"\n    "),t.TgZ(1,"a",26),t._uU(2,"\n        "),t.TgZ(3,"button",27),t._uU(4," Add Function "),t.qZA(),t._uU(5,"\n    "),t.qZA(),t._uU(6,"\n"))}class d extends E.S{constructor(o,e,u,s){super(s),(0,i.Z)(this,"functionService",void 0),(0,i.Z)(this,"dispatcherAssetModeService",void 0),(0,i.Z)(this,"dialog",void 0),(0,i.Z)(this,"authenticationService",void 0),(0,i.Z)(this,"functionsResult",void 0),(0,i.Z)(this,"dataSource",new l.by([])),(0,i.Z)(this,"columnsToDisplay",["code","type","params","bts"]),(0,i.Z)(this,"deletedRows",[]),(0,i.Z)(this,"showParams",!1),this.functionService=o,this.dispatcherAssetModeService=e,this.dialog=u,this.authenticationService=s}ngOnInit(){this.updateTable(0)}updateTable(o){this.setIsLoadingStart(),this.functionService.getFunctions(o.toString()).subscribe({next:e=>{this.functionsResult=e,this.dataSource=new l.by(e.functions)},complete:()=>{this.setIsLoadingEnd()}})}delete(o){this.deletedRows.push(o),this.functionService.deleteCommit(o.id.toString()).subscribe()}nextPage(){}prevPage(){}}(0,i.Z)(d,"\u0275fac",function(o){return new(o||d)(t.Y36(h),t.Y36(W.F),t.Y36(L.uw),t.Y36(H.$h))}),(0,i.Z)(d,"\u0275cmp",t.Xpm({type:d,selectors:[["functions"]],features:[t.qOj],decls:7,vars:1,consts:[[4,"ngIf"],["MainTableTemplate",""],["addButtonTemplate",""],["justify-content","space-between"],["justify-content","flex-end","align-items","center","gap","16px"],[3,"ngModel","ngModelChange"],[4,"ngTemplateOutlet"],["theme","info"],[3,"isWaiting"],["mat-table","","multiTemplateDataRows","",1,"mat-table",3,"dataSource"],["matColumnDef","code"],["mat-header-cell","",4,"matHeaderCellDef"],["mat-cell","",4,"matCellDef"],["matColumnDef","type"],["matColumnDef","params"],["matColumnDef","bts"],["mat-header-row","",4,"matHeaderRowDef"],["mat-row","",3,"deleted-table-row",4,"matRowDef","matRowDefColumns"],["mat-header-cell",""],["mat-cell",""],[3,"hidden"],["justify-content","flex-end","gap","8px",4,"ngIf"],["justify-content","flex-end","gap","8px"],["mat-flat-button","","color","warn","size","forTableRow",3,"click"],["mat-header-row",""],["mat-row",""],["routerLink","/dispatcher/functions/add"],["mat-flat-button","","color","primary"]],template:function(o,e){1&o&&(t.YNc(0,lt,56,5,"ct-section",0),t._uU(1,"\n\n\n\n"),t.YNc(2,Tt,39,4,"ng-template",null,1,t.W1O),t._uU(4,"\n\n\n\n"),t.YNc(5,xt,7,0,"ng-template",null,2,t.W1O)),2&o&&t.Q6J("ngIf",e.functionsResult)},dependencies:[m.O5,m.tP,p.rH,K.Q,T.U,x.n,v.V,A.R,X.a,F.i,C.Z,y.t,R.B,M.i,S._,V.v,Y.lW,k.Hw,tt.Rr,l.BZ,l.fO,l.as,l.w1,l.Dz,l.nj,l.ge,l.ev,l.XQ,l.Gk,f.JJ,f.On]})),function(n,o,e,u){var _,s=arguments.length,a=s<3?o:null===u?u=Object.getOwnPropertyDescriptor(o,e):u;if("object"==typeof Reflect&&"function"==typeof Reflect.decorate)a=Reflect.decorate(n,o,e,u);else for(var U=n.length-1;U>=0;U--)(_=n[U])&&(a=(s<3?_(a):s>3?_(o,e,a):_(o,e))||a);s>3&&a&&Object.defineProperty(o,e,a)}([(0,G.K)({question:n=>`Do you want to delete Function\xa0#${n.id}`,rejectTitle:"Cancel",resolveTitle:"Delete"}),g("design:type",Function),g("design:paramtypes",[Object]),g("design:returntype",void 0)],d.prototype,"delete",null);var vt=c(6423),At=c(1623);const N=[{path:"",component:d},{path:"add",component:z,data:{backConfig:["../"]}}];let J=(()=>{class n{}return(0,i.Z)(n,"\u0275fac",function(e){return new(e||n)}),(0,i.Z)(n,"\u0275mod",t.oAB({type:n})),(0,i.Z)(n,"\u0275inj",t.cJS({imports:[p.Bz.forChild(N),p.Bz]})),n})(),Ft=(()=>{class n{}return(0,i.Z)(n,"\u0275fac",function(e){return new(e||n)}),(0,i.Z)(n,"\u0275mod",t.oAB({type:n})),(0,i.Z)(n,"\u0275inj",t.cJS({imports:[m.ez,J,At.E,vt.$,f.u5,f.UX,Q.aw.forChild({})]})),n})()}}]);