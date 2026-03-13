/*
 * Metaheuristic, Copyright (C) 2017-2026, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.commons.graph.source_code_graph;

//import ai.metaheuristic.ai.Consts;
//import ai.metaheuristic.ai.dispatcher.data.ExecContextData;
//import ai.metaheuristic.ai.dispatcher.exec_context.ExecContextProcessGraphService;
//import ai.metaheuristic.ai.dispatcher.source_code.graph.mhsc.MhSourceCodeBaseVisitor;
//import ai.metaheuristic.ai.dispatcher.source_code.graph.mhsc.MhSourceCodeLexer;
//import ai.metaheuristic.ai.dispatcher.source_code.graph.mhsc.MhSourceCodeParser;
//import ai.metaheuristic.ai.exceptions.SourceCodeGraphException;
//import ai.metaheuristic.ai.utils.ContextUtils;
import ai.metaheuristic.ai.dispatcher.source_code.graph.mhsc.MhSourceCodeBaseVisitor;
import ai.metaheuristic.ai.dispatcher.source_code.graph.mhsc.MhSourceCodeLexer;
import ai.metaheuristic.ai.dispatcher.source_code.graph.mhsc.MhSourceCodeParser;
import ai.metaheuristic.api.EnumsApi;
import ai.metaheuristic.api.data.SourceCodeGraph;
import ai.metaheuristic.api.data.exec_context.ExecContextApiData;
import ai.metaheuristic.api.data.exec_context.ExecContextParamsYaml;
import ai.metaheuristic.commons.exceptions.SourceCodeGraphException;
import ai.metaheuristic.commons.graph.ExecContextProcessGraphService;
import ai.metaheuristic.commons.utils.ContextUtils;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static ai.metaheuristic.commons.CommonConsts.MH_FINISH_FUNCTION;
import static ai.metaheuristic.commons.CommonConsts.TOP_LEVEL_CONTEXT_ID;

/**
 * @author Serge
 * Date: 3/2026
 * Parses .mhsc (MH SourceCode DSL) files into SourceCodeData.SourceCodeGraph
 */
public class SourceCodeGraphLanguageMhsc implements SourceCodeGraphLanguage {

    @Override
    public SourceCodeGraph parse(String sourceCode, Supplier<String> contextIdSupplier) {
        MhSourceCodeLexer lexer = new MhSourceCodeLexer(CharStreams.fromString(sourceCode));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MhSourceCodeParser parser = new MhSourceCodeParser(tokens);

        // Throw on syntax errors instead of printing to stderr
        parser.removeErrorListeners();
        parser.addErrorListener(new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                                    int line, int charPositionInLine, String msg, RecognitionException e) {
                throw new SourceCodeGraphException("564.200 Syntax error at line " + line + ":" + charPositionInLine + " " + msg);
            }
        });

        MhSourceCodeParser.CompilationUnitContext tree = parser.compilationUnit();
        MhscVisitor visitor = new MhscVisitor(contextIdSupplier);
        visitor.visit(tree);
        return visitor.getGraph();
    }


    public static String unquote(String s) {
        if (s.length() >= 2 && ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'")))) {
            return s.substring(1, s.length() - 1)
                .replace("\\\"", "\"")
                .replace("\\'", "'")
                .replace("\\\\", "\\");
        }
        return s;
    }

