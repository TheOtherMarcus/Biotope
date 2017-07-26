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

public final class Account extends MetaProc {

    private String passfile = null;
    
    /**
     * @see HttpServlet#HttpServlet()
     */
    public Account() {
	super();
    }

    public void init(ServletConfig config) throws ServletException
    {
	super.init(config);
	passfile = getServletContext().getInitParameter("passfile");
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

	String root = getRootURL(request);
	Properties users = new Properties();
	users.load(new FileInputStream(passfile));

	if (request.getServletPath().equals("/reset")) {
	    if (request.getParameter("name") == null ||
		users.getProperty(request.getParameter("name")) == null) {
		response.sendRedirect(root + "?result=unknown");
	    }
	    else {
		response.sendRedirect(root + "?result=ok");
	    }
	}
	else {
	    if (request.getParameter("pwd") == null) {
		response.sendRedirect(root + "?result=wrongpwd");
	    }
	    else if (request.getParameter("npwd1") == null ||
		     request.getParameter("npwd2") == null ||
		     request.getParameter("npwd1") != request.getParameter("npwd2")) {
		response.sendRedirect(root + "?result=pwdmismatch");
	    }
	    else {
		response.sendRedirect(root + "?result=ok");
	    }
	}
    }	
}
