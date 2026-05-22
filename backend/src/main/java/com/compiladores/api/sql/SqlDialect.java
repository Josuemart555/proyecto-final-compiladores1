package com.compiladores.api.sql;

import java.util.Locale;

public enum SqlDialect {
    MYSQL,
    SQLSERVER,
    MONGODB;

    public static SqlDialect parse(String value) {
        if (value == null) return MYSQL;
        try {
            return SqlDialect.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return MYSQL;
        }
    }
}
