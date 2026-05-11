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
    | dropDatabase
    | dropSchema
    | dropTable
    | insertStatement
    | updateStatement
    | deleteStatement
    | selectStatement
    | beginTransaction
    | commitTransaction
    | rollbackTransaction
    ;

dropDatabase
    : DROP DATABASE ifExists? identifier
    ;

dropSchema
    : DROP SCHEMA ifExists? qualifiedName
    ;

dropTable
    : DROP TABLE ifExists? qualifiedName
    ;

ifNotExists
    : IF NOT EXISTS
    ;

ifExists
    : IF EXISTS
    ;

createDatabase
    : CREATE DATABASE ifNotExists? identifier
    ;

createSchema
    : CREATE SCHEMA ifNotExists? qualifiedName
    ;

createTable
    : CREATE TABLE ifNotExists? qualifiedName '(' columnDef (',' columnDef)* ')'
    ;

columnDef
    : identifier typeName columnConstraint*
    ;

columnConstraint
    : PRIMARY KEY
    | NOT NULL
    | UNIQUE
    | DEFAULT literal
    ;

typeName
    : INT
    | STRING
    | VARCHAR '(' NUMBER ')'
    | BIGINT
    | BOOLEAN
    | DOUBLE
    ;

insertStatement
    : INSERT INTO qualifiedName ('(' identifier (',' identifier)* ')')? VALUES '(' value (',' value)* ')'
    ;

updateStatement
    : UPDATE qualifiedName SET assignment (',' assignment)* whereClause?
    ;

assignment
    : identifier '=' expression
    ;

deleteStatement
    : DELETE FROM qualifiedName whereClause?
    ;

beginTransaction
    : BEGIN
    ;

commitTransaction
    : COMMIT
    ;

rollbackTransaction
    : ROLLBACK
    ;

selectStatement
    : SELECT selectElements FROM tableSource joinClause* whereClause? groupByClause? havingClause? orderByClause? limitClause?
    ;

joinClause
    : (INNER)? JOIN tableSource ON expression
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

groupByClause
    : GROUP BY expression (',' expression)*
    ;

havingClause
    : HAVING expression
    ;

orderByClause
    : ORDER BY orderByElement (',' orderByElement)*
    ;

orderByElement
    : expression (ASC | DESC)?
    ;

limitClause
    : LIMIT NUMBER (OFFSET NUMBER)?
    ;

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
    : comparisonExpression (IS NULL)?
    | comparisonExpression (IS NOT NULL)?
    | comparisonExpression
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
    | LIKE
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
    | functionCall
    | '(' expression ')'
    ;

functionCall
    : functionName '(' (STAR | expression) ')'
    ;

functionName
    : COUNT
    | SUM
    | MIN
    | MAX
    | AVG
    ;

value
    : literal
    | NULL
    ;

literal
    : NUMBER
    | FLOAT_NUMBER
    | STRING_LITERAL
    | TRUE
    | FALSE
    ;

columnReference
    : qualifiedName
    ;

qualifiedName
    : identifier ('.' identifier)*
    ;

identifier
    : ID
    | BACKTICK_ID
    ;

CREATE   : 'CREATE';
DROP     : 'DROP';
DATABASE : 'DATABASE';
SCHEMA   : 'SCHEMA';
TABLE    : 'TABLE';
INSERT   : 'INSERT';
INTO     : 'INTO';
VALUES   : 'VALUES';
UPDATE   : 'UPDATE';
SET      : 'SET';
DELETE   : 'DELETE';
FROM     : 'FROM';
SELECT   : 'SELECT';
WHERE    : 'WHERE';
AS       : 'AS';
IF       : 'IF';
NOT      : 'NOT';
EXISTS   : 'EXISTS';
BEGIN    : 'BEGIN';
COMMIT   : 'COMMIT';
ROLLBACK : 'ROLLBACK';

GROUP    : 'GROUP';
BY       : 'BY';
HAVING   : 'HAVING';
ORDER    : 'ORDER';
ASC      : 'ASC';
DESC     : 'DESC';
LIMIT    : 'LIMIT';
OFFSET   : 'OFFSET';

JOIN     : 'JOIN';
LEFT     : 'LEFT';
RIGHT    : 'RIGHT';
INNER    : 'INNER';
OUTER    : 'OUTER';
ON       : 'ON';

PRIMARY  : 'PRIMARY';
KEY      : 'KEY';
UNIQUE   : 'UNIQUE';
DEFAULT  : 'DEFAULT';
NULL     : 'NULL';
IS       : 'IS';
LIKE     : 'LIKE';
IN       : 'IN';
BETWEEN  : 'BETWEEN';
DISTINCT : 'DISTINCT';

INT      : 'INT';
STRING   : 'STRING';
VARCHAR  : 'VARCHAR';
BIGINT   : 'BIGINT';
BOOLEAN  : 'BOOLEAN';
DOUBLE   : 'DOUBLE';
TRUE     : 'TRUE';
FALSE    : 'FALSE';

AND  : 'AND';
OR   : 'OR';

COUNT : 'COUNT';
SUM   : 'SUM';
MIN   : 'MIN';
MAX   : 'MAX';
AVG   : 'AVG';

STAR : '*';

ID
    : [a-zA-Z_][a-zA-Z0-9_]*
    ;

BACKTICK_ID
    : '`' (~[`\\] | '\\' .)* '`'
    ;

NUMBER
    : [0-9]+
    ;

FLOAT_NUMBER
    : [0-9]+ '.' [0-9]+
    ;

STRING_LITERAL
    : '\'' (~['\\] | '\\' .)* '\''
    ;

WS
    : [ \t\r\n]+ -> skip
    ;

LINE_COMMENT
    : '--' ~[\r\n]* -> skip
    ;

BLOCK_COMMENT
    : '/*' .*? '*/' -> skip
    ;
