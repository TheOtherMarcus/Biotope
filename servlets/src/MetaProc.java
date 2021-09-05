import java.io.IOException;
import java.io.PrintWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Enumeration;
import java.util.Properties;

import org.postgresql.ds.PGPoolingDataSource;
import org.postgresql.ds.PGSimpleDataSource;

import java.security.MessageDigest;

import java.util.TimeZone;
import java.util.Date;
import java.text.SimpleDateFormat;

public class MetaProc extends HttpServlet {

    protected PGSimpleDataSource source;

    protected String prefix;
    
    static private Properties props = new Properties();
    static {
	try {
	    props.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("biotope.properties"));
	}
	catch (Exception e) {
	    e.printStackTrace();
	}
    }

    /**
     * @see HttpServlet#HttpServlet()
     */
    public MetaProc() {
	super();
    }

    public void init(ServletConfig config) throws ServletException
    {
	super.init(config);
	prefix = config.getServletName() + "_";
	
	try {
	    Class.forName(props.getProperty("db.driver"));
	}
	catch (ClassNotFoundException e) {
	    e.printStackTrace();
	}
	source = new PGSimpleDataSource();
	//source.setDataSourceName(prefix + props.getProperty("db.datasourcename"));
	source.setServerName(props.getProperty("db.servername"));
	source.setDatabaseName(props.getProperty("db.databasename"));
	source.setUser(props.getProperty("db.user"));
	source.setPassword(props.getProperty("db.password"));
	//source.setMaxConnections(Integer.parseInt(props.getProperty("db.maxconnections")));
    }

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
    
    /**
     * Respond to a GET request for the content produced by
     * this servlet.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are producing
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet error occurs
     */
    public void doGet(HttpServletRequest request,
                      HttpServletResponse response)
	throws IOException, ServletException {
	
	MetaEval5 me = new MetaEval5();
	try {
	    me.con = source.getConnection();
	    me.con.setTransactionIsolation(me.con.TRANSACTION_READ_UNCOMMITTED);
	    //me.con.setAutoCommit(false);
	    me.auth = request.getHeader("Authorization");
	    me.contextUrl = getContextURL(request);
	    response.setCharacterEncoding("UTF-8");
	    String url = request.getRequestURI().replaceFirst(request.getContextPath() + request.getServletPath(), "");
	    if (url.length() == 0) {
		url = "/";
	    }
	    SymbolTable bindings = new SymbolTable(new SymbolArchive(me.con,
								     request.getHeader("Authorization"),
								     getContextURL(request)));
	    bindings.bind("prefix", prefix);
	    bindings.bind("url", url);
	    bindings.bind("contexturl",  getContextURL(request));
	    bindings.bind("rooturl",  getRootURL(request));
	    if (request.getRemoteUser() != null) {
		bindings.bind("principal", request.getRemoteUser());
	    }
	    else {
		bindings.bind("principal", "");
	    }
	    Enumeration<String> params = request.getParameterNames();
	    while (params.hasMoreElements()) {
		String name = params.nextElement();
		bindings.bind("p_" + name, request.getParameter(name));
	    }
	    Cookie[] cookies = request.getCookies();
	    if (cookies != null) {
		for (int c = 0; c < cookies.length; c++) {
		    bindings.bind("c_" + cookies[c].getName(), cookies[c].getValue());
		}
	    }
	    String entryPoint = prefix + "root";
	    bindings.bind("self", entryPoint);
	    me.eval(new ByteArrayInputStream(bindings.lookup(entryPoint)),
		    bindings, response.getOutputStream());
	    //me.con.commit();
	}
	catch (SQLException e) {
            try { me.con.rollback(); } catch (SQLException ex) { throw new IOException(ex); }
	    throw new IOException(e);
	}
	finally {
	    if (me.con != null) {
		try { me.con.close(); } catch (SQLException e) {}
	    }
	}
    }


    /**
     * Read characters from input and write them to output.
     *
     * @param in Stream to read from.
     * @param out Stream to write to.
     * @thros IOException Thrown when reading or writing fails.
     */
    public static void writeStream(InputStream in, OutputStream out) throws IOException {
	int n = in.available();
	if (n < 1024*1024) {
	    n = 1024*1024;
	}
	byte b[] = new byte[n];
	while ((n = in.read(b)) != -1) {
	    out.write(b, 0, n);
	}
    }

    public static String getSha256(byte[] value) {
	try{
	    MessageDigest md = MessageDigest.getInstance("SHA-256");
	    md.update(value);
	    return bytesToHex(md.digest());
	} catch(Exception ex){
	    throw new RuntimeException(ex);
	}
    }
    
    public static String bytesToHex(byte[] bytes) {
	StringBuffer result = new StringBuffer();
	for (byte byt : bytes) result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
	return result.toString();
    }

    public static String now() {
	TimeZone tz = TimeZone.getTimeZone("UTC");
	SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'");
	df.setTimeZone(tz);
	return df.format(new Date());
    }
}
