import java.util.Properties;

import java.util.List;
import java.util.LinkedList;

import java.util.Date;
import java.util.TimeZone;
import java.text.SimpleDateFormat;
import javax.xml.bind.DatatypeConverter;

import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.BufferedReader;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.HashMap;

public final class SensorLog4 {

    public static String sensorsroot = "";
    public static HashMap<String, String> readPath = new HashMap<String, String>();
    public static HashMap<String, BufferedReader> reader = new HashMap<String, BufferedReader>();
    
    public static SimpleDateFormat timestamp = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
    public static SimpleDateFormat date = new SimpleDateFormat("yyyy-MM-dd");


    public static String loadLegend(String legendPath)
    {
	try {
	    return new String(Files.readAllBytes(Paths.get(legendPath)), "UTF-8");
	} catch(IOException e) {
	    return "";
	}
    }

    public static void saveLegend(String legendPath, String legend) throws FileNotFoundException
    {
    	File f = new File(legendPath);
	if (f.exists()) {
	    f.delete();
	}
	PrintWriter out = new PrintWriter(legendPath);
	out.print(legend);
	out.close();
    }

    public static void log(String sensor, String legend, String value, String isotime) throws IOException, FileNotFoundException
    {
	timestamp.setTimeZone(TimeZone.getDefault());
	date.setTimeZone(TimeZone.getDefault());

	Date time = new Date();
	if (isotime != null) {
	    time = DatatypeConverter.parseDateTime(isotime).getTime();
	}

	String sensorPath = sensorsroot + "/" + sensor;
	File dir = new File(sensorPath);
	if (!dir.exists()) {
	    dir.mkdir();
	}

	// Legend
	legend = "time" + legend;
	String legendPath = sensorPath + "/LEGEND";
	String oldLegend = loadLegend(legendPath);
	if (!oldLegend.equals(legend)) {
	    saveLegend(legendPath, legend);
	}
	
	String logPath = sensorPath + "/" + date.format(time);
	PrintWriter out = new PrintWriter(new FileOutputStream(new File(logPath), true));
	out.append(timestamp.format(time) + " " + value + "\n");
	out.close();

	System.out.println(sensor + " " + legend + " " + value + " " + timestamp.format(time));
    }

    public static List<String> getSensors() throws IOException
    {
	List<String> sensors = new LinkedList<String>();
	System.out.println("getSesors");
	return sensors;
    }
    
    public static long getTime(String sensor) throws IOException
    {
	long time = 0;
	System.out.println("getTime: " + sensor);
	return time;
    }
    
    public static void pushLegend(String sensor, OutputStream out) throws IOException
    {
	String sensorPath = sensorsroot + "/" + sensor;
	String legendPath = sensorPath + "/LEGEND";
	String legend = loadLegend(legendPath);
	System.out.println("pushLegend: " + sensor);
	out.write(("event: legend\n").getBytes("UTF-8"));
	out.write(("data: " + legend + "\n\n").getBytes("UTF-8"));
	out.flush();
    }

    public static Date pushLog(String sensor, Date last, Counter counter, int interval, OutputStream out) throws IOException
    {
	date.setTimeZone(TimeZone.getDefault());
	Date time = new Date();

	String newReadPath = sensorsroot + "/" + sensor + "/" + date.format(time);
	
	if (!newReadPath.equals(readPath.get(sensor))) {
	    try {
		readPath.put(sensor, newReadPath);
		reader.put(sensor, new BufferedReader(new FileReader(readPath.get(sensor))));
	    }
	    catch (IOException e) {
		readPath.put(sensor, null);
		reader.put(sensor, null);
	    }
	}
	if (reader.get(sensor) != null) {
	    String line;
	    while ((line = reader.get(sensor).readLine()) != null) {
		out.write(("event: measurement\n").getBytes("UTF-8"));
		out.write(("data: " + line + "\n\n").getBytes("UTF-8"));
	    }
	    out.flush();
	}
	return time;
    }
}
