package model;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.time.*;
import java.time.temporal.*;
import java.util.concurrent.TimeUnit;

import org.junit.*;
import org.junit.rules.*;
import static org.junit.Assert.*;

import model.*;

public class DatabaseConnectionPoolTest {

    @Rule
    public final Timeout globalTimeout = Timeout.seconds(120);

    @Before
    public void setUpBeforeTest() {
    }

    @After
    public void tearDownAfterTest() {
        DatabaseConnectionPool.releaseAllConnections();
    }

    @Test
    public void testGetConnection() {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
    }

    @Test
    public void testGetConnectionIncreasePoolSize() {
        List<DatabaseConnection> dbconnList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            dbconnList.add(DatabaseConnectionPool.getConnection());
        }
    }

    @Test
    public void testGetConnectionMaxPoolSizeWaitForAvailableConnection() throws InterruptedException {
        List<DatabaseConnection> dbconnList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            dbconnList.add(DatabaseConnectionPool.getConnection());
        }

        Thread thread = new Thread(() -> {
            DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        });

        // Check that the 21st attempt to obtain a DatabaseConnection is blocked
        thread.start();
        Thread.sleep(500);
        assertTrue(thread.isAlive());

        // Release a connection
        DatabaseConnection dbconn = dbconnList.get(0);
        DatabaseConnectionPool.releaseConnection(dbconn);

        Thread.sleep(500);

        // Check that the 21st attempt to obtain a DatabaseConnection has gone through
        assertFalse(thread.isAlive());
    }

    @Test
    public void testEnableTestingReducedPoolSize() {

    }

    @Test
    public void testDisableTestingNormalPoolSize() {

    }
}