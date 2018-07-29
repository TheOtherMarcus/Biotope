import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Properties;

import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;

import java.io.IOException;
import java.io.OutputStream;

public final class SensorLog {

    public static void log(String sensorpath, String legend, String value) throws IOException
    {
	String url = "jdbc:sqlite:" + sensorpath + ".sqlite";
	System.out.println("log: " + url);
	Connection con = null;
	try {
	    PreparedStatement stmt;
	    ResultSet rs;
	    String q;
	    boolean correctLegend;
	    final Properties p = new Properties();
	    p.setProperty("journal_mode", "WAL");
	    con = DriverManager.getConnection(url, p);

	    q = "create table if not exists legend (legend text)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    q = "select legend from legend where legend = ?";
	    stmt = con.prepareStatement(q);
	    stmt.setString(1, legend);
	    rs = stmt.executeQuery();
	    correctLegend = rs.next();
	    rs.close();
	    stmt.close();
	    if (!correctLegend) {
		q = "delete from legend";
		stmt = con.prepareStatement(q);
		stmt.executeUpdate();
		stmt.close();
		q = "insert into legend (legend) values (?)";
		stmt = con.prepareStatement(q);
		stmt.setString(1, legend);
		stmt.executeUpdate();
		stmt.close();
	    }
	    
	    q = "create table if not exists data (time integer, value text)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    q = "create index if not exists data_time on data (time)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    stmt = con.prepareStatement("insert into data (time, value) values (?, ?)");
	    stmt.setDate(1, new java.sql.Date((new Date()).getTime()));
	    stmt.setString(2, value);
	    stmt.executeUpdate();
	    stmt.close();
	    
	} catch (SQLException e) {
	    throw new IOException(e);
	}
	finally {
	    try { con.close(); } catch (Exception e) { throw new IOException(e); }
	}	
    }

    public static void pushLegend(String sensorpath, OutputStream out) throws IOException
    {
	String url = "jdbc:sqlite:" + sensorpath + ".sqlite";
	System.out.println("pushLegend: " + url);
	Connection con = null;
	try {
	    PreparedStatement stmt;
	    ResultSet rs;
	    boolean schema;
	    String q;
	    con = DriverManager.getConnection(url);

	    q = "select legend from legend";
	    stmt = con.prepareStatement(q);
	    rs = stmt.executeQuery();
	    if (rs.next()) {
		out.write((rs.getString(1) + "\n").getBytes("UTF-8"));
		out.flush();
	    }
	    rs.close();
	    stmt.close();
	    
	} catch (SQLException e) {
	    throw new IOException(e);
	}
	finally {
	    try { con.close(); } catch (Exception e) { throw new IOException(e); }
	}
    }

    public static Date pushLog(String sensorpath, Date last, OutputStream out) throws IOException
    {
	String url = "jdbc:sqlite:" + sensorpath + ".sqlite";
	System.out.println("pushLog: " + url + " " + last.getTime());
	Connection con = null;
	Date time = last;
	try {
	    PreparedStatement stmt;
	    ResultSet rs;
	    boolean schema;
	    String q;
	    con = DriverManager.getConnection(url);

	    q = "select time, value from data where time > ? order by time";
	    stmt = con.prepareStatement(q);
	    stmt.setDate(1, new java.sql.Date(last.getTime()));
	    rs = stmt.executeQuery();
	    while (rs.next()) {
		Date now = new Date(rs.getDate(1).getTime());
		TimeZone tz = TimeZone.getTimeZone("UTC");
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'");
		df.setTimeZone(tz);
		out.write((df.format(now) + " " + rs.getString(2) + "\n").getBytes("UTF-8"));
		// Postpone stepping the time to here if rs.getString(2) throws an exception
		time = now;
	    }
	    rs.close();
	    stmt.close();
	    
	} catch (SQLException e) {
	    // Just log and return where we are in time in the log so that
	    // the next call can pick up where this failed.
	    e.printStackTrace();
	}
	finally {
	    try { con.close(); } catch (SQLException e) { e.printStackTrace(); }
	}
	out.flush();
	return time;
    }
}
