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

import java.nio.file.StandardOpenOption;
import java.nio.file.Files;

public class Streamer extends HttpServlet {

    String sensorsroot;

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

	private String sensorpath;
	private AsyncContext asyncContext;
	long filePointer;
	int timeout;
	Date start;
	
	public Worker(String sensorpath, Date start, int timeout, AsyncContext asyncContext) {
	    this.sensorpath = sensorpath;
	    this.start = start;
	    this.timeout = timeout;
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
		SensorLog.pushLegend(sensorpath, out);
		
		while (dataUnchangedSeconds < timeout) {
		    Date time = SensorLog.pushLog(sensorpath, last, out);
		    if (time.getTime() > last.getTime()) {
			dataUnchangedSeconds = 0;
			last = time;
		    }
		    else {
			dataUnchangedSeconds++;
		    }
		    Thread.sleep(1000);
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
	if (sensor == null) {
	    File f = new File(sensorsroot);
	    File[] files = f.listFiles();
	    Arrays.sort(files, new Comparator<File>(){
		    public int compare(File f1, File f2)
		    {
			return f2.getName().compareTo(f1.getName());
		    }
		});
	    for (File file : files) {
		if (file.isFile() && file.getName().endsWith(".sqlite")) {
		    try {
			String filename = file.getName();
			String sensorname = filename.substring(0, filename.length() - 7);
			response.getOutputStream()
			    .write(("<a href=\"" + getRootURL(request) + "?sensor=" +
				    URLEncoder.encode(sensorname, "UTF-8") + "\">" +
				    escapeHtml4(sensorname) + "</a><br>").getBytes("UTF-8"));
		    } catch (Exception e) { e.printStackTrace(); }
		}
	    }
	}
	else {
	    response.setContentType("text/event-stream");
	    final AsyncContext asyncContext = request.startAsync(request, response);
	    asyncContext.setTimeout(0);
	    
	    if (sensor.lastIndexOf('/') != -1) {
		sensor = sensor.substring(sensor.lastIndexOf('/'));
	    }
	    Date startDate = new Date();
	    if (start != null) {
		startDate = DatatypeConverter.parseDateTime(start).getTime();
	    }
	    int tmout = 10*60;
	    if (timeout != null) {
		tmout = Integer.parseInt(timeout);
	    }
	    String sensorpath = sensorsroot + "/" + sensor;
	    asyncContext.start(new Worker(sensorpath, startDate, tmout, asyncContext));
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
	SensorLog.log(sensorsroot + "/" + "protocol=post_id=" + id,
		      legend, value);
	
	response.getOutputStream().write("OK\n".getBytes("UTF-8"));
	response.getOutputStream().flush();
    }
    
    public void init(ServletConfig config) throws ServletException
    {
	super.init(config);

	sensorsroot = getServletContext().getInitParameter("sensorsroot");
    } 
}
