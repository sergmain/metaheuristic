// Generated from ai/metaheuristic/ai/dispatcher/source_code/graph/mhsc/MhSourceCode.g4 by ANTLR 4.13.2
package ai.metaheuristic.ai.dispatcher.source_code.graph.mhsc;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link MhSourceCodeParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface MhSourceCodeVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#compilationUnit}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompilationUnit(MhSourceCodeParser.CompilationUnitContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#sourceDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSourceDecl(MhSourceCodeParser.SourceDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#sourceOptions}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSourceOptions(MhSourceCodeParser.SourceOptionsContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#sourceOption}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSourceOption(MhSourceCodeParser.SourceOptionContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#sourceBody}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSourceBody(MhSourceCodeParser.SourceBodyContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#sourceElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSourceElement(MhSourceCodeParser.SourceElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#variablesBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariablesBlock(MhSourceCodeParser.VariablesBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#variablesElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVariablesElement(MhSourceCodeParser.VariablesElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#inlineEntry}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInlineEntry(MhSourceCodeParser.InlineEntryContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#metasBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMetasBlock(MhSourceCodeParser.MetasBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#processDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProcessDecl(MhSourceCodeParser.ProcessDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#processInline}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProcessInline(MhSourceCodeParser.ProcessInlineContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#functionRef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionRef(MhSourceCodeParser.FunctionRefContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#processElement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProcessElement(MhSourceCodeParser.ProcessElementContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#processAttr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProcessAttr(MhSourceCodeParser.ProcessAttrContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#inputsDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInputsDecl(MhSourceCodeParser.InputsDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#outputsDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOutputsDecl(MhSourceCodeParser.OutputsDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#varDefList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarDefList(MhSourceCodeParser.VarDefListContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#varDef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarDef(MhSourceCodeParser.VarDefContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#varModifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitVarModifier(MhSourceCodeParser.VarModifierContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#metaDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMetaDecl(MhSourceCodeParser.MetaDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#metaEntry}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMetaEntry(MhSourceCodeParser.MetaEntryContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#nameDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNameDecl(MhSourceCodeParser.NameDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#paramsDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParamsDecl(MhSourceCodeParser.ParamsDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#timeoutDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTimeoutDecl(MhSourceCodeParser.TimeoutDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#cacheDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCacheDecl(MhSourceCodeParser.CacheDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#cacheOption}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCacheOption(MhSourceCodeParser.CacheOptionContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#conditionDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionDecl(MhSourceCodeParser.ConditionDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#skipPolicy}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSkipPolicy(MhSourceCodeParser.SkipPolicyContext ctx);
	/**
	 * Visit a parse tree produced by the {@code condGrouped}
	 * labeled alternative in {@link MhSourceCodeParser#conditionExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondGrouped(MhSourceCodeParser.CondGroupedContext ctx);
	/**
	 * Visit a parse tree produced by the {@code condBareBoolean}
	 * labeled alternative in {@link MhSourceCodeParser#conditionExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondBareBoolean(MhSourceCodeParser.CondBareBooleanContext ctx);
	/**
	 * Visit a parse tree produced by the {@code condFalse}
	 * labeled alternative in {@link MhSourceCodeParser#conditionExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondFalse(MhSourceCodeParser.CondFalseContext ctx);
	/**
	 * Visit a parse tree produced by the {@code condCompare}
	 * labeled alternative in {@link MhSourceCodeParser#conditionExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondCompare(MhSourceCodeParser.CondCompareContext ctx);
	/**
	 * Visit a parse tree produced by the {@code condTrue}
	 * labeled alternative in {@link MhSourceCodeParser#conditionExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondTrue(MhSourceCodeParser.CondTrueContext ctx);
	/**
	 * Visit a parse tree produced by the {@code condAnd}
	 * labeled alternative in {@link MhSourceCodeParser#conditionExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondAnd(MhSourceCodeParser.CondAndContext ctx);
	/**
	 * Visit a parse tree produced by the {@code condOr}
	 * labeled alternative in {@link MhSourceCodeParser#conditionExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondOr(MhSourceCodeParser.CondOrContext ctx);
	/**
	 * Visit a parse tree produced by the {@code condTernary}
	 * labeled alternative in {@link MhSourceCodeParser#conditionExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondTernary(MhSourceCodeParser.CondTernaryContext ctx);
	/**
	 * Visit a parse tree produced by the {@code condNot}
	 * labeled alternative in {@link MhSourceCodeParser#conditionExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCondNot(MhSourceCodeParser.CondNotContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#compareExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompareExpr(MhSourceCodeParser.CompareExprContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#compOp}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCompOp(MhSourceCodeParser.CompOpContext ctx);
	/**
	 * Visit a parse tree produced by the {@code arithAddSub}
	 * labeled alternative in {@link MhSourceCodeParser#arithmeticExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithAddSub(MhSourceCodeParser.ArithAddSubContext ctx);
	/**
	 * Visit a parse tree produced by the {@code arithMulDivMod}
	 * labeled alternative in {@link MhSourceCodeParser#arithmeticExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithMulDivMod(MhSourceCodeParser.ArithMulDivModContext ctx);
	/**
	 * Visit a parse tree produced by the {@code arithString}
	 * labeled alternative in {@link MhSourceCodeParser#arithmeticExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithString(MhSourceCodeParser.ArithStringContext ctx);
	/**
	 * Visit a parse tree produced by the {@code arithIdRef}
	 * labeled alternative in {@link MhSourceCodeParser#arithmeticExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithIdRef(MhSourceCodeParser.ArithIdRefContext ctx);
	/**
	 * Visit a parse tree produced by the {@code arithGrouped}
	 * labeled alternative in {@link MhSourceCodeParser#arithmeticExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithGrouped(MhSourceCodeParser.ArithGroupedContext ctx);
	/**
	 * Visit a parse tree produced by the {@code arithInt}
	 * labeled alternative in {@link MhSourceCodeParser#arithmeticExpr}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArithInt(MhSourceCodeParser.ArithIntContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#triesDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTriesDecl(MhSourceCodeParser.TriesDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#tagDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTagDecl(MhSourceCodeParser.TagDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#priorityDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPriorityDecl(MhSourceCodeParser.PriorityDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#preFunctionDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPreFunctionDecl(MhSourceCodeParser.PreFunctionDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#postFunctionDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPostFunctionDecl(MhSourceCodeParser.PostFunctionDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#subProcessBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSubProcessBlock(MhSourceCodeParser.SubProcessBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#processOrControl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitProcessOrControl(MhSourceCodeParser.ProcessOrControlContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#templateDecl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTemplateDecl(MhSourceCodeParser.TemplateDeclContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#paramList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitParamList(MhSourceCodeParser.ParamListContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#templateCall}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTemplateCall(MhSourceCodeParser.TemplateCallContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#argList}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArgList(MhSourceCodeParser.ArgListContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#arg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArg(MhSourceCodeParser.ArgContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#forLoop}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitForLoop(MhSourceCodeParser.ForLoopContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#idRef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdRef(MhSourceCodeParser.IdRefContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#idPart}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdPart(MhSourceCodeParser.IdPartContext ctx);
	/**
	 * Visit a parse tree produced by {@link MhSourceCodeParser#keyword}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitKeyword(MhSourceCodeParser.KeywordContext ctx);
}