
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.*;
import java.io.*;
import java.lang.Math;
import java.net.*;
import com.drew.metadata.*;
import com.drew.imaging.*;
import org.json.*;

/** Servlet that performs tranformations on dot files.
 */
public final class ExifProc extends HttpServlet
{
    private String getContextURL(HttpServletRequest request)
    {
	return request.getScheme()
	                    + "://"
	    + request.getServerName()
	    + (request.getServerPort() == 80 ? "" : ":" + request.getServerPort())
	    + request.getContextPath();
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
	String url = null;
	try {
	    response.setContentType("application/json");
	    url = request.getParameter("url");
	    InputStream in = null;
	    String root = getContextURL(request);
	    if (url.indexOf(root + "/archive/") == 0 ||
		url.indexOf("/archive/") == 0) {
		in = (new FileInputStream(".." + url.replace(root, "")));
	    }
	    else {
		URLConnection urlConnection = (new URL(url)).openConnection();
		String authorization = request.getHeader("Authorization");
		if (authorization != null && url.indexOf(root) == 0) {
		    urlConnection.setRequestProperty("Authorization", authorization);
		}
		in = urlConnection.getInputStream();
	    }

	    JSONObject base = new JSONObject();
	    Metadata metadata = ImageMetadataReader.readMetadata(in);
	    for (Directory directory : metadata.getDirectories()) {
		JSONObject dir = new JSONObject();
		base.put(directory.getName(), dir);
		for (Tag tag : directory.getTags()) {
		    dir.put(tag.getTagName(), tag.getDescription());
		}
		if (directory.hasErrors()) {
		    for (String error : directory.getErrors()) {
			System.out.println("EXIF ERROR: " + error);
		    }
		}
	    }
	    response.getOutputStream().write(base.toString(2).getBytes("UTF-8"));
	    
	} catch (Exception e) {
	    e.printStackTrace();
	    response.setContentType("text/html");
	    help(getContextURL(request) + request.getServletPath(),
		 response.getOutputStream());
	}
    }

    public static void help(String root, OutputStream out)
	throws IOException {
	String page =
	    "<!DOCTYPE html>" +
	    "<html lang=\"en\">" +
	    "<head>" +
	    "<meta charset=\"utf-8\">" +
	    "<title>EXIF Extractor</title>" +
	    "</head>" +
	    "<body>" +
	    "<blockquote>" +
	    "<hr>" +
	    "<b>EXIF Extractor -</b> Get EXIF data from images." +
	    "<hr>" +
	    "</blockquote>" +
	    "</body>" +
	    "</html>";
	out.write(page.getBytes("UTF-8"));
	out.flush();
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
}
