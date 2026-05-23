package com.compiladores.api.sql;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SqlAnalysisService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Set<String> SQL_STARTERS = Set.of("SELECT", "WITH", "SHOW", "DESCRIBE", "DESC", "EXPLAIN", "MERGE");
    private static final Set<String> MONGODB_READ_ONLY = Set.of("FIND", "AGGREGATE", "COUNT");
    private static final Pattern MONGO_COLLECTION_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern MONGO_FIELD_PATTERN = Pattern.compile("([\\\"']?)([A-Za-z_][A-Za-z0-9_]*?)\\1\\s*:");
    private static final Pattern MONGO_FILTER_PATTERN = Pattern.compile("([\\\"']?)([A-Za-z_][A-Za-z0-9_]*?)\\1\\s*:\\s*(?:\"([^\"]*)\"|'([^']*)'|([0-9]+(?:\\.[0-9]+)?)|(true|false|null))", Pattern.CASE_INSENSITIVE);
    private static final Set<String> CLAUSE_KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "GROUP", "ORDER", "HAVING", "LIMIT", "OFFSET",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "ON", "WITH", "UNION",
            "RETURNING", "FETCH", "LATERAL"
    );

    private final Clock clock;
    private final int maxRows;

    @Autowired
    public SqlAnalysisService(Clock clock,
                              @Value("${application.sql.max-rows:100}") int maxRows) {
        this.clock = clock;
        this.maxRows = maxRows;
    }

    public SqlAnalysisService(Clock clock) {
        this(clock, 100);
    }

    public SqlAnalysisResponse analyze(SqlAnalysisRequest request) {
        String originalSql = request.sql();
        SqlDialect dialect = SqlDialect.parse(request.dialect());
        String normalizedSql = normalizeSql(originalSql);
        String trimmedSql = originalSql.trim();
        List<SqlAnalysisResponse.SqlDiagnostic> diagnostics = new ArrayList<>();

        if (dialect == SqlDialect.MONGODB) {
            MongoParseResult mongoParseResult = analyzeMongoQuery(trimmedSql, diagnostics);
            SqlAnalysisResponse.SemanticReport semantic = analyzeSemantics(
                    new ParseResult(mongoParseResult.statementType(), mongoParseResult.ast(), mongoParseResult.collections(), mongoParseResult.fields()),
                    diagnostics);
            SqlAnalysisResponse.ExecutionReport execution = executeIfPossible(trimmedSql, mongoParseResult.statementType(), diagnostics);
            boolean valid = diagnostics.stream().noneMatch(d -> "ERROR".equals(d.severity()));
            return new SqlAnalysisResponse(
                    normalizedSql,
                    trimmedSql.length(),
                    trimmedSql.lines().count(),
                    countStatements(trimmedSql),
                    trimmedSql.endsWith(";"),
                    Instant.now(clock),
                    valid,
                    mongoParseResult.statementType(),
                    mongoParseResult.tokens(),
                    mongoParseResult.ast(),
                    diagnostics,
                    semantic,
                    execution
            );
        }

        Lexer lexer = new Lexer(trimmedSql, dialect);
        List<Token> tokens = lexer.tokenize();
        for (Token token : tokens) {
            if (token.type == TokenType.INVALID) {
                diagnostics.add(diagnostic("LEXER", "ERROR",
                        "Token no reconocido: " + token.lexeme, token.line, token.column));
            }
        }

        Parser parser = new Parser(tokens, diagnostics, dialect);
        ParseResult parseResult = parser.parse();
        validateDialectRules(tokens, dialect, diagnostics);
        String statementType = parseResult.statementType();
        SqlAnalysisResponse.SemanticReport semantic = analyzeSemantics(parseResult, diagnostics);
        SqlAnalysisResponse.ExecutionReport execution = executeIfPossible(trimmedSql, statementType, diagnostics);
        boolean valid = diagnostics.stream().noneMatch(d -> "ERROR".equals(d.severity()));

        return new SqlAnalysisResponse(
                normalizedSql,
                trimmedSql.length(),
                trimmedSql.lines().count(),
                countStatements(trimmedSql),
                trimmedSql.endsWith(";"),
                Instant.now(clock),
                valid,
                statementType,
                tokens.stream()
                        .filter(token -> token.type != TokenType.EOF)
                        .map(token -> new SqlAnalysisResponse.SqlToken(token.type.name(), token.lexeme, token.line, token.column))
                        .toList(),
                parseResult.ast(),
                diagnostics,
                semantic,
                execution
        );
    }

    private void validateDialectRules(List<Token> tokens, SqlDialect dialect,
                                      List<SqlAnalysisResponse.SqlDiagnostic> diagnostics) {
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            String upper = token.lexeme.toUpperCase(Locale.ROOT);
            if (dialect == SqlDialect.SQLSERVER && "LIMIT".equals(upper)) {
                diagnostics.add(diagnostic("PARSER", "ERROR",
                        "La clausula LIMIT no es compatible con SQL Server. Use TOP o OFFSET/FETCH.", token.line, token.column));
            }

            if ("SELECT".equals(upper)) {
                // scan ahead for TOP, allowing DISTINCT/ALL
                for (int j = i + 1; j < tokens.size(); j++) {
                    Token t = tokens.get(j);
                    String up = t.lexeme.toUpperCase(Locale.ROOT);
                    if (t.type == TokenType.SEMICOLON || t.type == TokenType.EOF) break;
                    if ("DISTINCT".equals(up) || "ALL".equals(up)) {
                        continue;
                    }
                    if ("TOP".equals(up)) {
                        if (dialect != SqlDialect.SQLSERVER) {
                            diagnostics.add(diagnostic("PARSER", "ERROR",
                                    "La clausula TOP no es compatible con " + dialectName(dialect) + ". Use LIMIT.", t.line, t.column));
                        }
                        break;
                    }
                    break;
                }
            }

            if ("RETURNING".equals(upper) && dialect != SqlDialect.POSTGRESQL) {
                diagnostics.add(diagnostic("PARSER", "ERROR",
                        "La clausula RETURNING es especifica de PostgreSQL y no es compatible con " +
                        dialectName(dialect) + ".", token.line, token.column));
            }

            if ("ILIKE".equals(upper) && dialect != SqlDialect.POSTGRESQL) {
                diagnostics.add(diagnostic("PARSER", "ERROR",
                        "El operador ILIKE es especifico de PostgreSQL. Use LIKE para " +
                        dialectName(dialect) + ".", token.line, token.column));
            }

            if ("::".equals(token.lexeme) && dialect != SqlDialect.POSTGRESQL) {
                diagnostics.add(diagnostic("PARSER", "ERROR",
                        "El operador :: (cast) es especifico de PostgreSQL. Use CAST(valor AS tipo) en " +
                        dialectName(dialect) + ".", token.line, token.column));
            }

            if ("MERGE".equals(upper) && dialect != SqlDialect.SQLSERVER) {
                diagnostics.add(diagnostic("PARSER", "ERROR",
                        "La sentencia MERGE solo esta habilitada para SQL Server en este analizador.", token.line, token.column));
            }

            if ("FETCH".equals(upper) && dialect == SqlDialect.MYSQL) {
                diagnostics.add(diagnostic("PARSER", "ERROR",
                        "La clausula FETCH no es compatible con MySQL. Use LIMIT.", token.line, token.column));
            }

            if ("OFFSET".equals(upper) && dialect == SqlDialect.SQLSERVER && !hasKeywordBefore(tokens, i, "ORDER")) {
                diagnostics.add(diagnostic("PARSER", "ERROR",
                        "OFFSET en SQL Server requiere una clausula ORDER BY.", token.line, token.column));
            }
        }
    }

    private boolean hasKeywordBefore(List<Token> tokens, int index, String keyword) {
        for (int i = index - 1; i >= 0; i--) {
            Token token = tokens.get(i);
            if (token.type == TokenType.SEMICOLON) {
                return false;
            }
            if (token.lexeme.equalsIgnoreCase(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String dialectName(SqlDialect dialect) {
        return switch (dialect) {
            case MYSQL -> "MySQL";
            case SQLSERVER -> "SQL Server";
            case POSTGRESQL -> "PostgreSQL";
            case MONGODB -> "MongoDB";
        };
    }

    private SqlAnalysisResponse.SemanticReport analyzeSemantics(ParseResult parseResult,
                                                                List<SqlAnalysisResponse.SqlDiagnostic> diagnostics) {
        List<String> warnings = new ArrayList<>();
        for (String column : parseResult.columns()) {
            if (!"*".equals(column) && column.contains(".")) {
                warnings.add("Columna calificada detectada: " + column);
            }
        }
        return new SqlAnalysisResponse.SemanticReport(true, List.copyOf(parseResult.tables()), List.copyOf(parseResult.columns()), warnings);
    }

    private SqlAnalysisResponse.ExecutionReport executeIfPossible(String sql, String statementType,
                                                                  List<SqlAnalysisResponse.SqlDiagnostic> diagnostics) {
        boolean hasErrors = diagnostics.stream().anyMatch(d -> "ERROR".equals(d.severity()));
        if (hasErrors) {
            return new SqlAnalysisResponse.ExecutionReport(false,
                    "No se ejecuto la consulta debido a errores de sintaxis o semantica.",
                    0, 0, List.of(), List.of());
        }

        if (MONGODB_READ_ONLY.contains(statementType)) {
            return new SqlAnalysisResponse.ExecutionReport(false,
                    "Consulta MongoDB válida. Solo se valida sintaxis y estructura; no hay conexión a base de datos.",
                    0, 0, List.of(), List.of());
        }

        return new SqlAnalysisResponse.ExecutionReport(false,
                "Sin ejecucion: este servicio opera en modo analisis sin conexion a base de datos.",
                0, 0, List.of(), List.of());
    }

    private SqlAnalysisResponse.ExecutionReport executeMongoQuery(String sql, String operation,
                                                                  List<SqlAnalysisResponse.SqlDiagnostic> diagnostics) {
        String content = sql.trim();
        if (content.endsWith(";")) {
            content = content.substring(0, content.length() - 1).trim();
        }

        int openParen = content.indexOf('(');
        int closeParen = content.lastIndexOf(')');
        if (openParen < 0 || closeParen < openParen) {
            return new SqlAnalysisResponse.ExecutionReport(false,
                    "Operacion MongoDB valida, pero no se pudo ejecutar por sintaxis de paréntesis.",
                    0, 0, List.of(), List.of());
        }

        String arguments = content.substring(openParen + 1, closeParen).trim();
        Map<String, Object> filter = parseMongoFilter(arguments);

        if ("FIND".equals(operation)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            if (!filter.isEmpty()) {
                rows.add(new LinkedHashMap<>(filter));
            }
            List<String> columns = new ArrayList<>(filter.keySet());
            String message = rows.isEmpty()
                    ? "Consulta MongoDB válida. No se encontraron resultados simulados."
                    : "Consulta MongoDB válida. Resultados simulados generados.";
            return new SqlAnalysisResponse.ExecutionReport(true, message,
                    0, rows.size(), columns, rows);
        }

        if ("COUNT".equals(operation)) {
            int count = filter.isEmpty() ? 0 : 1;
            Map<String, Object> row = Map.of("count", count);
            return new SqlAnalysisResponse.ExecutionReport(true,
                    "Consulta MongoDB COUNT válida. Resultado simulado.",
                    0, 1, List.of("count"), List.of(row));
        }

        if ("AGGREGATE".equals(operation)) {
            return new SqlAnalysisResponse.ExecutionReport(true,
                    "Consulta MongoDB AGGREGATE válida. Ejecución simulada sin resultados concretos.",
                    0, 0, List.of(), List.of());
        }

        return new SqlAnalysisResponse.ExecutionReport(false,
                "Operacion MongoDB no soportada para ejecucion simulada.",
                0, 0, List.of(), List.of());
    }

    private Map<String, Object> parseMongoFilter(String arguments) {
        Map<String, Object> filter = new LinkedHashMap<>();
        Matcher matcher = MONGO_FILTER_PATTERN.matcher(arguments);
        while (matcher.find()) {
            String key = matcher.group(2);
            String stringValue = matcher.group(3);
            String singleValue = matcher.group(4);
            String numericValue = matcher.group(5);
            String booleanNullValue = matcher.group(6);
            Object value = null;
            if (stringValue != null) {
                value = stringValue;
            } else if (singleValue != null) {
                value = singleValue;
            } else if (numericValue != null) {
                if (numericValue.contains(".")) {
                    value = Double.parseDouble(numericValue);
                } else {
                    value = Long.parseLong(numericValue);
                }
            } else if (booleanNullValue != null) {
                String normalized = booleanNullValue.toLowerCase(Locale.ROOT);
                if ("true".equals(normalized)) {
                    value = true;
                } else if ("false".equals(normalized)) {
                    value = false;
                } else {
                    value = null;
                }
            }
            filter.put(key, value);
        }
        return filter;
    }

    private SqlAnalysisResponse.SqlDiagnostic diagnostic(String phase, String severity, String message, int line, int column) {
        return new SqlAnalysisResponse.SqlDiagnostic(phase, severity, message, line, column);
    }

    private String normalizeSql(String sql) {
        return WHITESPACE.matcher(sql.trim()).replaceAll(" ");
    }

    private MongoParseResult analyzeMongoQuery(String sql, List<SqlAnalysisResponse.SqlDiagnostic> diagnostics) {
        String content = sql.trim();
        if (content.endsWith(";")) {
            content = content.substring(0, content.length() - 1).trim();
        }

        if (!content.startsWith("db.")) {
            diagnostics.add(diagnostic("MONGO", "ERROR", "Las consultas MongoDB deben iniciar con db.<coleccion>.<operacion>(...).", 1, 1));
            return new MongoParseResult("MONGODB", emptyAst(), Set.of(), Set.of(), List.of());
        }

        int collectionEnd = content.indexOf('.', 3);
        if (collectionEnd < 0) {
            diagnostics.add(diagnostic("MONGO", "ERROR", "No se detecto el nombre de coleccion en la consulta MongoDB.", 1, 4));
            return new MongoParseResult("MONGODB", emptyAst(), Set.of(), Set.of(), List.of());
        }

        String collection = content.substring(3, collectionEnd).trim();
        if (collection.isEmpty()) {
            diagnostics.add(diagnostic("MONGO", "ERROR", "El nombre de la coleccion no puede estar vacio.", 1, 4));
            return new MongoParseResult("MONGODB", emptyAst(), Set.of(), Set.of(), List.of());
        }
        if (!MONGO_COLLECTION_PATTERN.matcher(collection).matches()) {
            diagnostics.add(diagnostic("MONGO", "ERROR", "El nombre de la coleccion MongoDB solo puede usar letras, numeros y guion bajo, y debe iniciar con letra o guion bajo.", 1, 4));
        }

        int opStart = collectionEnd + 1;
        int openParen = content.indexOf('(', opStart);
        int closeParen = content.lastIndexOf(')');
        if (openParen < 0 || closeParen < openParen) {
            diagnostics.add(diagnostic("MONGO", "ERROR", "Falta la clausula de parametros o el paréntesis de cierre en la consulta MongoDB.", 1, opStart + 1));
            return new MongoParseResult("MONGODB", emptyAst(), Set.of(collection), Set.of(), List.of());
        }

        String operation = content.substring(opStart, openParen).trim().toUpperCase(Locale.ROOT);
        String arguments = content.substring(openParen + 1, closeParen).trim();
        String trailing = content.substring(closeParen + 1).trim();
        if (!trailing.isEmpty()) {
            diagnostics.add(diagnostic("MONGO", "ERROR", "No se permite contenido despues del parentesis de cierre en la consulta MongoDB.", 1, closeParen + 2));
        }
        if (operation.isEmpty()) {
            diagnostics.add(diagnostic("MONGO", "ERROR", "No se detecto la operacion MongoDB.", 1, opStart + 1));
        }
        if (arguments.isEmpty() && !"FIND".equals(operation) && !"COUNT".equals(operation)) {
            diagnostics.add(diagnostic("MONGO", "ERROR", "La operación MongoDB requiere argumentos válidos.", 1, openParen + 2));
        }

        if (!MONGODB_READ_ONLY.contains(operation)) {
            diagnostics.add(diagnostic("MONGO", "ERROR", "Operación MongoDB no soportada: " + operation + ". Solo se admiten FIND, AGGREGATE y COUNT.", 1, opStart + 1));
        }

        if (!arguments.isEmpty() && !hasBalancedBrackets(arguments)) {
            diagnostics.add(diagnostic("MONGO", "ERROR", "Los argumentos MongoDB no tienen una sintaxis de llaves o corchetes balanceada.", 1, openParen + 2));
        }
        validateMongoArguments(operation, arguments, diagnostics, openParen + 2);

        List<String> fields = extractMongoFields(arguments);
        List<SqlAnalysisResponse.SqlToken> tokens = List.of(
                new SqlAnalysisResponse.SqlToken("DB", "db", 1, 1),
                new SqlAnalysisResponse.SqlToken("DOT", ".", 1, 3),
                new SqlAnalysisResponse.SqlToken("COLLECTION", collection, 1, 4),
                new SqlAnalysisResponse.SqlToken("OPERATION", operation, 1, opStart + 1),
                new SqlAnalysisResponse.SqlToken("LPAREN", "(", 1, openParen + 1),
                new SqlAnalysisResponse.SqlToken("ARGUMENTS", arguments, 1, openParen + 2),
                new SqlAnalysisResponse.SqlToken("RPAREN", ")", 1, closeParen + 1)
        );

        SqlAnalysisResponse.AstNode ast = node("MongoQuery", "db." + collection + "." + operation, null, List.of(
                node("Collection", collection, null, List.of()),
                node("Operation", operation, null, List.of()),
                node("Arguments", arguments.isEmpty() ? "{}" : arguments, null, List.of())
        ));

        return new MongoParseResult(operation, ast, Set.of(collection), new LinkedHashSet<>(fields), tokens);
    }

    private void validateMongoArguments(String operation, String arguments,
                                        List<SqlAnalysisResponse.SqlDiagnostic> diagnostics,
                                        int column) {
        if (arguments.isEmpty()) {
            return;
        }
        if (Set.of("FIND", "COUNT").contains(operation) && !(arguments.startsWith("{") && arguments.endsWith("}"))) {
            diagnostics.add(diagnostic("MONGO", "ERROR",
                    "Las operaciones FIND y COUNT requieren un documento de filtro entre llaves: { ... }.", 1, column));
        }
        if ("AGGREGATE".equals(operation) && !(arguments.startsWith("[") && arguments.endsWith("]"))) {
            diagnostics.add(diagnostic("MONGO", "ERROR",
                    "La operacion AGGREGATE requiere un arreglo de etapas entre corchetes: [ ... ].", 1, column));
        }
    }

    private boolean hasBalancedBrackets(String content) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        char quoteChar = '\u0000';
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inString) {
                if (c == quoteChar) {
                    inString = false;
                } else if (c == '\\') {
                    i++;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                inString = true;
                quoteChar = c;
                continue;
            }
            if (c == '{' || c == '[') {
                stack.push(c);
            } else if (c == '}' || c == ']') {
                if (stack.isEmpty()) {
                    return false;
                }
                char opening = stack.pop();
                if ((opening == '{' && c != '}') || (opening == '[' && c != ']')) {
                    return false;
                }
            }
        }
        return stack.isEmpty() && !inString;
    }

    private List<String> extractMongoFields(String arguments) {
        List<String> fields = new ArrayList<>();
        Matcher matcher = MONGO_FIELD_PATTERN.matcher(arguments);
        while (matcher.find()) {
            fields.add(matcher.group(2));
        }
        return fields;
    }

    private SqlAnalysisResponse.AstNode emptyAst() {
        return node("MongoQuery", "Invalid", null, List.of());
    }

    private long countStatements(String sql) {
        return Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(fragment -> !fragment.isEmpty())
                .count();
    }

    private SqlAnalysisResponse.AstNode node(String type, String label, String value, List<SqlAnalysisResponse.AstNode> children) {
        return new SqlAnalysisResponse.AstNode(type, label, value, children);
    }

    private enum TokenType {
        KEYWORD, IDENTIFIER, NUMBER, STRING, OPERATOR, COMMA, DOT, SEMICOLON, LPAREN, RPAREN, ASTERISK, INVALID, EOF
    }

    private record Token(TokenType type, String lexeme, int line, int column) {
    }

    private record ParseResult(String statementType, SqlAnalysisResponse.AstNode ast, Set<String> tables, Set<String> columns) {
    }

    private record MongoParseResult(String statementType,
                                    SqlAnalysisResponse.AstNode ast,
                                    Set<String> collections,
                                    Set<String> fields,
                                    List<SqlAnalysisResponse.SqlToken> tokens) {
    }

    private static final class Lexer {
        private final SqlDialect dialect;
        private static final Set<String> KEYWORDS = Set.of(
                "SELECT", "FROM", "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "ON",
                "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "AS", "WITH", "UNION", "ALL",
                "DISTINCT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "SHOW", "DESCRIBE",
                "DESC", "EXPLAIN", "AND", "OR", "NOT", "IN", "IS", "NULL", "LIKE", "BETWEEN", "CASE",
                "WHEN", "THEN", "ELSE", "END", "PARTITION", "RANK", "AVG", "SUM", "COUNT", "ROUND",
                "CAST", "INTEGER", "CURRENT", "ROW", "PRECEDING",
                // SQL Server
                "TOP", "FETCH", "NEXT", "ROWS", "ONLY", "FIRST", "MERGE", "INTO", "USING", "MATCHED",
                "SOURCE", "TARGET", "SET", "VALUES",
                // PostgreSQL
                "RETURNING", "ILIKE", "LATERAL", "SIMILAR", "FILTER", "OVER", "NULLS"
        );

        private final String source;
        private int index;
        private int line = 1;
        private int column = 1;

        private Lexer(String source, SqlDialect dialect) {
            this.source = source;
            this.dialect = dialect;
        }

        private List<Token> tokenize() {
            List<Token> tokens = new ArrayList<>();
            while (!isAtEnd()) {
                char current = peek();
                if (Character.isWhitespace(current)) {
                    advance();
                } else if (current == '-' && peekNext() == '-') {
                    skipLineComment();
                } else if (dialect == SqlDialect.MYSQL && current == '#') {
                    skipLineComment();
                } else if (current == '[' && dialect == SqlDialect.SQLSERVER) {
                    tokens.add(readBracketIdentifier());
                } else if (Character.isLetter(current) || current == '_') {
                    tokens.add(readIdentifier());
                } else if (Character.isDigit(current)) {
                    tokens.add(readNumber());
                } else if (current == '`' && dialect == SqlDialect.MYSQL) {
                    tokens.add(readQuotedIdentifier('`'));
                } else if (current == '\'' || current == '"') {
                    tokens.add(readString(current));
                } else if (current == '$' && dialect == SqlDialect.POSTGRESQL) {
                    tokens.add(readPostgresParam());
                } else {
                    tokens.add(readSymbol());
                }
            }
            tokens.add(new Token(TokenType.EOF, "", line, column));
            return tokens;
        }

        private Token readIdentifier() {
            int startLine = line;
            int startColumn = column;
            StringBuilder value = new StringBuilder();
            while (!isAtEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_' || peek() == '$')) {
                value.append(advance());
            }
            String lexeme = value.toString();
            TokenType type = KEYWORDS.contains(lexeme.toUpperCase(Locale.ROOT)) ? TokenType.KEYWORD : TokenType.IDENTIFIER;
            return new Token(type, lexeme, startLine, startColumn);
        }

        private Token readNumber() {
            int startLine = line;
            int startColumn = column;
            StringBuilder value = new StringBuilder();
            while (!isAtEnd() && (Character.isDigit(peek()) || peek() == '.')) {
                value.append(advance());
            }
            return new Token(TokenType.NUMBER, value.toString(), startLine, startColumn);
        }

        private Token readString(char quote) {
            int startLine = line;
            int startColumn = column;
            StringBuilder value = new StringBuilder();
            value.append(advance());
            if (quote == '"' && dialect == SqlDialect.POSTGRESQL) {
                return readQuotedIdentifierFromOpenQuote('"', value, startLine, startColumn);
            }
            while (!isAtEnd() && peek() != quote) {
                if (peek() == '\\') {
                    value.append(advance());
                }
                value.append(advance());
            }
            if (isAtEnd()) {
                return new Token(TokenType.INVALID, value.toString(), startLine, startColumn);
            }
            value.append(advance());
            return new Token(TokenType.STRING, value.toString(), startLine, startColumn);
        }

        private Token readQuotedIdentifier(char quote) {
            int startLine = line;
            int startColumn = column;
            StringBuilder value = new StringBuilder();
            value.append(advance());
            return readQuotedIdentifierFromOpenQuote(quote, value, startLine, startColumn);
        }

        private Token readQuotedIdentifierFromOpenQuote(char quote, StringBuilder value, int startLine, int startColumn) {
            while (!isAtEnd() && peek() != quote) {
                value.append(advance());
            }
            if (isAtEnd()) {
                return new Token(TokenType.INVALID, value.toString(), startLine, startColumn);
            }
            value.append(advance());
            String content = value.toString().substring(1, value.length() - 1);
            return new Token(TokenType.IDENTIFIER, content, startLine, startColumn);
        }

        private Token readBracketIdentifier() {
            int startLine = line;
            int startColumn = column;
            StringBuilder value = new StringBuilder();
            // consume '['
            advance();
            while (!isAtEnd() && peek() != ']') {
                value.append(advance());
            }
            if (isAtEnd()) {
                return new Token(TokenType.INVALID, value.toString(), startLine, startColumn);
            }
            // consume ']'
            advance();
            return new Token(TokenType.IDENTIFIER, value.toString(), startLine, startColumn);
        }

        private Token readPostgresParam() {
            int startLine = line;
            int startColumn = column;
            advance(); // consume '$'
            StringBuilder value = new StringBuilder("$");
            while (!isAtEnd() && Character.isDigit(peek())) {
                value.append(advance());
            }
            if (value.length() == 1) {
                return new Token(TokenType.INVALID, "$", startLine, startColumn);
            }
            return new Token(TokenType.IDENTIFIER, value.toString(), startLine, startColumn);
        }

        private Token readSymbol() {
            int startLine = line;
            int startColumn = column;
            char current = advance();
            return switch (current) {
                case ',' -> new Token(TokenType.COMMA, ",", startLine, startColumn);
                case '.' -> new Token(TokenType.DOT, ".", startLine, startColumn);
                case ';' -> new Token(TokenType.SEMICOLON, ";", startLine, startColumn);
                case '(' -> new Token(TokenType.LPAREN, "(", startLine, startColumn);
                case ')' -> new Token(TokenType.RPAREN, ")", startLine, startColumn);
                case '*' -> new Token(TokenType.ASTERISK, "*", startLine, startColumn);
                case '=', '>', '<', '+', '-', '/', '%', '!', '|', '&' -> {
                    String op = Character.toString(current);
                    if (!isAtEnd() && "=<>|&".indexOf(peek()) >= 0) {
                        op += advance();
                    }
                    yield new Token(TokenType.OPERATOR, op, startLine, startColumn);
                }
                case ':' -> {
                    if (!isAtEnd() && peek() == ':') {
                        advance();
                        yield new Token(TokenType.OPERATOR, "::", startLine, startColumn);
                    }
                    yield new Token(TokenType.INVALID, ":", startLine, startColumn);
                }
                default -> new Token(TokenType.INVALID, Character.toString(current), startLine, startColumn);
            };
        }

        private void skipLineComment() {
            while (!isAtEnd() && peek() != '\n') {
                advance();
            }
        }

        private boolean isAtEnd() {
            return index >= source.length();
        }

        private char peek() {
            return source.charAt(index);
        }

        private char peekNext() {
            return index + 1 >= source.length() ? '\0' : source.charAt(index + 1);
        }

        private char advance() {
            char current = source.charAt(index++);
            if (current == '\n') {
                line++;
                column = 1;
            } else {
                column++;
            }
            return current;
        }
    }

    private final class Parser {
        private final List<Token> tokens;
        private final List<SqlAnalysisResponse.SqlDiagnostic> diagnostics;
        private final SqlDialect dialect;
        private int index;
        private final Set<String> tables = new LinkedHashSet<>();
        private final Set<String> columns = new LinkedHashSet<>();

        private Parser(List<Token> tokens, List<SqlAnalysisResponse.SqlDiagnostic> diagnostics, SqlDialect dialect) {
            this.tokens = tokens;
            this.diagnostics = diagnostics;
            this.dialect = dialect;
        }

        private ParseResult parse() {
            if (tokens.isEmpty() || current().type == TokenType.EOF) {
                diagnostics.add(diagnostic("PARSER", "ERROR", "La consulta esta vacia.", 1, 1));
                return new ParseResult("UNKNOWN", node("Statement", "UNKNOWN", "", List.of()), tables, columns);
            }

            validateBalancedParentheses();
            validateSingleStatement();
            String statementType = current().lexeme.toUpperCase(Locale.ROOT);
            if (!SQL_STARTERS.contains(statementType)) {
                diagnostics.add(diagnostic("PARSER", "ERROR",
                        "Solo se permite validar sentencias soportadas: SELECT, WITH, SHOW, DESCRIBE, EXPLAIN o MERGE.",
                        current().line, current().column));
            }
            SqlAnalysisResponse.AstNode ast = parseSelectLike(statementType);
            return new ParseResult(statementType, ast, tables, columns);
        }

        private SqlAnalysisResponse.AstNode parseSelectLike(String statementType) {
            List<SqlAnalysisResponse.AstNode> children = new ArrayList<>();
            while (current().type != TokenType.EOF) {
                Token token = current();
                if (isKeyword("SELECT")) {
                    children.add(parseSelectClause());
                } else if (isKeyword("FROM")) {
                    children.add(parseTableClause("FROM"));
                } else if (isJoinKeyword()) {
                    children.add(parseTableClause("JOIN"));
                } else if (CLAUSE_KEYWORDS.contains(token.lexeme.toUpperCase(Locale.ROOT))) {
                    children.add(parseGenericClause(token.lexeme.toUpperCase(Locale.ROOT)));
                } else {
                    advance();
                }
            }
            if (children.stream().noneMatch(child -> "SELECT".equals(child.label())) && ("SELECT".equals(statementType) || "WITH".equals(statementType))) {
                diagnostics.add(diagnostic("PARSER", "ERROR", "No se encontro clausula SELECT.", 1, 1));
            }
            if (children.stream().noneMatch(child -> "FROM".equals(child.label())) && ("SELECT".equals(statementType) || "WITH".equals(statementType))) {
                diagnostics.add(diagnostic("PARSER", "WARN", "No se encontro clausula FROM; puede ser una consulta escalar.", 1, 1));
            }
            return node("Statement", statementType, "", children);
        }

        private SqlAnalysisResponse.AstNode parseSelectClause() {
            Token select = advance();
            List<String> values = collectUntil(Set.of("FROM"));
            if (values.isEmpty()) {
                diagnostics.add(diagnostic("PARSER", "ERROR", "La clausula SELECT requiere al menos una expresion.", select.line, select.column));
            }
            splitCsv(values).forEach(column -> {
                if (!column.isBlank()) {
                    columns.add(column);
                }
            });
            return node("Clause", "SELECT", select.lexeme, values.stream()
                    .map(value -> node("ColumnExpression", value, value, List.of()))
                    .toList());
        }

        private SqlAnalysisResponse.AstNode parseTableClause(String label) {
            Token clause = advance();
            if ("JOIN".equals(label) && !clause.lexeme.equalsIgnoreCase("JOIN")) {
                while (!current().lexeme.equalsIgnoreCase("JOIN") && current().type != TokenType.EOF) {
                    advance();
                }
                if (current().lexeme.equalsIgnoreCase("JOIN")) {
                    advance();
                }
            }
            List<SqlAnalysisResponse.AstNode> refs = new ArrayList<>();
            while (current().type != TokenType.EOF && !CLAUSE_KEYWORDS.contains(current().lexeme.toUpperCase(Locale.ROOT))) {
                if (current().type == TokenType.IDENTIFIER || current().type == TokenType.KEYWORD) {
                    String name = readQualifiedName();
                    if (!name.isBlank() && !"AS".equalsIgnoreCase(name)) {
                        tables.add(name);
                        refs.add(node("TableReference", name, name, List.of()));
                        break;
                    }
                } else {
                    advance();
                }
            }
            if (refs.isEmpty()) {
                diagnostics.add(diagnostic("PARSER", "ERROR", "La clausula " + label + " requiere una referencia de tabla.", clause.line, clause.column));
            }
            return node("Clause", label, label, refs);
        }

        private SqlAnalysisResponse.AstNode parseGenericClause(String label) {
            Token clause = advance();
            List<String> parts = collectUntil(CLAUSE_KEYWORDS);
            return node("Clause", label, clause.lexeme, parts.stream()
                    .map(value -> node("Expression", value, value, List.of()))
                    .toList());
        }

        private List<String> collectUntil(Set<String> stopKeywords) {
            List<String> values = new ArrayList<>();
            StringBuilder currentValue = new StringBuilder();
            int depth = 0;
            while (current().type != TokenType.EOF) {
                Token token = current();
                String upper = token.lexeme.toUpperCase(Locale.ROOT);
                if (depth == 0 && token.type == TokenType.KEYWORD && stopKeywords.contains(upper)) {
                    break;
                }
                if (token.type == TokenType.LPAREN) {
                    depth++;
                } else if (token.type == TokenType.RPAREN && depth > 0) {
                    depth--;
                }
                if (token.type == TokenType.COMMA && depth == 0) {
                    values.add(currentValue.toString().trim());
                    currentValue.setLength(0);
                } else {
                    if (!currentValue.isEmpty()) {
                        currentValue.append(' ');
                    }
                    currentValue.append(token.lexeme);
                }
                advance();
            }
            if (!currentValue.isEmpty()) {
                values.add(currentValue.toString().trim());
            }
            return values;
        }

        private List<String> splitCsv(List<String> values) {
            return values.stream()
                    .map(value -> value.replaceAll("\\s+AS\\s+.*$", "").trim())
                    .filter(value -> !value.isBlank())
                    .toList();
        }

        private String readQualifiedName() {
            StringBuilder value = new StringBuilder(advance().lexeme);
            while (current().type == TokenType.DOT) {
                advance();
                if (current().type == TokenType.IDENTIFIER || current().type == TokenType.KEYWORD) {
                    value.append('.').append(advance().lexeme);
                }
            }
            return value.toString();
        }

        private void validateBalancedParentheses() {
            int depth = 0;
            for (Token token : tokens) {
                if (token.type == TokenType.LPAREN) {
                    depth++;
                } else if (token.type == TokenType.RPAREN) {
                    depth--;
                    if (depth < 0) {
                        diagnostics.add(diagnostic("PARSER", "ERROR", "Parentesis de cierre sin apertura.", token.line, token.column));
                        depth = 0;
                    }
                }
            }
            if (depth > 0) {
                diagnostics.add(diagnostic("PARSER", "ERROR", "Falta cerrar uno o mas parentesis.", 1, 1));
            }
        }

        private void validateSingleStatement() {
            int semicolonCount = 0;
            int firstSemicolon = -1;
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).type == TokenType.SEMICOLON) {
                    semicolonCount++;
                    if (firstSemicolon < 0) {
                        firstSemicolon = i;
                    }
                }
            }
            if (semicolonCount > 1) {
                diagnostics.add(diagnostic("PARSER", "ERROR", "Solo se permite una sentencia por ejecucion.", 1, 1));
                return;
            }
            if (firstSemicolon >= 0) {
                for (int i = firstSemicolon + 1; i < tokens.size(); i++) {
                    Token token = tokens.get(i);
                    if (token.type != TokenType.EOF) {
                        diagnostics.add(diagnostic("PARSER", "ERROR", "No se permite contenido despues del punto y coma final.", token.line, token.column));
                        return;
                    }
                }
            }
        }

        private boolean isKeyword(String keyword) {
            return current().lexeme.equalsIgnoreCase(keyword);
        }

        private boolean isJoinKeyword() {
            return Set.of("JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS").contains(current().lexeme.toUpperCase(Locale.ROOT));
        }

        private Token current() {
            return tokens.get(Math.min(index, tokens.size() - 1));
        }

        private Token advance() {
            Token token = current();
            if (index < tokens.size() - 1) {
                index++;
            }
            return token;
        }

        private SqlAnalysisResponse.AstNode node(String type, String label, String value, List<SqlAnalysisResponse.AstNode> children) {
            return new SqlAnalysisResponse.AstNode(type, label, value, children);
        }
    }
}
