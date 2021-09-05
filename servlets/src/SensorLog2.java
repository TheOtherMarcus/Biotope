import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import java.util.Properties;

import java.util.UUID;

import java.util.List;
import java.util.LinkedList;

import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import javax.xml.bind.DatatypeConverter;

import java.io.IOException;
import java.io.OutputStream;

public final class SensorLog2 {

    public static String sensorsroot = "";
    public static String uid = "";

    public static synchronized String getUID(Connection con)  throws SQLException {
	PreparedStatement stmt;
	ResultSet rs;
	String q;

	if (uid == null || uid.equals("")) {
	    q = "select max(m) from (" +
		"select max(cast(s.a as integer)) as m from sensorEE s UNION " +
		"select max(cast(s.b as integer)) as m from sensorEE s UNION " +
		"select max(cast(q.a as integer)) as m from quantityEE q UNION " +
		"select max(cast(q.b as integer)) as m from quantityEE q )";
	    stmt = con.prepareStatement(q);
	    rs = stmt.executeQuery();
	    if (rs.next()) {
		uid = rs.getString(1);
	    }
	    rs.close();
	    stmt.close();
	    if (uid == null || uid.equals("")) {
		uid = "0";
	    }
	}
	uid = "" + (Integer.parseInt(uid) + 1);
	return uid;
    }
    
    public static void log(String sensor, String legend, String value, String isotime) throws IOException
    {
	String url = "jdbc:sqlite:" + sensorsroot + "/sensors.sqlite";
	System.out.println("log: " + sensor);
	Connection con = null;
	try {
	    PreparedStatement stmt;
	    ResultSet rs;
	    String q;
	    boolean correctLegend;
	    final Properties p = new Properties();
	    p.setProperty("journal_mode", "WAL");
	    con = DriverManager.getConnection(url, p);

	    // The name of a sensor
	    q = "create table if not exists sensornameES (a text unique, b text unique)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists sensornameES_a on sensornameES (a)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists sensornameES_b on sensornameES (b)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // The name of a quantity
	    q = "create table if not exists quantitynameES (a text unique, b text unique)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists quantitynameES_a on quantitynameES (a)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists quantitynameES_b on quantitynameES (b)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // The sensor that did the measurement
	    q = "create table if not exists sensorEE (a text unique, b text)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists sensorEE_a on sensorEE (a)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists sensorEE_b on sensorEE (b)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // The time of the mesaurement
	    q = "create table if not exists timestampET (a text unique, b integer)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists timestampET_a on timestampET (a)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists timestampET_b on timestampET (b)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // The measured value of the datapoint
	    q = "create table if not exists valueEN (a text unique, b numeric)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists valueEN_a on valueEN (a)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // The order of the datapoint in the measurement
	    q = "create table if not exists ordinalEN (a text unique, b numeric)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists ordinalEN_a on ordinalEN (a)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // What has been measured in the datapoints
	    q = "create table if not exists quantityEE (a text unique, b text)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists quantityEE_a on quantityEE (a)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // Associtates a measurement with its datapoints
	    q = "create table if not exists datapointEE (a text, b text unique)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists datapointEE_a on datapointEE (a)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // Create the sensor, if necessary
	    String sensorId = "";
	    q = "select n.a from sensornameES n where n.b = ?";
	    stmt = con.prepareStatement(q);
	    stmt.setString(1, sensor);
	    rs = stmt.executeQuery();
	    if (rs.next()) {
		sensorId = rs.getString(1);
	    }
	    rs.close();
	    stmt.close();
	    if (sensorId.equals("")) {
		sensorId = getUID(con);
		q = "insert into sensornameES values (?, ?)";
		stmt = con.prepareStatement(q);
		stmt.setString(1, sensorId);
		stmt.setString(2, sensor);
		stmt.executeUpdate();
		stmt.close();
	    }
	    
	    con.setAutoCommit(false);
	    
	    String measurementId = getUID(con);

	    stmt = con.prepareStatement("insert into timestampET (a, b) values (?, ?)");
	    stmt.setString(1, measurementId);
	    if (isotime != null) {
		stmt.setDate(2, new java.sql.Date(DatatypeConverter.parseDateTime(isotime).getTime().getTime()));
	    }
	    else {
		stmt.setDate(2, new java.sql.Date((new Date()).getTime()));
	    }
	    stmt.executeUpdate();
	    stmt.close();

	    stmt = con.prepareStatement("insert into sensorEE (a, b) values (?, ?)");
	    stmt.setString(1, measurementId);
	    stmt.setString(2, sensorId);
	    stmt.executeUpdate();
	    stmt.close();

	    String[] quantities = legend.split(":");
	    String[] values = value.split(" ");
	    int qc = 0;
	    for (int i = 0; i < values.length && i+qc < quantities.length; i++) {
		// Skip extra ':' in legend
		while (i+qc < quantities.length && quantities[i+qc].equals("")) qc++;

		// Create the quantity, if necessary
		String quantityId = "";
		q = "select n.a from quantitynameES n where n.b = ?";
		stmt = con.prepareStatement(q);
		stmt.setString(1, quantities[i+qc]);
		rs = stmt.executeQuery();
		if (rs.next()) {
		    quantityId = rs.getString(1);
		}
		rs.close();
		stmt.close();
		if (quantityId.equals("")) {
		    quantityId = getUID(con);
		    q = "insert into quantitynameES values (?, ?)";
		    stmt = con.prepareStatement(q);
		    stmt.setString(1, quantityId);
		    stmt.setString(2, quantities[i+qc]);
		    stmt.executeUpdate();
		    stmt.close();
		}
		
		String datapointId = getUID(con);

		stmt = con.prepareStatement("insert into valueEN (a, b) values (?, ?)");
		stmt.setString(1, datapointId);
		stmt.setFloat(2, Float.parseFloat(values[i]));
		stmt.executeUpdate();
		stmt.close();
		
		stmt = con.prepareStatement("insert into ordinalEN (a, b) values (?, ?)");
		stmt.setString(1, datapointId);
		stmt.setInt(2, i);
		stmt.executeUpdate();
		stmt.close();
		
		stmt = con.prepareStatement("insert into quantityEE (a, b) values (?, ?)");
		stmt.setString(1, datapointId);
		stmt.setString(2, quantityId);
		stmt.executeUpdate();
		stmt.close();

		stmt = con.prepareStatement("insert into datapointEE (a, b) values (?, ?)");
		stmt.setString(1, measurementId);
		stmt.setString(2, datapointId);
		stmt.executeUpdate();
		stmt.close();
	    }
	    con.commit();
	    
	} catch (SQLException e) {
	    throw new IOException(e);
	}
	finally {
	    try { con.close(); } catch (Exception e) { throw new IOException(e); }
	}	
    }

