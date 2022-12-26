package model;

import java.util.*;
import org.junit.*;
import org.junit.rules.*;
import static org.junit.Assert.*;

public class DatabaseConnectionPoolTest {

    @Rule
    public final Timeout globalTimeout = Timeout.seconds(120);

    // DatabaseConnectionPool sizes
    private final int INITIAL_POOL_SIZE = 10;
    private final int REDUCED_MAX_POOL_SIZE = 1;
    private final int MAX_POOL_SIZE = 20;

    @BeforeClass
    public static void setUpBeforeClass() {
        DatabaseConnectionPool.enableTesting();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        DatabaseConnectionPool.disableTesting();
    }

    @Before
    public void setUpBeforeTest() {
        DatabaseConnectionPool.restorePoolSize();
    }

    @After
    public void tearDownAfterTest() {
    }

    @Test
    public void testGetConnection() {
        DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
    }

    @Test
    public void testGetConnectionIncreasePoolSize() {
        assertEquals(INITIAL_POOL_SIZE, DatabaseConnectionPool.size());

        // Check that the connection pool size grows up to the maximum
        List<DatabaseConnection> dbconnList = new ArrayList<>();
        for (int i = 0; i < MAX_POOL_SIZE; i++) {
            dbconnList.add(DatabaseConnectionPool.getConnection());
        }
        assertEquals(MAX_POOL_SIZE, DatabaseConnectionPool.size());
    }

    @Test
    public void testGetConnectionWaitForAvailableConnection() throws InterruptedException {
        // Obtain the maximum number of connections
        List<DatabaseConnection> dbconnList = new ArrayList<>();
        for (int i = 0; i < MAX_POOL_SIZE; i++) {
            dbconnList.add(DatabaseConnectionPool.getConnection());
        }

        // Check that the next attempt to obtain a connection is blocked
        Thread thread = new Thread(() -> {
            DatabaseConnection dbconn = DatabaseConnectionPool.getConnection();
        });
        thread.start();
        Thread.sleep(500);
        assertTrue(thread.isAlive());

        // Release a connection
        DatabaseConnection dbconn = dbconnList.get(0);
        dbconn = DatabaseConnectionPool.releaseConnection(dbconn);

        Thread.sleep(500);

        // Check that the next attempt to obtain a connection has gone through
        assertFalse(thread.isAlive());
    }

    @Test
    public void testGetConnectionEfficientReuse() {
        assertEquals(INITIAL_POOL_SIZE, DatabaseConnectionPool.size());

        List<DatabaseConnection> dbconnList = new ArrayList<>();

        long startInitializeTime = System.currentTimeMillis();
        // Obtain the maximum number of connections
        for (int i = 0; i < MAX_POOL_SIZE; i++) {
            dbconnList.add(DatabaseConnectionPool.getConnection());
        }
        long finalInitializeTime = System.currentTimeMillis();
        assertEquals(MAX_POOL_SIZE, DatabaseConnectionPool.size());

        DatabaseConnectionPool.releaseAllConnections();

        long startReuseTime = System.currentTimeMillis();
        // Obtain the maximum number of connections
        for (int i = 0; i < MAX_POOL_SIZE; i++) {
            dbconnList.add(DatabaseConnectionPool.getConnection());
        }
        long finalReuseTime = System.currentTimeMillis();
        assertEquals(MAX_POOL_SIZE, DatabaseConnectionPool.size());

        long initializeDuration = finalInitializeTime - startInitializeTime;
        long reuseDuration = finalReuseTime - startReuseTime;

        System.out.println("Initialize duration: " + initializeDuration + "ms");
        System.out.println("Reuse duration: " + reuseDuration + "ms");

        // Check that the connection pool is reusing existing connections instead of generating new ones
        assert(reuseDuration < initializeDuration / MAX_POOL_SIZE);
    }

    @Test
    public void testReducedPoolSize() throws InterruptedException {
        DatabaseConnectionPool.reducePoolSize();

        // Check that the number of connections in the pool has been reduced to 1
        assertEquals(REDUCED_MAX_POOL_SIZE, DatabaseConnectionPool.size());

        // Obtain a connection
        DatabaseConnection dbconn1 = DatabaseConnectionPool.getConnection();

        // Check that the next attempt to obtain a connection is blocked
        Thread thread = new Thread(() -> {
            DatabaseConnection dbconn2 = DatabaseConnectionPool.getConnection();
        });
        thread.start();
        Thread.sleep(500);
        assertTrue(thread.isAlive());

        // Release a connection
        dbconn1 = DatabaseConnectionPool.releaseConnection(dbconn1);

        Thread.sleep(500);

        // Check that the next attempt to obtain a connection has gone through
        assertFalse(thread.isAlive());

        // Test that number of connections in the pool is still 1
        assertEquals(1, DatabaseConnectionPool.size());
    }

    @Test
    public void testRestorePoolSize() {
        DatabaseConnectionPool.reducePoolSize();

        // Check that the number of connections in the pool has been reduced to 1
        assertEquals(REDUCED_MAX_POOL_SIZE, DatabaseConnectionPool.size());

        DatabaseConnectionPool.restorePoolSize();

        // Test that we can obtain MAX_POOL_SIZE number of connections
        List<DatabaseConnection> dbconnList = new ArrayList<>();
        for (int i = 0; i < MAX_POOL_SIZE; i++) {
            dbconnList.add(DatabaseConnectionPool.getConnection());
        }
    }
}