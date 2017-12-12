package com.github.nija123098.tipbot;

import com.mysql.jdbc.jdbc2.optional.MysqlConnectionPoolDataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import sx.blah.discord.handle.obj.IDiscordObject;
import com.github.nija123098.tipbot.utility.Config;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

public class Database {
    //TODO: this should not be in a database, only the blockchain contains the truth, the dash client is all we need to query this!
    public static final String BALANCES_TABLE = "balances";
    //TODO: again, this is only needed for non registered users, otherwise we could send tips directly, this is just cumbersome
    public static final String RECEIVED_TABLE = "received";
    //TODO: not using db properly (user should have this as a property)
    public static final String RECEIVING_ADDRESSES_TABLE = "receiving_addresses";
    //TODO: unnecessary imo
    public static final String PREFERRED_CURRENCY = "preferred_currency";
    //TODO: not sure why this has to be in a tip bot
    public static final String ANNOUNCEMENT_CHANNEL = "announcement_channel";
    private static final Connection CONNECTION;
    private static final QueryRunner RUNNER;
    static {
        try {
            //TODO: This is called several times
            Config.setUp();
        } catch (IOException e) {
            e.printStackTrace();
        }
        Connection c;
        try {
            MysqlConnectionPoolDataSource dataSource = new MysqlConnectionPoolDataSource();
            dataSource.setUser(Config.DB_USER);
            dataSource.setPassword(Config.DB_PASS);
            dataSource.setServerName(Config.DB_HOST);
            dataSource.setPort(3306);
            dataSource.setDatabaseName(Config.DB_NAME);
            dataSource.setZeroDateTimeBehavior("convertToNull");
            dataSource.setUseUnicode(true);
            c = dataSource.getConnection();
        } catch (SQLException e) {
            //TODO: Code issue: Avoid throwing raw exception types.
            throw new RuntimeException("Could not init database connection!", e);
        }
        CONNECTION = c;
        HikariConfig config = new HikariConfig();
        config.setMaximumPoolSize(20);
        config.setJdbcUrl("jdbc:mariadb://" + Config.DB_HOST + ":" + Config.DB_PORT + "/" + Config.DB_NAME);
        config.setDriverClassName("org.mariadb.jdbc.Driver");
        config.setUsername(Config.DB_USER);
        config.setPassword(Config.DB_PASS);
        RUNNER = new QueryRunner(new HikariDataSource(config));
        query("SET NAMES utf8mb4");
    }

    // DB stuff
    //TODO: messy way of doing sql, better would be to use ParameterizedQuery
    private static <E> E select(String sql, ResultSetHandler<E> handler) {
        try{return RUNNER.query(sql, handler);
        } catch (SQLException e) {
            //TODO: all this is a very bad way of error handling
            //TODO: Code issue: Avoid throwing raw exception types.
            throw new RuntimeException("Could not select: ERROR: " + e.getErrorCode() + " " + sql, e);
        }
    }

    private static void query(String sql) {
        try{RUNNER.update(sql);
        } catch (SQLException e) {
            //TODO: Code issue: Avoid throwing raw exception types.
            throw new RuntimeException("Could not query: ERROR: " + e.getErrorCode() + " " + sql, e);
        }
    }

    private static void insert(String sql) {// set
        try{RUNNER.update(sql);
        } catch (SQLException e) {
            //TODO: Code issue: Avoid throwing raw exception types.
            throw new RuntimeException("Could not insert: ERROR: " + e.getErrorCode() + " " + sql, e);
        }
    }

    private static String quote(String id){
        return Character.isDigit(id.charAt(0)) ? id : "'" + id + "'";
    }

    //TODO: Code issue: Fields should be declared at the top of the class, before any method declarations, constructors, initializers or inner classes.
    private static final Set<String> EXISTING_TABLES = new HashSet<>();
    //TODO: each db table is just a key value pair, using mysql for that is kinda wasteful and not really necessary
    private static void ensureTableExistence(String table){
        if (!EXISTING_TABLES.add(table)) return;
        try (ResultSet rs = CONNECTION.getMetaData().getTables(null, null, table, null)) {
            while (rs.next()) {
                String tName = rs.getString("TABLE_NAME");
                if (tName != null && tName.equals(table)) return;
            }// make
            Database.query("CREATE TABLE `" + table + "` (id TINYTEXT, value TINYTEXT)");
        } catch (SQLException e) {
            //TODO: Code issue: Avoid throwing raw exception types.
            throw new RuntimeException("Could not ensure table existence", e);
        }
    }

    // HELPERS
    public static void setValue(String table, IDiscordObject user, String value) throws InterruptedException {
        ensureTableExistence(table);
        Database.select("SELECT * FROM " + table + " WHERE id = " + Database.quote(user.getStringID()), set -> {
            try{set.next();
                if (set.getString(2).equals(value)) return null;
                query("UPDATE " + table + " SET value = " + value + " WHERE id = " + user.getStringID());
            } catch (SQLException e) {
                Database.insert("INSERT INTO " + table + " (`id`, `value`) VALUES ('" + user.getStringID() + "','" + value + "');");
            }
            //TODO: bad way of error handling!
            return null;
        });
    }

    public static void resetValue(String table, IDiscordObject object){
        ensureTableExistence(table);
        Database.query("DELETE FROM " + table + " WHERE id = " + quote(object.getStringID()));
    }

    //TODO: what is with the typo defaul?
    public static String getValue(String table, IDiscordObject user, String defaul){
        ensureTableExistence(table);
        return Database.select("SELECT * FROM " + table + " WHERE id = " + Database.quote(user.getStringID()), set -> {
            try{set.next();
                return set.getString(2);
            } catch (SQLException e) {
                //TODO: bad way of error handling, why is this here?
                if (!e.getMessage().equals("Current position is after the last row")) throw new RuntimeException("Error while getting value", e);
                return defaul;
            }
        });
    }
}