    public static List<String> getSensors() throws IOException
    {
	List<String> sensors = new LinkedList<String>();
	String url = "jdbc:sqlite:" + sensorsroot + "/sensors.sqlite";
	System.out.println("getSensors: " + url);
	Connection con = null;
	try {
	    PreparedStatement stmt;
	    ResultSet rs;
	    boolean schema;
	    String q;
	    con = DriverManager.getConnection(url);

	    q = "select distinct n.b from sensornameES n";
	    stmt = con.prepareStatement(q);
	    rs = stmt.executeQuery();
	    while (rs.next()) {
		sensors.add(rs.getString(1));
	    }
	    rs.close();
	    stmt.close();
	    
	} catch (SQLException e) {
	    throw new IOException(e);
	}
	finally {
	    try { con.close(); } catch (Exception e) { throw new IOException(e); }
	}
	return sensors;
    }
    
    public static long getTime(String sensor) throws IOException
    {
	long time = 0;
	String url = "jdbc:sqlite:" + sensorsroot + "/sensors.sqlite";
	System.out.println("getTime: " + url);
	Connection con = null;
	try {
	    PreparedStatement stmt;
	    ResultSet rs;
	    boolean schema;
	    String q;
	    con = DriverManager.getConnection(url);

	    q = "select max(t.b) from timestampET t, sensorEE s, sensornameES n " +
		"where t.a = s.a and s.b = n.a and n.b = ?";
	    stmt = con.prepareStatement(q);
	    stmt.setString(1, sensor);
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
	String url = "jdbc:sqlite:" + sensorsroot + "/sensors.sqlite";
	System.out.println("pushLegend: " + sensor);
	Connection con = null;
	try {
	    PreparedStatement stmt;
	    ResultSet rs;
	    boolean schema;
	    String q;
	    con = DriverManager.getConnection(url);

	    q = "select group_concat(qn.b, ':') from quantityEE q, quantitynameES qn, ordinalEN o, datapointEE d, sensorEE s, sensornameES sn " +
		"where qn.a = q.b and q.a = d.b and o.a = d.b and s.a = d.a and s.b = sn.a and sn.b = ? " +
		"group by d.a order by o.b limit 1";
	    stmt = con.prepareStatement(q);
	    stmt.setString(1, sensor);
	    rs = stmt.executeQuery();
	    if (rs.next()) {
		out.write(("event: legend\n").getBytes("UTF-8"));
		out.write(("data: time:" + rs.getString(1) + "\n\n").getBytes("UTF-8"));
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

    public static Date pushLog(String sensor, Date last, Counter counter, int interval, OutputStream out) throws IOException
    {
	String url = "jdbc:sqlite:" + sensorsroot + "/sensors.sqlite";
	System.out.println("pushLog: " + sensor + " " + last.getTime());
	Connection con = null;
	Date time = last;
	try {
	    PreparedStatement stmt;
	    ResultSet rs;
	    boolean schema;
	    String q;
	    con = DriverManager.getConnection(url);

	    q = "select tim, group_concat(val, ' ') from (select max(t.b) as tim, max(d.a) as mes, sum(v.b)/count(v.b) as val, t.b/(?*1000) as bin from valueEN v, ordinalEN o, datapointEE d, sensorEE s, sensornameES n, timestampET t where v.a = d.b and o.a = d.b and s.a = d.a and t.a = d.a and t.b > ? and s.b = n.a and n.b = ? group by bin, o.b order by t.b, o.b) group by mes order by tim";
	    if (counter != null) q += " limit " + counter.count;
	    stmt = con.prepareStatement(q);
	    stmt.setInt(1, interval);
	    stmt.setDate(2, new java.sql.Date(last.getTime()));
	    stmt.setString(3, sensor);
	    rs = stmt.executeQuery();
	    String data_acc = null;
	    while (rs.next()) {
		Date now = new Date(rs.getDate(1).getTime());
		TimeZone tz = TimeZone.getTimeZone("UTC");
		SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'");
		df.setTimeZone(tz);
		out.write(("event: measurement\n").getBytes("UTF-8"));
		out.write(("data: " + df.format(now) + " " + rs.getString(2) + "\n\n").getBytes("UTF-8"));
		// Postpone stepping the time to here if rs.getString(2) throws an exception
		time = now;
		if (counter != null && counter.down()) {
		    break;
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
