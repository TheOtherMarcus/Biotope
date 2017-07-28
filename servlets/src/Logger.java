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
import java.util.Arrays;
import java.util.Comparator;
import java.nio.file.Paths;

import org.postgresql.ds.PGPoolingDataSource;

import org.json.JSONObject;
import org.json.JSONArray;

import java.security.MessageDigest;

import java.lang.ProcessBuilder;

public final class Logger extends MetaProc {

    private String logroot;
    
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Logger() {
	super();
    }

    public void init(ServletConfig config) throws ServletException
    {
	super.init(config);
	logroot = getServletContext().getInitParameter("logroot");
    }
    
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
	throws IOException, ServletException {

	String p = request.getParameter("p");
	if (p != null) {
	    // With parameter p, generate content from filesystem
	    File f = new File("../log" + p);
	    if (f.isFile()) {
		writeStream(new FileInputStream(f), response.getOutputStream());
	    }
	    else {
		File[] files = f.listFiles();
		Arrays.sort(files, new Comparator<File>(){
			public int compare(File f1, File f2)
			{
			    return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
			}
		    });
		
		JSONArray result = new JSONArray();		
		for (File file : files) {
		    if (p.equals("/") && file.isDirectory()) {
			JSONObject obj = new JSONObject();
			obj.put("partition", file.getName());
			result.put(obj);
		    }
		    else if (!p.equals("/") && file.isFile()) {
			try {
			    JSONObject obj = new JSONObject();
			    obj.put("entry", file.getName());
			    // Load log entry from file
			    ByteArrayOutputStream baos = new ByteArrayOutputStream();
			    writeStream(new FileInputStream(file), baos);
			    JSONArray log = new JSONArray(new String(baos.toByteArray(), "UTF-8"));
			    // Extract data from log entry
			    JSONObject entry = log.getJSONObject(0);
			    if (entry.has("t")) {
				obj.put("t", entry.getString("t"));
			    }
			    if (entry.has("meta")) {
				obj.put("meta", entry.getString("meta"));
			    }
			    result.put(obj);		    
			} catch (Exception e) { e.printStackTrace(); }
		    }
		}
		response.getOutputStream().write(result.toString().getBytes("UTF-8"));
	    }
	}
	else {
	    // Witout parameter p, let MetaProc handle it
	    super.doGet(request, response);
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
	
	try {
	    response.setCharacterEncoding("UTF-8");
	    String partition = request.getRequestURI().replace(request.getContextPath() + request.getServletPath(), "").replace("/", "");
	    if (partition.equals("")) {
		throw new IOException("No partition selected.");
	    }
	    ByteArrayOutputStream out = new ByteArrayOutputStream();
	    writeStream(request.getInputStream(), out);
	    String hash = getSha256(out.toByteArray());
	    (new File(logroot + "/" + partition)).mkdir();
	    writeStream(new ByteArrayInputStream(out.toByteArray()),
			new FileOutputStream(logroot + "/" + partition + "/" + hash));
	    
	    String cd = Paths.get(".").toAbsolutePath().normalize().toString();
	    Process p = (new ProcessBuilder("/usr/bin/python",
					    "logreader.py",
					    "log/" + partition))
		.directory(new File(cd.substring(0, cd.lastIndexOf("/"))))
		.redirectErrorStream(true).start();
	    writeStream(p.getInputStream(), System.out);
	    p.waitFor();
	    
	    response.getOutputStream().write((getRootURL(request) + "/" + partition + "/" + hash).getBytes("UTF-8"));
	    response.getOutputStream().flush();
	}
	catch (InterruptedException ie) {
	    throw new IOException(ie);
	}
    }
}
