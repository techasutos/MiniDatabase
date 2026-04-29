grammar SQL;


// =====================
// ENTRY POINT
// =====================

parse
    : statement EOF
    ;

// =====================
// STATEMENTS
// =====================

statement
    : createDatabase
    | createSchema
    | createTable
    | insertStatement
    | selectStatement
    ;

// =====================
// DDL
// =====================

createDatabase
    : CREATE DATABASE ifNotExists? identifier
    ;

createSchema
    : CREATE SCHEMA ifNotExists? qualifiedName
    ;

createTable
    : CREATE TABLE ifNotExists? qualifiedName '(' columnDef (',' columnDef)* ')'
    ;

ifNotExists
    : IF NOT EXISTS
    ;

// =====================
// TABLE DEFINITIONS
// =====================

columnDef
    : identifier typeName
    ;

typeName
    : INT
    | STRING
    ;

// =====================
// DML
// =====================

insertStatement
    : INSERT INTO qualifiedName VALUES '(' value (',' value)* ')'
    ;

// =====================
// SELECT
// =====================

selectStatement
    : SELECT selectElements FROM tableSource whereClause?
    ;

selectElements
    : '*'
    | selectElement (',' selectElement)*
    ;

selectElement
    : expression (AS? identifier)?
    ;

tableSource
    : qualifiedName (AS? identifier)?
    ;

whereClause
    : WHERE expression
    ;

// =====================
// EXPRESSIONS
// =====================

expression
    : logicalExpression
    ;

logicalExpression
    : logicalExpression AND logicalExpression
    | logicalExpression OR logicalExpression
    | NOT logicalExpression
    | predicate
    ;

predicate
    : comparisonExpression
    ;

comparisonExpression
    : additiveExpression (comparisonOperator additiveExpression)?
    ;

comparisonOperator
    : '='
    | '!='
    | '<'
    | '<='
    | '>'
    | '>='
    ;

additiveExpression
    : multiplicativeExpression (('+' | '-') multiplicativeExpression)*
    ;

multiplicativeExpression
    : primaryExpression (('*' | '/') primaryExpression)*
    ;

primaryExpression
    : literal
    | columnReference
    | '(' expression ')'
    ;

// =====================
// VALUES
// =====================

value
    : literal
    ;

literal
    : NUMBER
    | STRING_LITERAL
    ;

// =====================
// IDENTIFIERS
// =====================

columnReference
    : qualifiedName
    ;

qualifiedName
    : identifier ('.' identifier)*
    ;

identifier
    : ID
    ;

// =====================
// KEYWORDS
// =====================

CREATE : 'CREATE';
DATABASE : 'DATABASE';
SCHEMA : 'SCHEMA';
TABLE : 'TABLE';
INSERT : 'INSERT';
INTO : 'INTO';
VALUES : 'VALUES';
SELECT : 'SELECT';
FROM : 'FROM';
WHERE : 'WHERE';
AS : 'AS';
IF : 'IF';
NOT : 'NOT';
EXISTS : 'EXISTS';

INT : 'INT';
STRING : 'STRING';

AND : 'AND';
OR : 'OR';

// =====================
// LEXER RULES
// =====================

ID
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

NUMBER
    : [0-9]+
    ;

STRING_LITERAL
    : '\'' (~['\\] | '\\' .)* '\''
    ;

WS
    : [ \t\r\n]+ -> skip
    ;