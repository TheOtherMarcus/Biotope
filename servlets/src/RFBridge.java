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
	
	public SerialReader( InputStream in, OutputStream out) {
	    this.in = in;
	    this.out = out;
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
				// Iterate over supported protocols
				for (RFProtocol protocol : RFProtocol.protocols.values()) {
				    try {
					System.out.println(protocol.process(msg));
					break;
				    } catch (IOException e) {}
				}
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

	SensorLog4.sensorsroot = getServletContext().getInitParameter("sensorsroot");

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

		    ( new Thread( new SerialReader(in, out) ) ).start();
		    
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
