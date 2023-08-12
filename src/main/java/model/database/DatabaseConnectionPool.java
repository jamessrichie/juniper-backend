package model.database;

import java.sql.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * DatabaseConnectionPool represents a pool of DatabaseConnections
 * Initialization of the pool is slow (about 1 second per connection)
 * but offers significant parallelism and caching improvements
 */
public class DatabaseConnectionPool {

    private static Boolean testingEnabled = false;

    public static final int INITIAL_POOL_SIZE = 5;
    public static final int REDUCED_MAX_POOL_SIZE = 1;
    public static final int MAX_POOL_SIZE = 20;

    private static int maxPoolSize = MAX_POOL_SIZE;

    private static final Lock lock;
    private static final BlockingQueue<DatabaseConnection> idleConnections;
    private static final BlockingQueue<DatabaseConnection> activeConnections;

    // getConnection timeout
    private static final int TIMEOUT_VALUE = 30;
    private static final TimeUnit TIMEOUT_UNIT = TimeUnit.SECONDS;

    static {
        try {
            lock = new ReentrantLock();
            idleConnections = new LinkedBlockingQueue<>();
            activeConnections = new LinkedBlockingQueue<>();

            initializePool(INITIAL_POOL_SIZE);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets a connection from the connection pool
     * TODO: optimize check for broken pipe / closed connection such that
     *       it only checks if connection hasn't been used in a while
     *
     * @return a DatabaseConnection object
     */
    public static DatabaseConnection getConnection() {
        lock.lock();

        try {
            if (idleConnections.size() == 0 && size() < maxPoolSize) {
                // If there are no idle connections and pool size is below max, then create new connection
                try {
                    DatabaseConnection dbconn = new DatabaseConnection();
                    activeConnections.put(dbconn);
                    return dbconn;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else {
                // Otherwise wait for idle connection
                DatabaseConnection dbconn = idleConnections.poll(TIMEOUT_VALUE, TIMEOUT_UNIT);
                if (dbconn == null) {
                    throw new RuntimeException("DatabaseConnection deadlock");
                }

                // If idle connection has closed, then create new connection
                try {
                    dbconn.getTransactionCount();
                } catch (Exception e) {
                    dbconn = new DatabaseConnection();
                }
                activeConnections.put(dbconn);
                return dbconn;
            }
        } catch (Exception e) {
            throw new RuntimeException(e);

        } finally {
            lock.unlock();
        }
    }

    /**
     * Releases a connection back to the connection pool
     *
     * @param dbconn DatabaseConnection to be released
     * @return null. it is recommended to use this return value to overwrite the connection
     */
    public static DatabaseConnection releaseConnection(DatabaseConnection dbconn) {
        try {
            if (activeConnections.remove(dbconn)) {
                idleConnections.put(dbconn);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return null;
    }

    /**
     * Returns the total number of active and idle connections in the connection pool
     *
     * @return total number of active and idle connections in the connection pool
     */
    public static int size() {
        return idleConnections.size() + activeConnections.size();
    }

    /**
     * Enables the use of testing features
     */
    public static void enableTesting() {
        if (!testingEnabled) {
            testingEnabled = true;
            initializePool(INITIAL_POOL_SIZE);
        }
    }

    /**
     * Disables the use of testing features and restores the pool size
     */
    public static void disableTesting() {
        if (testingEnabled) {
            testingEnabled = false;
            initializePool(INITIAL_POOL_SIZE);
        }
    }

    /**
     * Releases all connections back to the connection pool
     */
    public static void releaseAllConnections() {
        if (testingEnabled) {
            while (activeConnections.size() > 0) {
                releaseConnection(activeConnections.peek());
            }
        } else {
            throw new IllegalStateException("Enable testing to use releaseAllConnections()");
        }
    }

    /**
     * Closes all connections in the pool. These connections can no longer be used
     */
    public static void closeAllConnections() {
        closeConnections(idleConnections);
        closeConnections(activeConnections);
    }

    /**
     * Reduces the number of connections in the pool to 1 and sets them in accordance with the testingEnabled flag.
     * This forces all methods to utilize the same connection.
     * Very expensive method call and only meant for JUnit tests
     */
    public static void reducePoolSize() {
        if (testingEnabled) {
            maxPoolSize = REDUCED_MAX_POOL_SIZE;
            initializePool(REDUCED_MAX_POOL_SIZE);

        } else {
            throw new IllegalStateException("Enable testing to use reducePoolSize()");
        }
    }

    /**
     * Reverts the connection pool to its initial size in accordance with the testingEnabled flag.
     * Very expensive method call and only meant for JUnit tests
     */
    public static void restorePoolSize() {
        if (testingEnabled) {
            maxPoolSize = MAX_POOL_SIZE;
            initializePool(INITIAL_POOL_SIZE);

        } else {
            throw new IllegalStateException("Enable testing to use restorePoolSize()");
        }
    }

    /**
     * Closes all connections in the list
     *
     * @param dbconns list containing connections to be closed
     */
    private static void closeConnections(BlockingQueue<DatabaseConnection> dbconns) {
        for (DatabaseConnection dbconn : dbconns) {
            try {
                dbconn.closeConnection();
            } catch (SQLException ignored) {}
        }

    }

    /**
     * Initializes the connections in the pool in accordance with the testingEnabled flag
     *
     * @param size number of connections to initialize
     */
    private static void initializePool(int size) {
        try {
            closeAllConnections();
            idleConnections.clear();
            activeConnections.clear();

            for (int i = 0; i < size; i++) {
                idleConnections.put(new DatabaseConnection(testingEnabled));
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
