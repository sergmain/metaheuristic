grammar MhSourceCode;

// =====================================================================
// Parser Rules
// =====================================================================

compilationUnit
    : sourceDecl EOF
    ;

// --- Source declaration ---
sourceDecl
    : 'source' STRING sourceOptions? '{' sourceBody '}'
    ;

sourceOptions
    : '(' sourceOption (',' sourceOption)* ')'
    ;

sourceOption
    : 'strict'
    | 'clean'
    | 'instances' '=' INT
    ;

sourceBody
    : sourceElement*
    ;

sourceElement
    : variablesBlock
    | metasBlock
    | processDecl
    | templateDecl
    | forLoop
    ;

// --- Variables block ---
variablesBlock
    : 'variables' '{' variablesElement* '}'
    ;

variablesElement
    : '<-' varDefList
    | '->' varDefList
    | 'global' idRef (',' idRef)*
    | 'inline' ID '{' inlineEntry* '}'
    ;

inlineEntry
    : ID '=' STRING
    ;

// --- Source-level metas ---
metasBlock
    : 'metas' '{' metaEntry (',' metaEntry)* '}'
    ;

// --- Process ---
processDecl
    : idRef ':=' functionRef '{' processElement* '}'
    | idRef ':=' functionRef processInline        // one-liner
    ;

processInline
    : processAttr+
    ;

functionRef
    : 'internal' idRef
    | idRef
    ;

processElement
    : inputsDecl
    | outputsDecl
    | metaDecl
    | timeoutDecl
    | cacheDecl
    | conditionDecl
    | triesDecl
    | tagDecl
    | priorityDecl
    | preFunctionDecl
    | postFunctionDecl
    | nameDecl
    | subProcessBlock
    ;

processAttr
    : inputsDecl
    | outputsDecl
    | metaDecl
    | timeoutDecl
    | cacheDecl
    | conditionDecl
    | triesDecl
    | tagDecl
    | priorityDecl
    | nameDecl
    ;

// --- Inputs / Outputs ---
inputsDecl
    : '<-' varDefList
    ;

outputsDecl
    : '->' varDefList
    ;

varDefList
    : varDef (',' varDef)*
    ;

varDef
    : idRef (':' varModifier (',' varModifier)*)? '?'?
    ;

varModifier
    : 'type' '=' ID
    | 'ext' '=' STRING
    | 'nullable'
    | 'array'
    | 'parentContext'
    | 'sourcing' '=' ID
    ;

// --- Meta ---
metaDecl
    : 'meta' '{' metaEntry (',' metaEntry)* '}'
    | 'meta' metaEntry (',' metaEntry)*
    ;

metaEntry
    : idRef '=' (idRef | STRING)
    ;

// --- Process attributes ---
nameDecl
    : 'name' STRING
    ;

timeoutDecl
    : 'timeout' INT
    ;

cacheDecl
    : 'cache' cacheOption (',' cacheOption)*
    ;

cacheOption
    : 'on'
    | 'off'
    | 'omitInline'
    | 'cacheMeta'
    ;

conditionDecl
    : 'when' conditionExpr ('skip' skipPolicy)?
    ;

skipPolicy
    : 'normal'
    | 'always'
    ;

// --- Condition expressions (transpiled to SpEL) ---
conditionExpr
    : '(' conditionExpr ')'                                   # condGrouped
    | '!' conditionExpr                                        # condNot
    | conditionExpr '&&' conditionExpr                         # condAnd
    | conditionExpr '||' conditionExpr                         # condOr
    | conditionExpr '?' conditionExpr ':' conditionExpr        # condTernary
    | compareExpr                                              # condCompare
    | idRef                                                    # condBareBoolean
    | 'true'                                                   # condTrue
    | 'false'                                                  # condFalse
    ;

compareExpr
    : arithmeticExpr compOp arithmeticExpr
    ;

compOp
    : '>' | '<' | '>=' | '<=' | '==' | '!='
    ;

arithmeticExpr
    : arithmeticExpr ('+' | '-') arithmeticExpr        # arithAddSub
    | arithmeticExpr ('*' | '/' | '%') arithmeticExpr  # arithMulDivMod
    | '(' arithmeticExpr ')'                           # arithGrouped
    | idRef                                            # arithIdRef
    | INT                                              # arithInt
    | STRING                                           # arithString
    ;

triesDecl
    : 'tries' INT
    ;

tagDecl
    : 'tag' ID
    ;

priorityDecl
    : 'priority' '-'? INT
    ;

preFunctionDecl
    : 'pre' functionRef
    ;

postFunctionDecl
    : 'post' functionRef
    ;

// --- SubProcesses ---
subProcessBlock
    : 'sequential' '{' processOrControl* '}'
    | 'parallel' '{' processOrControl* '}'      // logic: and
    | 'race' '{' processOrControl* '}'          // logic: or
    ;

processOrControl
    : processDecl
    | forLoop
    | templateCall
    | subProcessBlock
    ;

// --- Templates ---
templateDecl
    : 'template' ID '(' paramList? ')' '{' processOrControl* '}'
    ;

paramList
    : ID (',' ID)*
    ;

templateCall
    : '@' ID '(' argList? ')'
    ;

argList
    : arg (',' arg)*
    ;

arg
    : idRef
    | INT
    | STRING
    ;

// --- For loop ---
forLoop
    : 'for' ID 'in' INT '..' INT '{' processOrControl* '}'
    ;

// --- Parameterized identifiers ---
// Supports: simpleId, some{L}, some{L+1}, some.complex.id{L}
idRef
    : idPart+
    ;

idPart
    : ID
    | '{' ID '}'                          // simple param substitution
    | '{' ID '+' INT '}'                  // param + offset
    | '{' ID '-' INT '}'                  // param - offset
    ;

// =====================================================================
// Lexer Rules
// =====================================================================

// Keywords are handled as literal strings in parser rules

INT     : [0-9]+ ;

STRING  : '"' (~["\\\r\n] | '\\' .)* '"'
        | '\'' (~['\\\r\n] | '\\' .)* '\''
        ;

// Allows dots, hyphens for function codes like mhdg-rg.call-cc
// Colons are NOT in ID — they are handled as separators in idPart for version suffixes
// Variable names (strict) won't have these - validated in visitor
ID      : [a-zA-Z_] [a-zA-Z0-9_.\-]* ;

LINE_COMMENT  : '//' ~[\r\n]* -> skip ;
BLOCK_COMMENT : '/*' .*? '*/' -> skip ;

WS : [ \t\r\n]+ -> skip ;
