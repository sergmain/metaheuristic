"use strict";(self.webpackChunkmetaheuristic_app=self.webpackChunkmetaheuristic_app||[]).push([[999],{7999:(Y,c,o)=>{o.r(c),o.d(c,{MhbpModule:()=>D,MhbpRoutes:()=>A,MhbpRoutingModule:()=>M});var s=o(1180),p=o(6895),d=o(9299),m=o(3081),f=o(1623),Z=o(6423),U=o(456),t=o(4650);let R=(()=>{class n{constructor(){}ngOnInit(){}}return(0,s.Z)(n,"\u0275fac",function(i){return new(i||n)}),(0,s.Z)(n,"\u0275cmp",t.Xpm({type:n,selectors:[["mhbp-index"]],decls:25,vars:0,template:function(i,e){1&i&&(t._uU(0,"    "),t.TgZ(1,"b"),t._uU(2,"Metaheuristic behavior platform"),t.qZA(),t._uU(3,"\n    "),t.TgZ(4,"ul"),t._uU(5,"\n        "),t.TgZ(6,"li"),t._uU(7,"\n            "),t.TgZ(8,"p"),t._uU(9,"Metaheuristic behavior platform"),t.qZA(),t._uU(10,"\n            Metaheuristic behavior platform is a SaaS for continuous evaluating and assessing quality of LLM-based APIs.\n        "),t.qZA(),t._uU(11,"\n        "),t.TgZ(12,"li"),t._uU(13,"\n            "),t.TgZ(14,"p"),t._uU(15,"Evaluating and assessing quality."),t.qZA(),t._uU(16,"\n            MHBP is scheduling requests to LLM and execute such requests. Collect the answers to prompt and evaluate them.\n        "),t.qZA(),t._uU(17,"\n        "),t.TgZ(18,"li"),t._uU(19,"\n            "),t.TgZ(20,"p"),t._uU(21,"Decisions which are based on the result of evaluation."),t.qZA(),t._uU(22,"\n            Based on the result of evaluating and assessing of API, MHBP can create triggers, events, or call API to inform 3rd parties, if quality of tested LLM was degraded.\n        "),t.qZA(),t._uU(23,"\n    "),t.qZA(),t._uU(24,"\n"))}})),n})();var L=o(8318),a=o(3788),g=o(1145),C=o(4186),T=o(197),I=o(5155),O=o(4859),h=o(3267);function y(n,u){1&n&&(t.TgZ(0,"button",8),t._uU(1),t.ALo(2,"translate"),t.qZA()),2&n&&(t.xp6(1),t.hij("\n                ",t.lcZ(2,1,"dispatcher.Scenario"),"\n            "))}function P(n,u){1&n&&(t.TgZ(0,"button",9),t._uU(1),t.ALo(2,"translate"),t.qZA()),2&n&&(t.xp6(1),t.hij("\n                ",t.lcZ(2,1,"dispatcher.Sessions"),"\n            "))}function S(n,u){1&n&&(t.TgZ(0,"button",10),t._uU(1),t.ALo(2,"translate"),t.qZA()),2&n&&(t.xp6(1),t.hij("\n                ",t.lcZ(2,1,"dispatcher.Eval"),"\n            "))}function x(n,u){1&n&&(t.TgZ(0,"button",11),t._uU(1),t.ALo(2,"translate"),t.qZA()),2&n&&(t.xp6(1),t.hij("\n                ",t.lcZ(2,1,"dispatcher.Kbs"),"\n            "))}function J(n,u){1&n&&(t.TgZ(0,"button",12),t._uU(1),t.ALo(2,"translate"),t.qZA()),2&n&&(t.xp6(1),t.hij("\n                ",t.lcZ(2,1,"dispatcher.Apis"),"\n            "))}function j(n,u){1&n&&(t.TgZ(0,"button",13),t._uU(1),t.ALo(2,"translate"),t.qZA()),2&n&&(t.xp6(1),t.hij("\n                ",t.lcZ(2,1,"dispatcher.Auths"),"\n            "))}let l=(()=>{class n extends L.S{constructor(i,e,b){super(b),(0,s.Z)(this,"router",void 0),(0,s.Z)(this,"settingsService",void 0),(0,s.Z)(this,"authenticationService",void 0),(0,s.Z)(this,"settings",void 0),(0,s.Z)(this,"sidenavOpened",void 0),this.router=i,this.settingsService=e,this.authenticationService=b,this.router.routeReuseStrategy.shouldReuseRoute=()=>!1}ngOnInit(){this.subscribeSubscription(this.settingsService.events.subscribe(i=>{i instanceof g.U&&(this.settings=i.settings,this.sidenavOpened=i.settings.sidenav)}))}ngOnDestroy(){this.unsubscribeSubscriptions()}}return(0,s.Z)(n,"\u0275fac",function(i){return new(i||n)(t.Y36(d.F0),t.Y36(g.g),t.Y36(a.$h))}),(0,s.Z)(n,"\u0275cmp",t.Xpm({type:n,selectors:[["mhbp-root"]],features:[t.qOj],decls:32,vars:7,consts:[["mode","side",3,"opened"],[1,"navigation"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/scenario",4,"ngIf"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/session",4,"ngIf"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/evaluation",4,"ngIf"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/kb",4,"ngIf"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/api",4,"ngIf"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/auth",4,"ngIf"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/scenario"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/session"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/evaluation"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/kb"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/api"],["mat-button","","routerLinkActive","active","routerLink","/mhbp/auth"]],template:function(i,e){1&i&&(t.TgZ(0,"mat-sidenav-container"),t._uU(1,"\n    "),t.TgZ(2,"mat-sidenav",0),t._uU(3,"\n        "),t.TgZ(4,"div",1),t._uU(5,"\n            "),t.YNc(6,y,3,3,"button",2),t._uU(7,"\n\n            "),t.YNc(8,P,3,3,"button",3),t._uU(9,"\n\n            "),t.YNc(10,S,3,3,"button",4),t._uU(11,"\n\n            "),t.YNc(12,x,3,3,"button",5),t._uU(13,"\n\n            "),t.YNc(14,J,3,3,"button",6),t._uU(15,"\n\n            "),t.YNc(16,j,3,3,"button",7),t._uU(17,"\n        "),t.qZA(),t._uU(18,"\n    "),t.qZA(),t._uU(19,"\n    "),t.TgZ(20,"mat-sidenav-content"),t._uU(21,"\n        "),t.TgZ(22,"ct-content"),t._uU(23,"\n            "),t._UZ(24,"router-outlet"),t._uU(25,"\n            "),t._UZ(26,"ct-back-button"),t._uU(27,"\n            "),t._UZ(28,"copy-right"),t._uU(29,"\n        "),t.qZA(),t._uU(30,"\n    "),t.qZA(),t._uU(31,"\n"),t.qZA()),2&i&&(t.xp6(2),t.Q6J("opened",e.sidenavOpened),t.xp6(4),t.Q6J("ngIf",e.isRole.Admin||e.isRole.Data||e.isRole.Operator||e.isRole.Manager),t.xp6(2),t.Q6J("ngIf",e.isRole.Admin||e.isRole.Data||e.isRole.Operator||e.isRole.Manager),t.xp6(2),t.Q6J("ngIf",e.isRole.Admin||e.isRole.Data||e.isRole.Operator||e.isRole.Manager),t.xp6(2),t.Q6J("ngIf",e.isRole.Admin||e.isRole.Data||e.isRole.Operator||e.isRole.Manager),t.xp6(2),t.Q6J("ngIf",e.isRole.Admin||e.isRole.Data||e.isRole.Operator||e.isRole.Manager),t.xp6(2),t.Q6J("ngIf",e.isRole.Admin||e.isRole.Data||e.isRole.Operator||e.isRole.Manager))},dependencies:[p.O5,d.lC,d.rH,d.Od,C.O,T.N,I.N,O.lW,h.JX,h.TM,h.Rh,m.X$],styles:["mat-sidenav-content[_ngcontent-%COMP%]{overflow:hidden}mat-sidenav[_ngcontent-%COMP%]{position:fixed;top:54px}.navigation[_ngcontent-%COMP%]{width:150px;display:flex;flex-direction:column;padding:9px}.navigation[_ngcontent-%COMP%]   button[_ngcontent-%COMP%]{margin-bottom:9px}.mat-button[_ngcontent-%COMP%]{text-align:left}"]})),n})();var r=o(5126);const v=[a.uU.Admin,a.uU.Data,a.uU.Manager],A=[{path:"",canActivate:[r.k],component:l,data:{requiredRoles:v},children:[{path:"",component:R,data:{requiredRoles:v}}]},{path:"scenario",canActivate:[r.k],component:l,loadChildren:()=>Promise.all([o.e(592),o.e(413)]).then(o.bind(o,8413)).then(n=>n.ScenarioModule),data:{requiredRoles:[a.uU.Admin,a.uU.Manager],section:"scenario-groups"}},{path:"session",canActivate:[r.k],component:l,loadChildren:()=>Promise.all([o.e(592),o.e(435)]).then(o.bind(o,9435)).then(n=>n.SessionModule),data:{requiredRoles:[a.uU.Admin,a.uU.Manager],section:"sessions"}},{path:"evaluation",canActivate:[r.k],component:l,loadChildren:()=>Promise.all([o.e(592),o.e(747)]).then(o.bind(o,747)).then(n=>n.EvaluationModule),data:{requiredRoles:[a.uU.Admin,a.uU.Manager],section:"evaluations"}},{path:"kb",canActivate:[r.k],component:l,loadChildren:()=>Promise.all([o.e(592),o.e(403)]).then(o.bind(o,6403)).then(n=>n.KbModule),data:{requiredRoles:[a.uU.Admin,a.uU.Manager],section:"kbs"}},{path:"auth",canActivate:[r.k],component:l,loadChildren:()=>Promise.all([o.e(592),o.e(364)]).then(o.bind(o,8364)).then(n=>n.AuthModule),data:{requiredRoles:[a.uU.Admin,a.uU.Manager],section:"auths"}},{path:"api",canActivate:[r.k],component:l,loadChildren:()=>Promise.all([o.e(592),o.e(200)]).then(o.bind(o,8200)).then(n=>n.ApiModule),data:{requiredRoles:[a.uU.Admin,a.uU.Manager],section:"apis"}}];let M=(()=>{class n{}return(0,s.Z)(n,"\u0275fac",function(i){return new(i||n)}),(0,s.Z)(n,"\u0275mod",t.oAB({type:n})),(0,s.Z)(n,"\u0275inj",t.cJS({imports:[d.Bz.forChild(A),d.Bz]})),n})(),D=(()=>{class n{}return(0,s.Z)(n,"\u0275fac",function(i){return new(i||n)}),(0,s.Z)(n,"\u0275mod",t.oAB({type:n})),(0,s.Z)(n,"\u0275inj",t.cJS({imports:[p.ez,M,f.E,U.k,Z.$,m.aw.forChild({})]})),n})()}}]);