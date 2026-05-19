package com.compiladores.api.sql;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SqlAnalysisService {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Set<String> READ_ONLY_STARTERS = Set.of("SELECT", "WITH", "SHOW", "DESCRIBE", "DESC", "EXPLAIN");
    private static final Set<String> CLAUSE_KEYWORDS = Set.of(
            "SELECT", "FROM", "WHERE", "GROUP", "ORDER", "HAVING", "LIMIT", "OFFSET",
            "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "ON", "WITH", "UNION"
    );

    private final Clock clock;
    @Nullable
    private final JdbcTemplate jdbcTemplate;
    private final int maxRows;

    @Autowired
    public SqlAnalysisService(Clock clock, @Nullable JdbcTemplate jdbcTemplate,
                              @Value("${application.sql.max-rows:100}") int maxRows) {
        this.clock = clock;
        this.jdbcTemplate = jdbcTemplate;
        this.maxRows = maxRows;
    }

    SqlAnalysisService(Clock clock) {
        this(clock, null, 100);
    }

    public SqlAnalysisResponse analyze(SqlAnalysisRequest request) {
        String originalSql = request.sql();
        String normalizedSql = normalizeSql(originalSql);
        String trimmedSql = originalSql.trim();
        List<SqlAnalysisResponse.SqlDiagnostic> diagnostics = new ArrayList<>();

        Lexer lexer = new Lexer(trimmedSql);
        List<Token> tokens = lexer.tokenize();
        for (Token token : tokens) {
            if (token.type == TokenType.INVALID) {
                diagnostics.add(diagnostic("LEXER", "ERROR",
                        "Token no reconocido: " + token.lexeme, token.line, token.column));
            }
        }

        Parser parser = new Parser(tokens, diagnostics);
        ParseResult parseResult = parser.parse();
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

    private SqlAnalysisResponse.SemanticReport analyzeSemantics(ParseResult parseResult,
                                                                List<SqlAnalysisResponse.SqlDiagnostic> diagnostics) {
        List<String> warnings = new ArrayList<>();
        if (jdbcTemplate == null) {
            warnings.add("No hay JdbcTemplate configurado; se omitio validacion semantica contra MySQL.");
            return new SqlAnalysisResponse.SemanticReport(false, List.copyOf(parseResult.tables()), List.copyOf(parseResult.columns()), warnings);
        }

        try {
            DataSource dataSource = Objects.requireNonNull(jdbcTemplate.getDataSource());
            try (Connection connection = dataSource.getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                for (String table : parseResult.tables()) {
                    if (!tableExists(metaData, table)) {
                        diagnostics.add(diagnostic("SEMANTIC", "ERROR",
                                "La tabla '" + table + "' no existe en la base configurada.", 1, 1));
                    }
                }
            }
            for (String column : parseResult.columns()) {
                if (!"*".equals(column) && column.contains(".")) {
                    warnings.add("Columna calificada detectada: " + column);
                }
            }
            return new SqlAnalysisResponse.SemanticReport(true, List.copyOf(parseResult.tables()), List.copyOf(parseResult.columns()), warnings);
        } catch (Exception ex) {
            warnings.add("No se pudo validar semantica en MySQL: " + ex.getMessage());
            return new SqlAnalysisResponse.SemanticReport(false, List.copyOf(parseResult.tables()), List.copyOf(parseResult.columns()), warnings);
        }
    }

    private boolean tableExists(DatabaseMetaData metaData, String table) throws SQLException {
        String schema = null;
        String tableName = table;
        if (table.contains(".")) {
            String[] parts = table.split("\\.", 2);
            schema = parts[0];
            tableName = parts[1];
        }
        try (ResultSet resultSet = metaData.getTables(null, schema, tableName, new String[]{"TABLE", "VIEW"})) {
            if (resultSet.next()) {
                return true;
            }
        }
        try (ResultSet resultSet = metaData.getTables(null, schema, tableName.toUpperCase(Locale.ROOT), new String[]{"TABLE", "VIEW"})) {
            return resultSet.next();
        }
    }

    private SqlAnalysisResponse.ExecutionReport executeIfPossible(String sql, String statementType,
                                                                  List<SqlAnalysisResponse.SqlDiagnostic> diagnostics) {
        boolean hasErrors = diagnostics.stream().anyMatch(d -> "ERROR".equals(d.severity()));
        if (hasErrors || jdbcTemplate == null || !READ_ONLY_STARTERS.contains(statementType)) {
            return new SqlAnalysisResponse.ExecutionReport(false, "La consulta no se ejecuto porque no es valida, no es de lectura o no hay base conectada.",
                    0, 0, List.of(), List.of());
        }

        long start = System.nanoTime();
        try {
            String runnableSql = sql.endsWith(";") ? sql.substring(0, sql.length() - 1) : sql;
            List<Map<String, Object>> rows = jdbcTemplate.query(connection -> {
                var statement = connection.prepareStatement(runnableSql);
                statement.setMaxRows(maxRows);
                return statement;
            }, resultSet -> {
                int columnCount = resultSet.getMetaData().getColumnCount();
                List<Map<String, Object>> result = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= columnCount; i++) {
                        row.put(resultSet.getMetaData().getColumnLabel(i), resultSet.getObject(i));
                    }
                    result.add(row);
                }
                return result;
            });
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.getFirst().keySet());
            return new SqlAnalysisResponse.ExecutionReport(true, "Query ejecutado correctamente.", elapsed, rows.size(), columns, rows);
        } catch (Exception ex) {
            diagnostics.add(diagnostic("EXECUTION", "ERROR", "Error al ejecutar en MySQL: " + ex.getMessage(), 1, 1));
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            return new SqlAnalysisResponse.ExecutionReport(false, ex.getMessage(), elapsed, 0, List.of(), List.of());
        }
    }

    private SqlAnalysisResponse.SqlDiagnostic diagnostic(String phase, String severity, String message, int line, int column) {
        return new SqlAnalysisResponse.SqlDiagnostic(phase, severity, message, line, column);
    }

    private String normalizeSql(String sql) {
        return WHITESPACE.matcher(sql.trim()).replaceAll(" ");
    }

    private long countStatements(String sql) {
        return Arrays.stream(sql.split(";"))
                .map(String::trim)
                .filter(fragment -> !fragment.isEmpty())
                .count();
    }

    private enum TokenType {
        KEYWORD, IDENTIFIER, NUMBER, STRING, OPERATOR, COMMA, DOT, SEMICOLON, LPAREN, RPAREN, ASTERISK, INVALID, EOF
    }

    private record Token(TokenType type, String lexeme, int line, int column) {
    }

    private record ParseResult(String statementType, SqlAnalysisResponse.AstNode ast, Set<String> tables, Set<String> columns) {
    }

    private static final class Lexer {
        private static final Set<String> KEYWORDS = Set.of(
                "SELECT", "FROM", "WHERE", "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "CROSS", "ON",
                "GROUP", "BY", "ORDER", "HAVING", "LIMIT", "OFFSET", "AS", "WITH", "UNION", "ALL",
                "DISTINCT", "INSERT", "UPDATE", "DELETE", "CREATE", "DROP", "ALTER", "SHOW", "DESCRIBE",
                "DESC", "EXPLAIN", "AND", "OR", "NOT", "IN", "IS", "NULL", "LIKE", "BETWEEN", "CASE",
                "WHEN", "THEN", "ELSE", "END"
        );

        private final String source;
        private int index;
        private int line = 1;
        private int column = 1;

        private Lexer(String source) {
            this.source = source;
        }

        private List<Token> tokenize() {
            List<Token> tokens = new ArrayList<>();
            while (!isAtEnd()) {
                char current = peek();
                if (Character.isWhitespace(current)) {
                    advance();
                } else if (current == '-' && peekNext() == '-') {
                    skipLineComment();
                } else if (Character.isLetter(current) || current == '_') {
                    tokens.add(readIdentifier());
                } else if (Character.isDigit(current)) {
                    tokens.add(readNumber());
                } else if (current == '\'' || current == '"') {
                    tokens.add(readString(current));
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
                case '=', '>', '<', '+', '-', '/', '%', '!' -> {
                    String op = Character.toString(current);
                    if (!isAtEnd() && "=<>".indexOf(peek()) >= 0) {
                        op += advance();
                    }
                    yield new Token(TokenType.OPERATOR, op, startLine, startColumn);
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
        private int index;
        private final Set<String> tables = new LinkedHashSet<>();
        private final Set<String> columns = new LinkedHashSet<>();

        private Parser(List<Token> tokens, List<SqlAnalysisResponse.SqlDiagnostic> diagnostics) {
            this.tokens = tokens;
            this.diagnostics = diagnostics;
        }

        private ParseResult parse() {
            if (tokens.isEmpty() || current().type == TokenType.EOF) {
                diagnostics.add(diagnostic("PARSER", "ERROR", "La consulta esta vacia.", 1, 1));
                return new ParseResult("UNKNOWN", node("Statement", "UNKNOWN", "", List.of()), tables, columns);
            }

            validateBalancedParentheses();
            validateSingleStatement();
            String statementType = current().lexeme.toUpperCase(Locale.ROOT);
            if (!READ_ONLY_STARTERS.contains(statementType)) {
                diagnostics.add(diagnostic("PARSER", "ERROR",
                        "Solo se permite ejecutar consultas de lectura: SELECT, WITH, SHOW, DESCRIBE o EXPLAIN.",
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
            long semicolonsBeforeEof = tokens.stream().filter(token -> token.type == TokenType.SEMICOLON).count();
            if (semicolonsBeforeEof > 1) {
                diagnostics.add(diagnostic("PARSER", "ERROR", "Solo se permite una sentencia por ejecucion.", 1, 1));
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
