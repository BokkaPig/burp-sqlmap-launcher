package uk.co.periculo.burpsqlmap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DBMS error signatures")
class SqlErrorsTest {

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            "You have an error in your SQL syntax; check the manual that corresponds to your MySQL server version | MySQL",
            "Warning: pg_query(): Query failed: ERROR: syntax error at or near | PostgreSQL",
            "Unclosed quotation mark after the character string ''. | Microsoft SQL Server",
            "ORA-01756: quoted string not properly terminated | Oracle",
            "sqlite3.OperationalError: near \"'\": syntax error | SQLite",
            "SQL0104N An unexpected token. DB2 SQL error: SQLCODE=-104 | IBM DB2",
            "SQLSTATE[42000]: Syntax error or access violation | Generic SQL",
    })
    void detectsRealErrors(String body, String expected) {
        assertEquals(expected, SqlErrors.detect(body));
    }

    @ParameterizedTest(name = "no false positive: {0}")
    @CsvSource(delimiter = '|', value = {
            "<html><body><h1>Order summary</h1><p>Select a database plan</p></body></html>",
            "Please select your preferred Oracle of Delphi tour date",
            "Our MySQL hosting plans start at 5 GBP per month",
            "{\"error\":\"invalid credentials\"}",
    })
    void ignoresBenignText(String body) {
        assertNull(SqlErrors.detect(body));
    }

    @Test
    @DisplayName("empty and null bodies are safe")
    void handlesEmpty() {
        assertNull(SqlErrors.detect(""));
        assertNull(SqlErrors.detect(null));
    }
}
