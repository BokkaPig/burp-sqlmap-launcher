package uk.co.periculo.burpsqlmap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Database error strings worth noticing in a response. A hit here is the
 * strongest signal available without sending a single extra request: the
 * application is already leaking SQL errors back to the client.
 */
public final class SqlErrors {

    private static final Map<String, Pattern> SIGNATURES = new LinkedHashMap<>();

    static {
        add("MySQL", "SQL syntax.{0,40}MySQL|Warning.{0,20}\\bmysqli?_|MySqlException|"
                + "valid MySQL result|check the manual that (corresponds|fits) to your MySQL server version|"
                + "Unknown column '[^']+' in 'field list'|MySqlClient\\.");
        add("PostgreSQL", "PostgreSQL.{0,20}ERROR|Warning.{0,20}\\bpg_|valid PostgreSQL result|"
                + "Npgsql\\.|PSQLException|ERROR:\\s+syntax error at or near|"
                + "org\\.postgresql\\.util\\.PSQLException");
        add("Microsoft SQL Server", "Driver.{0,20}SQL[\\-\\_\\ ]*Server|OLE DB.{0,40}SQL Server|"
                + "\\bSQL Server[^&<\"]{0,40}Driver|Warning.{0,20}\\bmssql_|"
                + "Unclosed quotation mark after the character string|"
                + "System\\.Data\\.SqlClient\\.SqlException|Incorrect syntax near|"
                + "Microsoft SQL Native Client error");
        add("Oracle", "\\bORA-\\d{4,5}|Oracle error|Oracle.{0,40}Driver|"
                + "Warning.{0,20}\\boci_|quoted string not properly terminated|"
                + "OracleException");
        add("SQLite", "SQLite/JDBCDriver|SQLite\\.Exception|System\\.Data\\.SQLite\\.SQLiteException|"
                + "Warning.{0,20}\\bsqlite_|\\[SQLITE_ERROR\\]|sqlite3\\.OperationalError|"
                + "SQLite error \\d+:|unrecognized token:");
        add("IBM DB2", "CLI Driver.{0,20}DB2|DB2 SQL error|\\bdb2_\\w+\\(|SQLCODE[=:]? ?-?\\d+");
        add("Sybase", "Warning.{0,20}\\bsybase_|Sybase message|Sybase.{0,40}Server message");
        add("Generic SQL", "SQLSTATE\\[|java\\.sql\\.SQLException|"
                + "org\\.hibernate\\.exception|com\\.microsoft\\.sqlserver\\.jdbc|"
                + "ODBC.{0,40}Driver|Dynamic SQL Error|SQL command not properly ended");
    }

    private static void add(String dbms, String regex) {
        SIGNATURES.put(dbms, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    private SqlErrors() {
    }

    /** Returns the DBMS whose error signature appears in the body, or null. */
    public static String detect(String body) {
        if (body == null || body.isEmpty()) return null;
        for (Map.Entry<String, Pattern> entry : SIGNATURES.entrySet()) {
            if (entry.getValue().matcher(body).find()) return entry.getKey();
        }
        return null;
    }
}
