package model;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;

/**
 * DatabaseConnectionPool represents a pool of DatabaseConnections
 * Initialization of the pool is slow (about 1 second per connection)
 * but offers significant parallelism and caching improvements
 */
public class DatabaseConnectionPool {

    private static final int INITIAL_POOL_SIZE = 10;
    private static int MAX_POOL_SIZE = 20;

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
            // if there are available connections, then get them
            if (idleConnections.size() > 0) {
                DatabaseConnection dbconn = idleConnections.take();
                activeConnections.put(dbconn);

                return dbconn;

            // if there are no available connections but pool size is below max, then create new connections
            } else if (getSize() < MAX_POOL_SIZE) {
                try {
                    DatabaseConnection dbconn = new DatabaseConnection();
                    activeConnections.put(dbconn);
                    return dbconn;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

            // if there are no available connections and pool size is below max, then wait for available connections
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
     */
    public static void releaseConnection(DatabaseConnection dbconn) {
        try {
            if (activeConnections.remove(dbconn)) {
                idleConnections.put(dbconn);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Releases all connections back to the connection pool
     *
     */
    public static void releaseAllConnections() {
        while (activeConnections.size() > 0) {
            releaseConnection(activeConnections.peek());
        }
    }

    /**
     * Configures the connection pool for testing and enables the use of testing features.
     * During testing, the number of connections in the pool is reduced to 1.
     * This forces all methods to utilize the same connection
     */
    public static void enableTesting() {
        releaseAllConnections();

        MAX_POOL_SIZE = 1;
        idleConnections.clear();
        activeConnections.clear();
        try {
            idleConnections.put(new DatabaseConnection(true));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reverts the connection pool to its original state and disables the use of testing features
     */
    public static void disableTesting() {
        releaseAllConnections();

        MAX_POOL_SIZE = 20;
        idleConnections.clear();
        activeConnections.clear();

        try {
            for (int i = 0; i < INITIAL_POOL_SIZE; i++) {
                idleConnections.put(new DatabaseConnection());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Returns the total number of active and idle connections in the connection pool
     *
     * @return total number of active and idle connections in the connection pool
     */
    public static int getSize() {
        return idleConnections.size() + activeConnections.size();
    }
}