/**
     * AST visitor that directly constructs SourceCodeData.SourceCodeGraph.
     */
    static class MhscVisitor extends MhSourceCodeBaseVisitor<Void> {

        private final Supplier<String> contextIdSupplier;
        private final SourceCodeGraph scg = new SourceCodeGraph();
        private final Set<String> processCodes = new HashSet<>();
        private final Map<String, Long> ids = new HashMap<>();
        private final AtomicLong currId = new AtomicLong();

        // Template storage
        private final Map<String, MhSourceCodeParser.TemplateDeclContext> templates = new HashMap<>();
        // def constants: def name = "value"
        private final Map<String, String> defConstants = new HashMap<>();
        // For-loop variable bindings during expansion
        private final Map<String, Integer> loopVariables = new HashMap<>();

        // Track current context during tree walk
        private String currentInternalContextId;
        private Set<ExecContextApiData.ProcessVertex> parentProcesses;
        private boolean finishPresent = false;

        MhscVisitor(Supplier<String> contextIdSupplier) {
            this.contextIdSupplier = contextIdSupplier;
            this.currentInternalContextId = contextIdSupplier.get();
            this.parentProcesses = new HashSet<>();
        }

        SourceCodeGraph getGraph() {
            if (scg.uid == null || scg.uid.isBlank()) {
                throw new SourceCodeGraphException("564.190 uid is required in source declaration");
            }
            if (!finishPresent) {
                addFinishProcess();
            }
            return scg;
        }

        @Override
        public Void visitSourceDecl(MhSourceCodeParser.SourceDeclContext ctx) {
            // Extract uid from source declaration: source "uid" (options) { ... }
            scg.uid = unquote(ctx.STRING().getText());

            // Process source options
            if (ctx.sourceOptions() != null) {
                for (MhSourceCodeParser.SourceOptionContext opt : ctx.sourceOptions().sourceOption()) {
                    if (opt.getText().equals("clean")) {
                        scg.clean = true;
                    } else if (opt.getText().startsWith("instances")) {
                        scg.instances = Integer.parseInt(opt.INT().getText());
                    } else if (opt.getText().startsWith("ac")) {
                        scg.ac = new SourceCodeGraph.AccessControl(unquote(opt.STRING().getText()));
                    }
                }
            }

            // Visit children (variables, processes, etc.)
            if (ctx.sourceBody() != null) {
                for (MhSourceCodeParser.SourceElementContext elem : ctx.sourceBody().sourceElement()) {
                    if (elem.variablesBlock() != null) {
                        visitVariablesBlock(elem.variablesBlock());
                    } else if (elem.defDecl() != null) {
                        processDefDecl(elem.defDecl());
                    } else if (elem.metasBlock() != null) {
                        parseSourceMetas(elem.metasBlock());
                    } else if (elem.templateDecl() != null) {
                        visitTemplateDecl(elem.templateDecl());
                    } else if (elem.forLoop() != null) {
                        processForLoop(elem.forLoop(), currentInternalContextId, parentProcesses);
                    } else if (elem.processDecl() != null) {
                        parentProcesses = processProcessDecl(elem.processDecl(), currentInternalContextId, parentProcesses);
                    }
                }
            }
            return null;
        }

        @Override
        public Void visitVariablesBlock(MhSourceCodeParser.VariablesBlockContext ctx) {
            for (MhSourceCodeParser.VariablesElementContext elem : ctx.variablesElement()) {
                if (elem.getStart().getText().equals("<-")) {
                    // inputs
                    for (MhSourceCodeParser.VarDefContext vd : elem.varDefList().varDef()) {
                        scg.variables.inputs.add(varDefToVariable(vd));
                    }
                } else if (elem.getStart().getText().equals("->")) {
                    // outputs
                    for (MhSourceCodeParser.VarDefContext vd : elem.varDefList().varDef()) {
                        scg.variables.outputs.add(varDefToVariable(vd));
                    }
                } else if (elem.getStart().getText().equals("global")) {
                    // globals
                    if (scg.variables.globals == null) {
                        scg.variables.globals = new ArrayList<>();
                    }
                    for (MhSourceCodeParser.IdRefContext idRef : elem.idRef()) {
                        scg.variables.globals.add(resolveIdRef(idRef));
                    }
                } else if (elem.getStart().getText().equals("inline")) {
                    // inline variables
                    String inlineName = elem.ID().getText();
                    Map<String, String> entries = new LinkedHashMap<>();
                    for (MhSourceCodeParser.InlineEntryContext ie : elem.inlineEntry()) {
                        entries.put(ie.ID().getText(), unquote(ie.STRING().getText()));
                    }
                    scg.variables.inline.put(inlineName, entries);
                }
            }
            return null;
        }

        private void parseSourceMetas(MhSourceCodeParser.MetasBlockContext ctx) {
            for (MhSourceCodeParser.MetaEntryContext me : ctx.metaEntry()) {
                Map<String, String> entry = new LinkedHashMap<>();
                String key = resolveIdRef(me.idRef(0));
                String value;
                if (me.STRING() != null) {
                    value = unquote(me.STRING().getText());
                } else {
                    value = resolveIdRef(me.idRef(1));
                }
                entry.put(key, value);
                scg.metas.add(entry);
            }
        }

        @Override
        public Void visitTemplateDecl(MhSourceCodeParser.TemplateDeclContext ctx) {
            templates.put(ctx.ID().getText(), ctx);
            return null;
        }

        private void processDefDecl(MhSourceCodeParser.DefDeclContext ctx) {
            String name = ctx.ID(0).getText();
            String value;
            if (ctx.STRING() != null) {
                value = unquote(ctx.STRING().getText());
            } else {
                // ID value (second ID token)
                value = ctx.ID(1).getText();
            }
            defConstants.put(name, value);
        }

/**
         * Process a processDecl and return the set of "last" vertices for chaining.
         */
        private Set<ExecContextApiData.ProcessVertex> processProcessDecl(
                MhSourceCodeParser.ProcessDeclContext ctx,
                String internalContextId,
                Set<ExecContextApiData.ProcessVertex> parents) {

            if (finishPresent) {
                throw new SourceCodeGraphException("564.240 mh.finish isn't the last process");
            }

            String processCode = resolveIdRef(ctx.idRef());
            checkProcessCode(processCode);

            ExecContextParamsYaml.Process process = new ExecContextParamsYaml.Process();
            process.processCode = processCode;
            process.processName = processCode; // default, may be overridden by nameDecl
            process.internalContextId = internalContextId;

            // Function reference
            MhSourceCodeParser.FunctionRefContext funcRef = ctx.functionRef();
            process.function = parseFunctionRef(funcRef);

            // Process attributes
            EnumsApi.@Nullable SourceCodeSubProcessLogic subProcessLogic = null;
            List<MhSourceCodeParser.ProcessOrControlContext> subProcessChildren = null;

            if (ctx.processElement() != null) {
                // Block form: processDecl with { processElement* }
                for (MhSourceCodeParser.ProcessElementContext pe : ctx.processElement()) {
                    if (pe.inputsDecl() != null) {
                        parseInputs(pe.inputsDecl(), process);
                    } else if (pe.outputsDecl() != null) {
                        parseOutputs(pe.outputsDecl(), process);
                    } else if (pe.metaDecl() != null) {
                        parseMeta(pe.metaDecl(), process);
                    } else if (pe.timeoutDecl() != null) {
                        process.timeoutBeforeTerminate = Long.parseLong(pe.timeoutDecl().INT().getText());
                    } else if (pe.cacheDecl() != null) {
                        process.cache = parseCacheDecl(pe.cacheDecl());
                    } else if (pe.conditionDecl() != null) {
                        process.condition = emitSpelCondition(pe.conditionDecl().conditionExpr());
                    } else if (pe.triesDecl() != null) {
                        process.triesAfterError = Integer.parseInt(pe.triesDecl().INT().getText());
                    } else if (pe.tagDecl() != null) {
                        process.tag = pe.tagDecl().ID().getText();
                    } else if (pe.priorityDecl() != null) {
                        process.priority = parsePriority(pe.priorityDecl());
                    } else if (pe.preFunctionDecl() != null) {
                        if (process.preFunctions == null) {
                            process.preFunctions = new ArrayList<>();
                        }
                        process.preFunctions.add(parseFunctionRef(pe.preFunctionDecl().functionRef()));
                    } else if (pe.postFunctionDecl() != null) {
                        if (process.postFunctions == null) {
                            process.postFunctions = new ArrayList<>();
                        }
                        process.postFunctions.add(parseFunctionRef(pe.postFunctionDecl().functionRef()));
                    } else if (pe.nameDecl() != null) {
                        process.processName = unquote(pe.nameDecl().STRING().getText());
                    } else if (pe.paramsDecl() != null) {
                        process.function = new ExecContextParamsYaml.FunctionDefinition(
                                process.function.code, unquote(pe.paramsDecl().STRING().getText()),
                                process.function.context, process.function.refType);
                    } else if (pe.subProcessBlock() != null) {
                        MhSourceCodeParser.SubProcessBlockContext spb = pe.subProcessBlock();
                        if (spb.getStart().getText().equals("sequential")) {
                            subProcessLogic = EnumsApi.SourceCodeSubProcessLogic.sequential;
                        } else if (spb.getStart().getText().equals("parallel")) {
                            subProcessLogic = EnumsApi.SourceCodeSubProcessLogic.and;
                        } else if (spb.getStart().getText().equals("race")) {
                            subProcessLogic = EnumsApi.SourceCodeSubProcessLogic.or;
                        }
                        subProcessChildren = spb.processOrControl();
                    }
                }
            } else if (ctx.processInline() != null) {
                // One-liner form
                for (MhSourceCodeParser.ProcessAttrContext pa : ctx.processInline().processAttr()) {
                    if (pa.inputsDecl() != null) {
                        parseInputs(pa.inputsDecl(), process);
                    } else if (pa.outputsDecl() != null) {
                        parseOutputs(pa.outputsDecl(), process);
                    } else if (pa.metaDecl() != null) {
                        parseMeta(pa.metaDecl(), process);
                    } else if (pa.timeoutDecl() != null) {
                        process.timeoutBeforeTerminate = Long.parseLong(pa.timeoutDecl().INT().getText());
                    } else if (pa.cacheDecl() != null) {
                        process.cache = parseCacheDecl(pa.cacheDecl());
                    } else if (pa.conditionDecl() != null) {
                        process.condition = emitSpelCondition(pa.conditionDecl().conditionExpr());
                    } else if (pa.triesDecl() != null) {
                        process.triesAfterError = Integer.parseInt(pa.triesDecl().INT().getText());
                    } else if (pa.tagDecl() != null) {
                        process.tag = pa.tagDecl().ID().getText();
                    } else if (pa.priorityDecl() != null) {
                        process.priority = parsePriority(pa.priorityDecl());
                    } else if (pa.nameDecl() != null) {
                        process.processName = unquote(pa.nameDecl().STRING().getText());
                    } else if (pa.paramsDecl() != null) {
                        process.function = new ExecContextParamsYaml.FunctionDefinition(
                                process.function.code, unquote(pa.paramsDecl().STRING().getText()),
                                process.function.context, process.function.refType);
                    }
                }
            }

            process.logic = subProcessLogic;
            scg.processes.add(process);

            ExecContextApiData.ProcessVertex vertex = createProcessVertex(processCode, internalContextId);
            ExecContextProcessGraphService.addProcessVertexToGraph(scg.processGraph, vertex, parents);

            if (MH_FINISH_FUNCTION.equals(process.function.code)) {
                finishPresent = true;
            }

            // Process subProcesses if present
            if (subProcessLogic != null && subProcessChildren != null && !subProcessChildren.isEmpty()) {
                return processSubProcessChildren(subProcessChildren, subProcessLogic, vertex, internalContextId);
            }

            Set<ExecContextApiData.ProcessVertex> result = new HashSet<>();
            result.add(vertex);
            return result;
        }

        private Set<ExecContextApiData.ProcessVertex> processSubProcessChildren(
                List<MhSourceCodeParser.ProcessOrControlContext> children,
                EnumsApi.SourceCodeSubProcessLogic logic,
                ExecContextApiData.ProcessVertex parentVertex,
                String parentInternalContextId) {

            Set<ExecContextApiData.ProcessVertex> lastProcesses = new HashSet<>();
            Set<ExecContextApiData.ProcessVertex> tempLastProcesses = new HashSet<>();
            tempLastProcesses.add(parentVertex);
            String subInternalContextId = null;
            List<ExecContextApiData.ProcessVertex> andProcesses = new ArrayList<>();
            // Accumulate recursive leaves from ALL 'and' children, not just the last one
            Set<ExecContextApiData.ProcessVertex> allAndLastProcesses = new HashSet<>();

            if (logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                subInternalContextId = parentInternalContextId + ContextUtils.CONTEXT_DIGIT_SEPARATOR + contextIdSupplier.get();
            }

            for (MhSourceCodeParser.ProcessOrControlContext poc : children) {
                if (logic == EnumsApi.SourceCodeSubProcessLogic.and || logic == EnumsApi.SourceCodeSubProcessLogic.or) {
                    subInternalContextId = parentInternalContextId + ContextUtils.CONTEXT_DIGIT_SEPARATOR + contextIdSupplier.get();
                    tempLastProcesses.add(parentVertex);
                }

                if (subInternalContextId == null) {
                    throw new IllegalStateException("564.280 (subInternalContextId==null)");
                }

                Set<ExecContextApiData.ProcessVertex> tempParents;
                if (logic == EnumsApi.SourceCodeSubProcessLogic.and || logic == EnumsApi.SourceCodeSubProcessLogic.or) {
                    tempParents = new HashSet<>();
                    tempParents.add(parentVertex);
                } else {
                    tempParents = tempLastProcesses;
                }

                if (poc.processDecl() != null) {
                    tempLastProcesses = processProcessDecl(poc.processDecl(), subInternalContextId, tempParents);
                    if (logic == EnumsApi.SourceCodeSubProcessLogic.and) {
                        // For 'and', collect all direct child vertices
                        // The last vertex from processProcessDecl is the process vertex itself
                        andProcesses.addAll(tempLastProcesses);
                        allAndLastProcesses.addAll(tempLastProcesses);
                    }
                } else if (poc.forLoop() != null) {
                    tempLastProcesses = processForLoop(poc.forLoop(), subInternalContextId, tempParents);
                } else if (poc.templateCall() != null) {
                    tempLastProcesses = processTemplateCall(poc.templateCall(), subInternalContextId, tempParents);
                } else if (poc.subProcessBlock() != null) {
                    // Nested sub-process block - create a synthetic nop? No, just recurse directly.
                    // Actually this shouldn't happen in current grammar usage.
                    throw new SourceCodeGraphException("564.300 Nested sub-process blocks not directly supported");
                }
            }

            if (logic == EnumsApi.SourceCodeSubProcessLogic.sequential) {
                lastProcesses.addAll(tempLastProcesses);
            }
            else if (logic == EnumsApi.SourceCodeSubProcessLogic.and || logic == EnumsApi.SourceCodeSubProcessLogic.or) {
                lastProcesses.addAll(allAndLastProcesses);
            }

            return lastProcesses;
        }

        private Set<ExecContextApiData.ProcessVertex> processForLoop(
                MhSourceCodeParser.ForLoopContext ctx,
                String internalContextId,
                Set<ExecContextApiData.ProcessVertex> parents) {

            String loopVar = ctx.ID().getText();
            int start = Integer.parseInt(ctx.INT(0).getText());
            int end = Integer.parseInt(ctx.INT(1).getText());

            Set<ExecContextApiData.ProcessVertex> currentParents = parents;
            for (int i = start; i <= end; i++) {
                loopVariables.put(loopVar, i);
                for (MhSourceCodeParser.ProcessOrControlContext poc : ctx.processOrControl()) {
                    if (poc.processDecl() != null) {
                        currentParents = processProcessDecl(poc.processDecl(), internalContextId, currentParents);
                    } else if (poc.forLoop() != null) {
                        currentParents = processForLoop(poc.forLoop(), internalContextId, currentParents);
                    } else if (poc.templateCall() != null) {
                        currentParents = processTemplateCall(poc.templateCall(), internalContextId, currentParents);
                    }
                }
            }
            loopVariables.remove(loopVar);
            return currentParents;
        }

        private Set<ExecContextApiData.ProcessVertex> processTemplateCall(
                MhSourceCodeParser.TemplateCallContext ctx,
                String internalContextId,
                Set<ExecContextApiData.ProcessVertex> parents) {

            String templateName = ctx.ID().getText();
            MhSourceCodeParser.TemplateDeclContext tmpl = templates.get(templateName);
            if (tmpl == null) {
                throw new SourceCodeGraphException("564.320 Unknown template: " + templateName);
            }

            // Bind template parameters to argument values
            Map<String, Integer> savedVars = new HashMap<>();
            List<String> paramNames = new ArrayList<>();
            if (tmpl.paramList() != null) {
                List<TerminalNode> paramIds = tmpl.paramList().ID();
                List<MhSourceCodeParser.ArgContext> args =
                        ctx.argList() != null ? ctx.argList().arg() : List.of();
                for (int i = 0; i < paramIds.size(); i++) {
                    String paramName = paramIds.get(i).getText();
                    paramNames.add(paramName);
                    // Save existing binding if any
                    if (loopVariables.containsKey(paramName)) {
                        savedVars.put(paramName, loopVariables.get(paramName));
                    }
                    if (i < args.size()) {
                        MhSourceCodeParser.ArgContext arg = args.get(i);
                        if (arg.INT() != null) {
                            loopVariables.put(paramName, Integer.parseInt(arg.INT().getText()));
                        } else if (arg.idRef() != null) {
                            // idRef arg — might be a loop variable reference like L
                            String argText = resolveIdRef(arg.idRef());
                            try {
                                loopVariables.put(paramName, Integer.parseInt(argText));
                            } catch (NumberFormatException e) {
                                // Non-numeric arg — not supported for parameterized ids
                            }
                        }
                    }
                }
            }

            Set<ExecContextApiData.ProcessVertex> currentParents = parents;
            for (MhSourceCodeParser.ProcessOrControlContext poc : tmpl.processOrControl()) {
                if (poc.processDecl() != null) {
                    currentParents = processProcessDecl(poc.processDecl(), internalContextId, currentParents);
                } else if (poc.forLoop() != null) {
                    currentParents = processForLoop(poc.forLoop(), internalContextId, currentParents);
                } else if (poc.templateCall() != null) {
                    currentParents = processTemplateCall(poc.templateCall(), internalContextId, currentParents);
                }
            }

            // Restore previous bindings
            for (String paramName : paramNames) {
                if (savedVars.containsKey(paramName)) {
                    loopVariables.put(paramName, savedVars.get(paramName));
                } else {
                    loopVariables.remove(paramName);
                }
            }

            return currentParents;
        }

        // ================= Helper methods =================

        private ExecContextParamsYaml.FunctionDefinition parseFunctionRef(MhSourceCodeParser.FunctionRefContext ctx) {
            if (ctx.getStart().getText().equals("internal")) {
                return new ExecContextParamsYaml.FunctionDefinition(
                        resolveIdRef(ctx.idRef()), null, EnumsApi.FunctionExecContext.internal, EnumsApi.FunctionRefType.code);
            } else {
                return new ExecContextParamsYaml.FunctionDefinition(
                        resolveIdRef(ctx.idRef()), null, EnumsApi.FunctionExecContext.external, EnumsApi.FunctionRefType.code);
            }
        }

        private void parseInputs(MhSourceCodeParser.InputsDeclContext ctx, ExecContextParamsYaml.Process process) {
            for (MhSourceCodeParser.VarDefContext vd : ctx.varDefList().varDef()) {
                ExecContextParamsYaml.Variable var = varDefToExecVariable(vd);
                process.inputs.add(var);
            }
        }

        private void parseOutputs(MhSourceCodeParser.OutputsDeclContext ctx, ExecContextParamsYaml.Process process) {
            for (MhSourceCodeParser.VarDefContext vd : ctx.varDefList().varDef()) {
                ExecContextParamsYaml.Variable var = varDefToExecVariable(vd);
                process.outputs.add(var);
            }
        }

        private void parseMeta(MhSourceCodeParser.MetaDeclContext ctx, ExecContextParamsYaml.Process process) {
            for (MhSourceCodeParser.MetaEntryContext me : ctx.metaEntry()) {
                Map<String, String> entry = new LinkedHashMap<>();
                String key = resolveIdRef(me.idRef(0));
                String value;
                if (me.STRING() != null) {
                    value = unquote(me.STRING().getText());
                } else {
                    // idRef value
                    value = resolveIdRef(me.idRef(1));
                }
                entry.put(key, value);
                process.metas.add(entry);
            }
        }

        private static int parsePriority(MhSourceCodeParser.PriorityDeclContext ctx) {
            int val = Integer.parseInt(ctx.INT().getText());
            // Check if '-' token is present (grammar: 'priority' '-'? INT)
            return ctx.getText().contains("-") ? -val : val;
        }

        private ExecContextParamsYaml.Cache parseCacheDecl(MhSourceCodeParser.CacheDeclContext ctx) {
            boolean enabled = false;
            boolean omitInline = false;
            boolean cacheMeta = false;
            for (MhSourceCodeParser.CacheOptionContext opt : ctx.cacheOption()) {
                switch (opt.getText()) {
                    case "on" -> enabled = true;
                    case "off" -> enabled = false;
                    case "omitInline" -> omitInline = true;
                    case "cacheMeta" -> cacheMeta = true;
                }
            }
            return new ExecContextParamsYaml.Cache(enabled, omitInline, cacheMeta);
        }

        private ExecContextParamsYaml.Variable varDefToVariable(MhSourceCodeParser.VarDefContext ctx) {
            String name = resolveIdRef(ctx.idRef());
            ExecContextParamsYaml.Variable var = new ExecContextParamsYaml.Variable(name, EnumsApi.VariableContext.local, EnumsApi.DataSourcing.dispatcher,
                    null, null, null, null, null, null);
            applyVarModifiers(ctx, var);
            return var;
        }

        private ExecContextParamsYaml.Variable varDefToExecVariable(MhSourceCodeParser.VarDefContext ctx) {
            String name = resolveIdRef(ctx.idRef());
            ExecContextParamsYaml.Variable var = new ExecContextParamsYaml.Variable(name, EnumsApi.VariableContext.local, EnumsApi.DataSourcing.dispatcher,
                    null, null, null, null, null, null);
            applyVarModifiers(ctx, var);
            // Handle '?' shorthand for nullable
            if (ctx.getText().endsWith("?")) {
                var.setNullable(true);
            }
            return var;
        }

        private void applyVarModifiers(MhSourceCodeParser.VarDefContext ctx, ExecContextParamsYaml.Variable var) {
            if (ctx.varModifier() != null) {
                for (MhSourceCodeParser.VarModifierContext mod : ctx.varModifier()) {
                    String modText = mod.getStart().getText();
                    switch (modText) {
                        case "type" -> var.type = mod.ID().getText();
                        case "ext" -> var.ext = unquote(mod.STRING().getText());
                        case "nullable" -> var.setNullable(true);
                        case "array" -> {
                            // Set context to array
                            var.context = EnumsApi.VariableContext.array;
                        }
                        case "parentContext" -> var.parentContext = true;
                        case "sourcing" -> var.setSourcing(EnumsApi.DataSourcing.valueOf(mod.ID().getText()));
                    }
                }
            }
        }

        private ExecContextApiData.ProcessVertex createProcessVertex(String processCode, String internalContextId) {
            return SourceCodeGraphLanguageYaml.createProcessVertex(ids, currId, processCode, internalContextId);
        }

        private void checkProcessCode(String code) {
            if (processCodes.contains(code)) {
                throw new SourceCodeGraphException("564.340 Duplicate process code: " + code);
            }
            processCodes.add(code);
        }

        private void addFinishProcess() {
            ExecContextParamsYaml.Process p = new ExecContextParamsYaml.Process();
            p.processCode = MH_FINISH_FUNCTION;
            p.processName = MH_FINISH_FUNCTION;
            p.internalContextId = TOP_LEVEL_CONTEXT_ID;
            p.function = new ExecContextParamsYaml.FunctionDefinition(
                    MH_FINISH_FUNCTION, null, EnumsApi.FunctionExecContext.internal, EnumsApi.FunctionRefType.code);
            scg.processes.add(p);

            ExecContextApiData.ProcessVertex finishVertex = createProcessVertex(MH_FINISH_FUNCTION, TOP_LEVEL_CONTEXT_ID);
            ExecContextProcessGraphService.addProcessVertexToGraph(scg.processGraph, finishVertex, parentProcesses);
        }

        // ================= idRef resolution =================

        private String resolveIdRef(MhSourceCodeParser.IdRefContext ctx) {
            StringBuilder sb = new StringBuilder();
            for (MhSourceCodeParser.IdPartContext part : ctx.idPart()) {
                if (part.getChildCount() == 1 && (part.ID() != null || part.keyword() != null)) {
                    // Simple ID or keyword-as-identifier
                    // Check if it's a standalone loop variable (single-part idRef used as an argument)
                    String text = part.getText();
                    if (ctx.idPart().size() == 1 && part.ID() != null) {
                        Integer value = loopVariables.get(text);
                        if (value != null) {
                            sb.append(value);
                            continue;
                        }
                    }
                    sb.append(text);
                } else if (part.DEF_REF() != null) {
                    // ${name} — resolve from defConstants
                    String raw = part.DEF_REF().getText(); // "${name}"
                    String defName = raw.substring(2, raw.length() - 1); // strip ${ and }
                    String defValue = defConstants.get(defName);
                    if (defValue == null) {
                        throw new SourceCodeGraphException("564.390 Undefined def constant: " + defName);
                    }
                    sb.append(defValue);
                } else {
                    // Parameterized: {L}, {L+1}, {L-1}
                    // Note: Due to the lexer treating L-1 or L+1 as a single ID token inside {},
                    // we may need to manually parse the offset from the ID text.
                    TerminalNode idNode = part.ID();
                    if (idNode != null) {
                        String varName = idNode.getText();
                        Integer value = loopVariables.get(varName);
                        if (value != null) {
                            TerminalNode intNode = part.INT();
                            if (intNode != null) {
                                int offset = Integer.parseInt(intNode.getText());
                                if (part.getText().contains("+")) {
                                    sb.append(value + offset);
                                } else if (part.getText().contains("-")) {
                                    sb.append(value - offset);
                                }
                            } else {
                                sb.append(value);
                            }
                        } else {
                            // Try parsing ID as varName+offset or varName-offset
                            // (lexer may tokenize L-1 as single ID "L-1")
                            boolean resolved = false;
                            for (String op : new String[]{"+", "-"}) {
                                int opIdx = varName.lastIndexOf(op);
                                if (opIdx > 0) {
                                    String candidateVar = varName.substring(0, opIdx);
                                    String candidateOffset = varName.substring(opIdx + 1);
                                    Integer varValue = loopVariables.get(candidateVar);
                                    if (varValue != null) {
                                        try {
                                            int offset = Integer.parseInt(candidateOffset);
                                            sb.append(op.equals("+") ? varValue + offset : varValue - offset);
                                            resolved = true;
                                            break;
                                        } catch (NumberFormatException ignored) {}
                                    }
                                }
                            }
                            if (!resolved) {
                                // Unresolved parameter — keep literal text
                                sb.append(part.getText());
                            }
                        }
                    }
                }
            }
            return sb.toString();
        }

        // ================= Condition → SpEL emission =================

        private String emitSpelCondition(MhSourceCodeParser.ConditionExprContext ctx) {
            if (ctx instanceof MhSourceCodeParser.CondGroupedContext gc) {
                return "(" + emitSpelCondition(gc.conditionExpr()) + ")";
            } else if (ctx instanceof MhSourceCodeParser.CondNotContext nc) {
                return "!" + emitSpelCondition(nc.conditionExpr());
            } else if (ctx instanceof MhSourceCodeParser.CondAndContext ac) {
                return emitSpelCondition(ac.conditionExpr(0)) + " && " + emitSpelCondition(ac.conditionExpr(1));
            } else if (ctx instanceof MhSourceCodeParser.CondOrContext oc) {
                return emitSpelCondition(oc.conditionExpr(0)) + " || " + emitSpelCondition(oc.conditionExpr(1));
            } else if (ctx instanceof MhSourceCodeParser.CondTernaryContext tc) {
                return emitSpelCondition(tc.conditionExpr(0)) + " ? " +
                       emitSpelCondition(tc.conditionExpr(1)) + " : " +
                       emitSpelCondition(tc.conditionExpr(2));
            } else if (ctx instanceof MhSourceCodeParser.CondCompareContext cc) {
                return emitCompareExpr(cc.compareExpr());
            } else if (ctx instanceof MhSourceCodeParser.CondBareBooleanContext bb) {
                return resolveIdRef(bb.idRef());
            } else if (ctx instanceof MhSourceCodeParser.CondTrueContext) {
                return "true";
            } else if (ctx instanceof MhSourceCodeParser.CondFalseContext) {
                return "false";
            }
            throw new SourceCodeGraphException("564.360 Unknown condition expression type: " + ctx.getClass().getSimpleName());
        }

        private String emitCompareExpr(MhSourceCodeParser.CompareExprContext ctx) {
            return emitArithmeticExpr(ctx.arithmeticExpr(0)) + " " +
                   ctx.compOp().getText() + " " +
                   emitArithmeticExpr(ctx.arithmeticExpr(1));
        }

        private String emitArithmeticExpr(MhSourceCodeParser.ArithmeticExprContext ctx) {
            if (ctx instanceof MhSourceCodeParser.ArithAddSubContext asc) {
                String op = asc.getChild(1).getText();
                return emitArithmeticExpr(asc.arithmeticExpr(0)) + " " + op + " " + emitArithmeticExpr(asc.arithmeticExpr(1));
            } else if (ctx instanceof MhSourceCodeParser.ArithMulDivModContext mdc) {
                String op = mdc.getChild(1).getText();
                return emitArithmeticExpr(mdc.arithmeticExpr(0)) + " " + op + " " + emitArithmeticExpr(mdc.arithmeticExpr(1));
            } else if (ctx instanceof MhSourceCodeParser.ArithGroupedContext gc) {
                return "(" + emitArithmeticExpr(gc.arithmeticExpr()) + ")";
            } else if (ctx instanceof MhSourceCodeParser.ArithIdRefContext irc) {
                return resolveIdRef(irc.idRef());
            } else if (ctx instanceof MhSourceCodeParser.ArithIntContext ic) {
                return ic.INT().getText();
            } else if (ctx instanceof MhSourceCodeParser.ArithStringContext sc) {
                return sc.STRING().getText();
            }
            throw new SourceCodeGraphException("564.380 Unknown arithmetic expression type: " + ctx.getClass().getSimpleName());
        }

        // ================= Utility =================
    }
}
