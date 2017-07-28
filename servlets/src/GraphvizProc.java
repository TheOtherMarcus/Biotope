
import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.*;
import java.io.*;
import java.lang.Math;
import java.awt.*;
import java.awt.image.*;
import java.net.*;
import javax.imageio.*;
import java.awt.geom.Ellipse2D;

/** Servlet that performs tranformations on dot files.
 */
public final class GraphvizProc extends HttpServlet
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
	    response.setContentType("image/svg+xml");
	    url = request.getParameter("url");
	    URLConnection urlConnection = (new URL(url)).openConnection();
	    String authorization = request.getHeader("Authorization");
	    String root = getContextURL(request);
	    if (authorization != null && url.indexOf(root) == 0) {
		urlConnection.setRequestProperty("Authorization", authorization);
	    }
	    
	    String[] c1 = {"/usr/bin/dot", "-Tsvg"};
	    Process p1 = exec(c1, urlConnection.getInputStream());
	    String[] c2 = {"sed",
			   "-e", "s_<text text-anchor=\"middle\" x=\"\\([-0-9\\.]*\\)\" y=\"\\([-0-9\\.]*\\)\" [^>]*>\\(http[^<]*\\)</text>_<image x=\"\\1\" y=\"\\2\" width=\"60\" height=\"60\" externalResourcesRequired=\"true\" preserveAspectRatio=\"xMidYMid meet\" xlink:href=\"\\3\\&amp;h=120\" transform=\"translate(-30,-5)\"/>_",
			   "-e", "s/2\\.00/8.00/"};
	    Process p2 = exec(c2, p1.getInputStream());
	    writeStream(p2.getInputStream(), response.getOutputStream());
	    p2.waitFor();
	    
	} catch (Exception e) {
	    e.printStackTrace();
	    response.setContentType("text/html");
	    help(getContextURL(request) + request.getServletPath(),
		 response.getOutputStream());
	}
    }

    private static Process exec(final String[] cmd, final InputStream data)
	throws Exception
    {
	Runtime r = Runtime.getRuntime();
	final Process proc;

	proc = r.exec(cmd);
	
	new Thread(new Runnable() {
		public void run() {
		    try {
			if (data != null) {
			    byte[] buffer = new byte[4096];
			    int n;
			    while ((n = data.read(buffer, 0, buffer.length))
				   != -1) {
				proc.getOutputStream().write(buffer, 0, n);
			    }
			    System.out.println("EOF " + cmd[0]);
			    proc.getOutputStream().close();
			}
			int i = proc.waitFor();
			System.out.println(cmd[0] + " exit: " + i);
			InputStream in = proc.getErrorStream();
			while(in.available() > 0) {
			    System.out.write(in.read());
			}
		    }
		    catch (Exception e) {
			e.printStackTrace();
		    }
		}
	    }).start();
	
	return proc;
    }

    public static void help(String root, OutputStream out)
	throws IOException {
	String page =
	    "<!DOCTYPE html>" +
	    "<html lang=\"en\">" +
	    "<head>" +
	    "<meta charset=\"utf-8\">" +
	    "<title>Graph generator</title>" +
	    "</head>" +
	    "<body>" +
	    "<blockquote>" +
	    "<hr>" +
	    "<b>Graph generator -</b> Create graphs in svg from graphviz specs." +
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
