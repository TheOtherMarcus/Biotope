import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletConfig;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.util.Comparator;
import java.util.Arrays;
import java.net.URLEncoder;
import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import java.util.TimeZone;
import java.util.Date;
import java.text.SimpleDateFormat;
import javax.xml.bind.DatatypeConverter;

import java.util.List;

public class Streamer extends HttpServlet {

    public String getContextURL(HttpServletRequest request)
    {
	return request.getScheme()
	                    + "://"
	    + request.getServerName()
	    + (request.getServerPort() == 80 ? "" : ":" + request.getServerPort())
	    + request.getContextPath();
    }
    
    public String getRootURL(HttpServletRequest request)
    {
	return getContextURL(request)
	    + request.getServletPath();
    }

    public static String now() {
	TimeZone tz = TimeZone.getTimeZone("UTC");
	SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'");
	df.setTimeZone(tz);
	return df.format(new Date());
    }

    private class Worker implements Runnable {

	private String sensor;
	private AsyncContext asyncContext;
	long filePointer;
	int timeout;
	Date start;
	Counter counter;
	int interval;
	
	public Worker(String sensor, Date start, int timeout, Counter counter, int interval, AsyncContext asyncContext) {
	    this.sensor = sensor;
	    this.start = start;
	    this.timeout = timeout;
	    this.counter = counter;
	    this.interval = interval;
	    this.asyncContext = asyncContext;
	}

	public void run() {
	    try {
		ServletOutputStream out = asyncContext.getResponse().getOutputStream();
		int dataUnchangedSeconds = 0;
		filePointer = 0;
		String line;
		RandomAccessFile file;
		Date last = new Date(start.getTime()-1);

		// Start with legend
		SensorLog4.pushLegend(sensor, out);
		
		while (dataUnchangedSeconds < timeout) {
		    Date time = SensorLog4.pushLog(sensor, last, counter, interval, out);
		    if (time.getTime() > last.getTime()) {
			dataUnchangedSeconds = 0;
			last = time;
		    }
		    else {
			dataUnchangedSeconds++;
		    }
		    if (counter != null && counter.count == 0) {
			break;
		    }
		    Thread.sleep(5000);
		}
	    }
	    catch (Exception e) {
		e.printStackTrace();
	    }
	    asyncContext.complete();
	}
    }
    
    public void doGet(HttpServletRequest request, HttpServletResponse response)
	throws ServletException, IOException {

	response.setCharacterEncoding("UTF-8");

	//add context to list for later use
	String sensor = request.getParameter("sensor");
	String start = request.getParameter("start");
	String timeout = request.getParameter("timeout");
	String count = request.getParameter("count");
	String interval = request.getParameter("interval");
	if (sensor == null) {
	    List<String> sensors = SensorLog4.getSensors();
	    for (String sensorname : sensors) {
		response.getOutputStream()
		    .write(("<a href=\"" + getRootURL(request) + "?sensor=" +
			    URLEncoder.encode(sensorname, "UTF-8") + "\">" +
			    escapeHtml4(sensorname) + "</a><br>").getBytes("UTF-8"));
	    }
	}
	else {
	    response.setContentType("text/event-stream");
	    final AsyncContext asyncContext = request.startAsync(request, response);
	    asyncContext.setTimeout(0);
	    
	    if (sensor.lastIndexOf('/') != -1) {
		sensor = sensor.substring(sensor.lastIndexOf('/'));
	    }
	    Date startDate = null;
	    if (start != null) {
		startDate = DatatypeConverter.parseDateTime(start).getTime();
	    }
	    else {
		startDate = new Date(SensorLog4.getTime(sensor));
	    }
	    int tmout = 10*60;
	    if (timeout != null) {
		tmout = Integer.parseInt(timeout);
	    }
	    Counter counter = null;
	    if (count != null) {
		counter = new Counter(Integer.parseInt(count));
	    }
	    int ival = 1;
	    if (interval != null) {
		ival = Integer.parseInt(interval);
	    }
	    asyncContext.start(new Worker(sensor, startDate, tmout, counter, ival, asyncContext));
	    System.out.println("Tracking " + sensor);
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
	
	String id = request.getParameter("id");
	String legend = request.getParameter("legend");
	String value = request.getParameter("value");
	String time = request.getParameter("time");
	
	if (id == null) {
	    throw new ServletException("Missing parameter 'id'.");
	}
	if (legend == null) {
	    throw new ServletException("Missing parameter 'legend'.");
	}
	if (value == null) {
	    throw new ServletException("Missing parameter 'value'.");
	}

	System.out.println("\npost " + id + " " + legend + " " + value);
	
	// Write to database
	SensorLog4.log("http_" + id, legend, value, time);
	
	response.getOutputStream().write("OK\n".getBytes("UTF-8"));
	response.getOutputStream().flush();
    }
    
    public void init(ServletConfig config) throws ServletException
    {
	super.init(config);

	SensorLog4.sensorsroot = getServletContext().getInitParameter("sensorsroot");
    } 
}
