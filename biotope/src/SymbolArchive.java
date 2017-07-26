
import java.io.*;
import java.util.*;
import java.sql.*;
import java.net.*;

/**
 * A hierarchical mapping of names to scopes, parameters and values.
 * 
 * @author maran
 *
 */
public class SymbolArchive implements Symbols {

    private static HashMap<String, byte[]> filecache = new HashMap<String, byte[]>();
    
    private Connection con;
    private String auth;
    private SymbolTable locations;
    private SymbolTable cache;
    private String contextUrl = "";
    
    /**
     * Create a new local scope.
     * 
     * @param con Database connection.
     */
    public SymbolArchive(Connection con, String auth, String contextUrl) {
        this.con = con;
	if (auth == null) {
	    this.auth = "";
	}
	else {
	    this.auth = auth;
	}
	this.contextUrl = contextUrl;
	cache = new SymbolTable(null);
    }

    /**
     * Read characters from input and write them to output.
     *
     * @param in Stream to read from.
     * @param out Stream to write to.
     * @thros IOException Thrown when reading or writing fails.
     */
    private static void writeStream(InputStream in, OutputStream out)
	throws IOException {
	int n = in.available();
	byte b[] = new byte[n];
	while ((n = in.read(b)) != -1) {
	    out.write(b, 0, n);
	}
    }

    /** Get the URL location for a name.
     *
     * @param name The name.
     * @return URL.
     */
    public String location(String name) throws IOException {
	PreparedStatement stmt = null;
	ResultSet rs = null;
	if (locations == null) {
	    locations = new SymbolTable(null);
	    try {
		// Get all symbol names and their urls.
		stmt = con.prepareStatement
		    ("select tn.v, t.v from text_value t, location02c11 l, data11cnn d, macro10cn m, name11c11 n, text_value tn"
		     + " where t.h = l.i and d.h = l.h and d.a = m.a and n.a = m.a and tn.h = n.h");
		rs = stmt.executeQuery();
		while (rs.next()) {
		    String symbol = rs.getString(1);
		    String url = rs.getString(2);
		    if (url.indexOf("/") == 0) {
			// Local archive
			url = contextUrl + url;
		    }
		    locations.bind(symbol, url);
		}
	    } catch (SQLException e) {
		try { con.rollback(); } catch (SQLException ex) { throw new IOException(ex); }
		throw new IOException(e);
	    }
	    finally {
		try { rs.close(); } catch (Exception e) {}
		try { stmt.close(); } catch (Exception e) {}
	    }
	}
	return new String(locations.lookup(name), "UTF-8");
    }
    
    /**
     * Check if a name is bound.
     * 
     * @param name Name to check.
     * @return True if bound.
     */
    public boolean isBound(String name) throws IOException {
	if (cache.isBound(name)) {
	    return true;
	}
	try {
	    System.out.println("loading macro: " + name);
	    String url = location(name);
	    if (filecache.containsKey(url)) {
		System.out.println("+- from cache: " + url);
		cache.bind(name, filecache.get(url));
		return true;
	    }
	    InputStream in = null;
	    if (url.indexOf(contextUrl + "/archive/") == 0) {
		in = new BufferedInputStream(new FileInputStream(".." + url.replace(contextUrl, "")));
	    }
	    else if (!url.equals("")) {
		URLConnection urlConnection = (new URL(url)).openConnection();
		if (url.indexOf(contextUrl) == 0) {
		    urlConnection.setRequestProperty("Authorization", auth);
		}
		in = new BufferedInputStream(urlConnection.getInputStream());
	    }
	    if (!url.equals("")) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		writeStream(in, baos);
		in.close();
		filecache.put(url, baos.toByteArray());
		cache.bind(name, baos.toByteArray());
		return true;
	    }
	} catch (Exception e) {
	    throw new IOException(e);
	}
	return false;
    }

    /**
     * Get the value bound to a name.
     * 
     * @param name The name.
     * @return The value.
     */
    public byte[] lookup(String name) throws IOException {
	if (!cache.isBound(name) && !isBound(name)) {
	    // Also cache empty responses
	    cache.bind(name, new byte[0]);
	}
	return cache.lookup(name);
    }
}
