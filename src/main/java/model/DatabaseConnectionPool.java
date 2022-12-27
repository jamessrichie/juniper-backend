package model;

import org.springframework.cglib.core.Block;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * DatabaseConnectionPool represents a pool of DatabaseConnections
 * Initialization of the pool is slow (about 1 second per connection)
 * but offers significant parallelism and caching improvements
 */
public class DatabaseConnectionPool {

    private static Boolean testingEnabled = false;

    private static final int INITIAL_POOL_SIZE = 5;
    private static final int REDUCED_MAX_POOL_SIZE = 1;
    private static final int MAX_POOL_SIZE = 20;

    private static int maxPoolSize = MAX_POOL_SIZE;

    private static final Lock lock;
    private static final BlockingQueue<DatabaseConnection> idleConnections;
    private static final BlockingQueue<DatabaseConnection> activeConnections;


    static {
        try {
            lock = new ReentrantLock();

            idleConnections = new LinkedBlockingQueue<>();
            for (int i = 0; i < INITIAL_POOL_SIZE; i++) {
                idleConnections.put(new DatabaseConnection());
            }
            activeConnections = new LinkedBlockingQueue<>();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gets a connection from the connection pool
     *
     * @return a DatabaseConnection object
     */
    public static DatabaseConnection getConnection() {
        lock.lock();

        try {
            // if there are no idle connections and pool size is below max, then create new connection
            if (idleConnections.size() == 0 && size() < maxPoolSize) {
                try {
                    DatabaseConnection dbconn = new DatabaseConnection();
                    activeConnections.put(dbconn);
                    return dbconn;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                // otherwise wait for idle connection
            } else {
                DatabaseConnection dbconn = idleConnections.take();
                activeConnections.put(dbconn);

                return dbconn;
            }
        } catch (InterruptedException e) {
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
     * Enables the use of testing features
     */
    public static void enableTesting() {
        if (!testingEnabled) {
            testingEnabled = true;
        }
    }

    /**
     * Disables the use of testing features and restores the pool size
     */
    public static void disableTesting() {
        if (testingEnabled) {
            if (maxPoolSize == REDUCED_MAX_POOL_SIZE) {
                restorePoolSize();
            }
            testingEnabled = false;
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
     * Closes all connections in the pool. These connections can no longer be used
     */
    private static void closeAllConnections() {
        if (testingEnabled) {
            closeConnections(idleConnections);
            closeConnections(activeConnections);
        } else {
            throw new IllegalStateException("Enable testing to use closeAllConnections()");
        }
    }

    /**
     * Reduces the number of connections in the pool to 1.
     * This forces all methods to utilize the same connection.
     * Very expensive method call and only meant for JUnit tests
     */
    public static void reducePoolSize() {
        if (testingEnabled) {
            closeAllConnections();
            idleConnections.clear();
            activeConnections.clear();

            maxPoolSize = REDUCED_MAX_POOL_SIZE;

            try {
                idleConnections.put(new DatabaseConnection(true));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalStateException("Enable testing to use reducePoolSize()");
        }
    }

    /**
     * Reverts the connection pool to its initial size with new connections.
     * Very expensive method call and only meant for JUnit tests
     */
    public static void restorePoolSize() {
        if (testingEnabled) {
            closeAllConnections();
            idleConnections.clear();
            activeConnections.clear();

            maxPoolSize = MAX_POOL_SIZE;

            try {
                for (int i = 0; i < INITIAL_POOL_SIZE; i++) {
                    idleConnections.put(new DatabaseConnection());
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new IllegalStateException("Enable testing to use restorePoolSize()");
        }
    }

    /**
     * Returns the total number of active and idle connections in the connection pool
     *
     * @return total number of active and idle connections in the connection pool
     */
    public static int size() {
        return idleConnections.size() + activeConnections.size();
    }
}
