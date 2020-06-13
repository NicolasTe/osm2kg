package de.l3s.osmlinks;

import org.postgresql.jdbc3.Jdbc3PoolingDataSource;

import java.sql.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This class represnts the Postgres database and handles the connections.
 */
public class PostGreDB {

	private static AtomicInteger sourceNumber = new AtomicInteger(0);
	private Jdbc3PoolingDataSource source = new Jdbc3PoolingDataSource();

	/**
	 * Constructor
	 * @param url Url of the database
	 * @param dbName Name of database
	 * @param userName Name of database user
	 * @param password Password for the database user
	 * @param maxConnections Maximal number of simultaneous connections to the database
	 */
	public PostGreDB(String url, String dbName, String userName, String password, int maxConnections) {
		this(url, dbName, userName, password);
		source.setMaxConnections(maxConnections);
	}

	/**
	 * Constructor with one simultaneous connection to the database
	 * @param url Url of the database
	 * @param dbName Name of database
	 * @param userName Name of database user
	 * @param password Password for the database user
	 */
	public PostGreDB(String url, String dbName, String userName, String password) {
			try {
	        Class.forName("org.postgresql.Driver");


				source.setDataSourceName("kgsource_"+(sourceNumber.getAndIncrement()));
				source.setServerName(url);
				source.setDatabaseName(dbName);
				source.setUser(userName);
				source.setPassword(password);
				source.setMaxConnections(1);
		} catch ( Exception e ) {
	         System.err.println( e.getClass().getName()+": "+ e.getMessage() );
	         System.exit(0);
	      }
	}

	/**
	 * Returns a connection to the database
	 * @return The connection
	 */
	public Connection getConnection() {
		Connection con = null;
		try {
			con = source.getConnection();
			// use connection
		} catch (SQLException e) {
			e.printStackTrace();
			System.exit(2);
		}
		return con;
	}

	/**
	 * Closes all connections.
	 */
	public void close() {
		source.close();
	}

}
