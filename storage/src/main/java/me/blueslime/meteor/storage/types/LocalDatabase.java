package me.blueslime.meteor.storage.types;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.*;

@SuppressWarnings("unused")
public class LocalDatabase extends SQLDatabase {

    private final String name;
    private final File folder;

    private Connection connection = null;

    /**
     * Creates a new SQL Database.
     *
     * @param databaseName The name of the MySQL database.
     */
    public LocalDatabase(@NotNull String databaseName, @NotNull File folder) {
        super("localhost", databaseName, "root", "", 3306);
        this.name = databaseName + (databaseName.endsWith(".db") ? "" : ".db");
        this.folder = folder;
    }

    @Override
    public void connect() {
        File databaseFile = new File(folder, name);
        if (!databaseFile.exists()) {
            try {
                boolean folderCreation = folder.mkdirs();
                boolean databaseFileCreation = databaseFile.createNewFile();
                if (folderCreation && databaseFileCreation) {
                    getLogger().info("Local database file: '" + name + "' has been created for first time.");
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to create SQLite database file: " + e);
            }
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        } catch (Exception e) {
            throw new RuntimeException("Failed to connect to SQLite database: " + e);
        }
    }

    @Override
    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to close SQLite database connection: " + e);
        }
    }

    @Override
    protected void ensureConnected() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error checking DB connection", e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return connection;
    }
}
