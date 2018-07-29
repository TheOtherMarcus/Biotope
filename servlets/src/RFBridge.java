import java.io.IOException;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Arrays;
import java.util.Comparator;
import java.nio.file.Paths;

import org.postgresql.ds.PGPoolingDataSource;

import org.json.JSONObject;
import org.json.JSONArray;

import java.security.MessageDigest;

import java.lang.ProcessBuilder;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.nio.file.StandardOpenOption;
import java.nio.file.Files;

import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Date;

public final class RFBridge extends MetaProc {

    private InputStream in;
    private OutputStream out;
    private String sensorsroot;

    public static class SerialReader implements Runnable {
	
	InputStream in;
	OutputStream out;
	String sensorsroot;
	
	public SerialReader( InputStream in, OutputStream out, String sensorsroot ) {
	    this.in = in;
	    this.out = out;
	    this.sensorsroot = sensorsroot;
	}
	
	private int parsePulses(String msg, int start, int length, String zero, String one)
	    throws IOException {
	    int value = 0;
	    msg = msg.substring(start * zero.length());
	    for (int bits = 0; bits < length; bits++) {
		if (msg.startsWith(zero)) {
		    value = value << 1;
		    msg = msg.substring(zero.length());
		}
		else if (msg.startsWith(one)) {
		    value = (value << 1) + 1;
		    msg = msg.substring(one.length());
		}
		else {
		    throw new IOException("Wrong message format");
		}
	    }
	    return value;
	}
	
	private void weather1(String msg) throws IOException {
	    if (msg.length() != 74) return;
	    // Extract data fields
	    int id, battery, channel, temperature, humidity;
	    try {
		id = parsePulses(msg, 4, 8, "01", "02");
		battery = parsePulses(msg, 12, 2, "01", "02");
		channel = parsePulses(msg, 14, 2, "01", "02");
		temperature = parsePulses(msg, 16, 12, "01", "02");
		humidity = parsePulses(msg, 28, 8, "01", "02");
	    } catch (IOException e) {
		return;
	    }

	    System.out.println("\nweather1 " + id + " " + channel + " " + battery + " " + temperature + " " + humidity);

	    // Write to database
	    SensorLog.log(sensorsroot + "/" + "protocol=weather1_id=" + id + "_channel=" + channel,
			  ":battery:temperature:humidity",
			  battery + " " +
			  temperature / 10  + "." + temperature % 10 + " " +
			  humidity);
	}
	
	public void run() {
	    BufferedReader br = new BufferedReader(new InputStreamReader(in));
	    while (true) {
		try {
		    synchronized(this.out) {
			this.out.write("RF receive 0\n".getBytes("UTF-8"));
		    }
		    String line;
		    while ((line = br.readLine()) != null) {
			System.out.println(line);
			if (line.contains("ready")) {
			    synchronized(this.out) {
				this.out.write("RF receive 0\n".getBytes("UTF-8"));
			    }
			}
			else if (line.startsWith("RF receive")) {
			    String[] tokens = line.split("\\s");
			    if (tokens.length > 10) {
				// Parse the interval times
				int[] periods = new int[8];
				int values;
				for (values = 0; values < 8; values++) {
				    periods[values] = Integer.parseInt(tokens[2+values]);
				    if (periods[values] == 0) {
					break;
				    }
				}
				// Sort the intervals
				int[] ordered_periods = new int[values];
				for (int i = 0; i < values; i++) {
				    ordered_periods[i] = periods[i];
				}
				Arrays.sort(ordered_periods);
				for (int i = 0; i < values; i++) {
				    periods[i] = Arrays.binarySearch(ordered_periods, periods[i]);
				}
				// Convert message to sorted intervals
				String msg = "";
				for (int i = 0; i < tokens[10].length(); i++) {
				    msg += periods[Integer.parseInt(tokens[10].substring(i, i+1))];
				}
				// Supported devices
				weather1(msg);
			    }
			}
		    }
		} catch( Exception e ) {
		    e.printStackTrace();
		}
	    }
	}
    }

    /**
     * @see HttpServlet#HttpServlet()
     */
    public RFBridge() {
	super();
    }

    public void init(ServletConfig config) throws ServletException
    {
	super.init(config);

	sensorsroot = getServletContext().getInitParameter("sensorsroot");

	try {
	    String port = getServletContext().getInitParameter("homeduinoport");;
	    CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(port);
	    if (portIdentifier.isCurrentlyOwned()) {
		System.out.println("Error: Port is currently in use");
	    } else {
		int timeout = 2000;
		CommPort commPort = portIdentifier.open(getClass().getName(), timeout);
		
		if (commPort instanceof SerialPort) {
		    SerialPort serialPort = (SerialPort)commPort;
		    serialPort.setSerialPortParams(115200,
						   SerialPort.DATABITS_8,
						   SerialPort.STOPBITS_1,
						   SerialPort.PARITY_NONE);
		    
		    in = serialPort.getInputStream();
		    out = serialPort.getOutputStream();

		    ( new Thread( new SerialReader(in, out, sensorsroot) ) ).start();
		    
		} else {
		    System.out.println("Error: Only serial ports are handled.");
		}
	    }
	} catch(Exception e) {
	    System.out.println("");
	    e.printStackTrace();
	    throw new ServletException(e);
	}
    }
    
    public void destroy() {
	try {
	    out.close();
	    in.close();
	}
	catch (IOException e) {
	    e.printStackTrace();
	}
    }
    
    /**
     * Respond to a POST request for the content produced by
     * this servlet.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are producing
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public void doPost(HttpServletRequest request,
                      HttpServletResponse response)
	throws IOException, ServletException {
	
	response.setCharacterEncoding("UTF-8");
	synchronized(out) {
	    writeStream(request.getInputStream(), out);
	    out.write('\n');
	}
	response.getOutputStream().write("OK\n".getBytes("UTF-8"));
	response.getOutputStream().flush();
    }
}
