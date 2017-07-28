import java.io.IOException;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
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

import org.postgresql.ds.PGPoolingDataSource;

import java.security.MessageDigest;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.TimeZone;
import java.util.Date;
import java.text.SimpleDateFormat;

import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.commons.fileupload.*;
import org.apache.commons.fileupload.servlet.*;
import java.util.regex.*;

public final class Archiver extends MetaProc {

    private String archiveroot;
    
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Archiver() {
	super();
    }

    public void init(ServletConfig config) throws ServletException
    {
	super.init(config);
	archiveroot = getServletContext().getInitParameter("archiveroot");
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
	
	try {
	    response.setCharacterEncoding("UTF-8");
	    String partition = "/" + request.getRequestURI().replace(request.getContextPath() + request.getServletPath(), "").replace("/", "");
	    if (partition.equals("/")) {
		throw new IOException("No partition selected.");
	    }
	    ByteArrayOutputStream out = new ByteArrayOutputStream();
	    String[] mimetypes = null;
	    if (ServletFileUpload.isMultipartContent(request)) {
		ServletFileUpload upload = new ServletFileUpload();
		FileItemIterator iter = upload.getItemIterator(request);
		while (iter.hasNext()) {
		    FileItemStream item = iter.next();
		    String name = item.getFieldName();
		    InputStream stream = item.openStream();
		    if (item.isFormField()) {
			System.out.println("Form field " + name +
					   " with value "
					   + "xxx" +
					   " detected.");
			if (name.equals("m")) {
			    ByteArrayOutputStream val =
				new ByteArrayOutputStream();
			    writeStream(stream, val);
			    mimetypes = new String[1];
			    mimetypes[0] = val.toString("UTF-8");
			}
		    } else {
			System.out.println("File field " + name +
					   " with file name "
					   + item.getName() + " detected.");
			writeStream(stream, out);
		    }
		}
	    }
	    else {
		writeStream(request.getInputStream(), out);
	    }
	    if (mimetypes == null) {
		mimetypes = request.getParameterValues("m");
	    }
	    if (mimetypes == null) {
		mimetypes = new String[0];
	    }
	    String digest = getSha256(out.toByteArray());
	    JSONArray log = new JSONArray();
	    JSONObject o;
	    TimeZone tz = TimeZone.getTimeZone("UTC");
	    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'");
	    df.setTimeZone(tz);
	    String now = df.format(new Date());
	    String destination = request.getParameter("d");
	    String redirect = request.getParameter("r");
	    if (destination != null && destination.equals("log")) {
		for (String mimetype : mimetypes) {
		    String relation = null;
		    if (mimetype.equals("text/date")) {
			relation = "date_value";
		    }
		    else if (mimetype.equals("text/timestamp")) {
			relation = "timestamp_value";
		    }
		    else if (mimetype.equals("text/numeric")) {
			relation = "numeric_value";
		    }
		    if (relation != null) {
			o = new JSONObject();
			o.put("r", relation);
			o.put("h", digest);
			o.put("v", out.toString("UTF-8"));
			log.put(o);
		    }
		}
		o = new JSONObject();
		o.put("r", "text_value");
		o.put("h", digest);
		o.put("v", out.toString("UTF-8"));
		log.put(o);
	    }
	    else {
		(new File(archiveroot + partition)).mkdir();
		FileOutputStream fios =
		    new FileOutputStream(archiveroot + partition
					 + "/" + digest);
		System.out.println("Upload size: " + out.toByteArray().length);
		writeStream(new ByteArrayInputStream(out.toByteArray()),
			    fios);
		fios.close();
		// Local archive, relative path
		String url = "/archive" + partition + "/" + digest;
		String urldigest = getSha256(url.getBytes("UTF-8"));
		o = new JSONObject();
		o.put("r", "text_value");
		o.put("h", urldigest);
		o.put("v", url);
		log.put(o);
		o = new JSONObject();
		o.put("h", digest);
		o.put("i", urldigest);
		o.put("r", "location02");
		o.put("o", "t");
		o.put("t", now);
		log.put(o);
	    }
	    for (String mimetype : mimetypes) {
		mimetype = mimetype.replace("/", "_").replace("-", "_").replace(".", "_");
		o = new JSONObject();
		o.put("h", digest);
		o.put("r", mimetype + "01");
		o.put("o", "t");
		o.put("t", now);
		log.put(o);
	    }

	    byte[] postData = log.toString().getBytes("UTF-8");
	    URL logurl = new URL(getContextURL(request) + "/logger" + partition + ".archive");
	    HttpURLConnection connection = (HttpURLConnection) logurl.openConnection();
	    connection.setDoOutput(true);
	    connection.setRequestMethod("POST");
	    connection.setRequestProperty("Content-Type", "application/json");
	    connection.setRequestProperty("Content-Length",  "" + postData.length);
	    String authorization = request.getHeader("Authorization");
	    if (authorization != null) {
		connection.setRequestProperty("Authorization", authorization);
	    }
	    OutputStream os = connection.getOutputStream();
	    os.write(postData);
	    os.flush();
	    ByteArrayOutputStream logresponse = new ByteArrayOutputStream();
	    writeStream(connection.getInputStream(), logresponse);
	    System.out.write(logresponse.toByteArray());
	    System.out.write('\n');
	    connection.getInputStream().close();
	    os.close();

	    if (redirect != null) {
		response.sendRedirect(redirect);
	    }
	    else {
		response.getOutputStream().write(digest.getBytes("UTF-8"));
		response.getOutputStream().flush();
	    }
	}
	catch (FileUploadException fe) {
	    throw new IOException(fe);
	}
    }
}
