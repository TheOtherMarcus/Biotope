import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Properties;

import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import javax.xml.bind.DatatypeConverter;

import java.io.IOException;
import java.io.OutputStream;

public final class SensorLog {

    public static String sensorsroot = "";

    public static void log(String sensor, String legend, String value, String isotime) throws IOException
    {
	String url = "jdbc:sqlite:" + sensorsroot + "/" + sensor + ".sqlite";
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
	    if (isotime != null) {
		stmt.setDate(1, new java.sql.Date(DatatypeConverter.parseDateTime(isotime).getTime().getTime()));
	    }
	    else {
		stmt.setDate(1, new java.sql.Date((new Date()).getTime()));
	    }
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

    public static long getTime(String sensor) throws IOException
    {
	long time = 0;
	String url = "jdbc:sqlite:" + sensorsroot + "/" + sensor + ".sqlite";
	System.out.println("getTime: " + url);
	Connection con = null;
	try {
	    PreparedStatement stmt;
	    ResultSet rs;
	    boolean schema;
	    String q;
	    con = DriverManager.getConnection(url);

	    q = "select max(time) from data";
	    stmt = con.prepareStatement(q);
	    rs = stmt.executeQuery();
	    if (rs.next()) {
		time = rs.getLong(1);
	    }
	    rs.close();
	    stmt.close();
	    
	} catch (SQLException e) {
	    throw new IOException(e);
	}
	finally {
	    try { con.close(); } catch (Exception e) { throw new IOException(e); }
	}
	return time;
    }
    
    public static void pushLegend(String sensor, OutputStream out) throws IOException
    {
	String url = "jdbc:sqlite:" + sensorsroot + "/" + sensor + ".sqlite";
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
		out.write(("event: legend\n").getBytes("UTF-8"));
		out.write(("data: time" + rs.getString(1) + "\n\n").getBytes("UTF-8"));
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

    private static String addToAcc(String data_acc, String data)
    {
	if (data_acc == null) {
	    return data;
	}
	else {
	    String result  = "";
	    String[] acc_tokens = data_acc.split(" ");
	    String[] data_tokens = data.split(" ");
	    int i;
	    for (i = 0; i < acc_tokens.length-1; i++) {
		result += Float.parseFloat(acc_tokens[i]) + Float.parseFloat(data_tokens[i]) + " ";
	    }
	    result += Float.parseFloat(acc_tokens[i]) + Float.parseFloat(data_tokens[i]);
	    return result;
	}
    }

    private static String divideAcc(String data_acc, int divisor)
    {
	String result  = "";
	String[] acc_tokens = data_acc.split(" ");
	int i;
	for (i = 0; i < acc_tokens.length-1; i++) {
	    result += Float.parseFloat(acc_tokens[i]) / divisor + " ";
	}
	result += Float.parseFloat(acc_tokens[i]) / divisor;
	return result;
    }

    public static Date pushLog(String sensor, Date last, Counter counter, int bin, OutputStream out) throws IOException
    {
	String url = "jdbc:sqlite:" + sensorsroot + "/" + sensor + ".sqlite";
	System.out.println("pushLog: " + url + " " + last.getTime());
	Connection con = null;
	Date time = last;
        Counter bining = new Counter(bin);
	try {
	    PreparedStatement stmt;
	    ResultSet rs;
	    boolean schema;
	    String q;
	    con = DriverManager.getConnection(url);

	    q = "select time, value from data where time > ? order by time";
	    if (counter != null) q += " limit " + counter.count;
	    stmt = con.prepareStatement(q);
	    stmt.setDate(1, new java.sql.Date(last.getTime()));
	    rs = stmt.executeQuery();
	    String data_acc = null;
	    while (rs.next()) {
		String data = rs.getString(2);
		data_acc = addToAcc(data_acc, data);
		if (bining.down()) {
		    bining = new Counter(bin);
		    Date now = new Date(rs.getDate(1).getTime());
		    TimeZone tz = TimeZone.getTimeZone("UTC");
		    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'");
		    df.setTimeZone(tz);
		    out.write(("event: datapoint\n").getBytes("UTF-8"));
		    out.write(("data: " + df.format(now) + " " + divideAcc(data_acc, bin) + "\n\n").getBytes("UTF-8"));
		    // Postpone stepping the time to here if rs.getString(2) throws an exception
		    time = now;
		    data_acc = null;
		    if (counter != null && counter.down()) {
			break;
		    }
		}
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
