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

public final class SensorLog3 {

    public static String sensorsroot = "";
    public static String uid = "";

    public static synchronized String getUID(Connection con)  throws SQLException {
	PreparedStatement stmt;
	ResultSet rs;
	String q;

	if (uid == null || uid.equals("")) {
	    q = "select max(m) from (" +
		"select max(cast(m.id as integer)) as m from measurement m UNION " +
		"select max(cast(s.id as integer)) as m from sensor s UNION " +
		"select max(cast(d.id as integer)) as m from datapoint d UNION " +
		"select max(cast(q.id as integer)) as m from quantity q )";
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
	    q = "create table if not exists sensor (id text unique, name text unique)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists sensor_id on sensor (id)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists sensor_name on sensor (name)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // The name of a quantity
	    q = "create table if not exists quantity (id text unique, name text unique)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists quantity_id on quantity (id)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists quantity_name on quantity (name)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // The sensor that did the measurement
	    q = "create table if not exists datapoint (id text unique, measurement text, quantity text, value numeric, ordinal numeric)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists datapoint_id on datapoint (id)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists datapoint_measurement on datapoint (measurement)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists datapoint_quantity on datapoint (quantity)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // The time of the mesaurement
	    q = "create table if not exists measurement (id text unique, sensor text, time integer)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists measurement_id on measurement (id)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists measurement_sensor on measurement (sensor)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();
	    q = "create index if not exists measurement_time on measurement (time)";
	    stmt = con.prepareStatement(q);
	    stmt.executeUpdate();
	    stmt.close();

	    // Create the sensor, if necessary
	    String sensorId = "";
	    q = "select id from sensor where name = ?";
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
		q = "insert into sensor (id, name) values (?, ?)";
		stmt = con.prepareStatement(q);
		stmt.setString(1, sensorId);
		stmt.setString(2, sensor);
		stmt.executeUpdate();
		stmt.close();
	    }
	    
	    con.setAutoCommit(false);
	    
	    String measurementId = getUID(con);

	    stmt = con.prepareStatement("insert into measurement (id, sensor, time) values (?, ?, ?)");
	    stmt.setString(1, measurementId);
	    stmt.setString(2, sensorId);
	    if (isotime != null) {
		stmt.setDate(3, new java.sql.Date(DatatypeConverter.parseDateTime(isotime).getTime().getTime()));
	    }
	    else {
		stmt.setDate(3, new java.sql.Date((new Date()).getTime()));
	    }
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
		q = "select id from quantity where name = ?";
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
		    q = "insert into quantity (id, name) values (?, ?)";
		    stmt = con.prepareStatement(q);
		    stmt.setString(1, quantityId);
		    stmt.setString(2, quantities[i+qc]);
		    stmt.executeUpdate();
		    stmt.close();
		}
		
		String datapointId = getUID(con);

		stmt = con.prepareStatement("insert into datapoint (id, measurement, quantity, value, ordinal) values (?, ?, ?, ?, ?)");
		stmt.setString(1, datapointId);
		stmt.setString(2, measurementId);
		stmt.setString(3, quantityId);
		stmt.setFloat(4, Float.parseFloat(values[i]));
		stmt.setInt(5, i);
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

	    q = "select name from sensor";
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

	    q = "select max(m.time) from measurement m, sensor s " +
		"where m.sensor = s.id and s.name = ?";
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

	    q = "select group_concat(q.name, ':') from quantity q, datapoint d, measurement m, sensor s " +
		"where q.id = d.quantity and d.measurement = m.id and m.sensor = s.id and s.name = ? " +
		"group by m.id order by d.ordinal limit 1";
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

	    q = "select tim, group_concat(val, ' ') from (select max(m.time) as tim, max(m.id) as mes, sum(d.value)/count(d.value) as val, m.time/(?*1000) as bin from datapoint d, sensor s, measurement m where s.id = m.sensor and m.id = d.measurement and m.time > ? and s.name = ? group by bin, d.ordinal order by m.time, d.ordinal) group by mes order by tim";
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
