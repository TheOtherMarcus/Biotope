
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

/** Servlet that performs tranformations on image retrieved from
 * URL. For local URL, the image is loaded directly from the
 * filesystem. Size adjustment, cropping and rotation. Returns result
 * as image/jpeg.
 */
public final class JpegProc extends HttpServlet
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
	    response.setContentType("image/jpeg");
	    response.setHeader("Cache-Control", "max-stale=31536000");
	    response.setHeader("Cache-Control", "max-age=31536000");
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
	    scaleImage2(in,
			request.getParameter("w"),
			request.getParameter("h"),
			request.getParameter("r"),
			request.getParameter("ulx"),
			request.getParameter("uly"),
			request.getParameter("lrx"),
			request.getParameter("lry"),
			response.getOutputStream());
	} catch (Exception e) {
	    if (request.getRequestURI()
		.equals(request.getContextPath() +
			request.getServletPath() + "/testimage")) {
		response.setContentType("image/png");
		testImage(response.getOutputStream());
	    }
	    else if (url != null) {
		response.sendRedirect(getContextURL(request) +
				      request.getServletPath() +
				      "?url=" + getContextURL(request) +
				                request.getServletPath() +
				                "/testimage" +
				      "&r=270" + 
				      (request.getParameter("w") != null ?
				       "&w=" + request.getParameter("w") : "") +
				      (request.getParameter("h") != null ?
				       "&h=" + request.getParameter("h") : ""));
	    }
	    else {
		e.printStackTrace();
		response.setContentType("text/html");
		help(getContextURL(request) + request.getServletPath(),
		     response.getOutputStream());
	    }
	}
    }
    public static void scaleImage(InputStream in,
				  String width,
				  String height,
				  String rotation,
				  String ulx,
				  String uly,
				  String lrx,
				  String lry,
				  OutputStream out)
	throws IOException
    {
	BufferedImage image = ImageIO.read(in);
	float xScale = 1.0f;
	float yScale = 1.0f;
	float thumbWidth = image.getWidth(null);
	float thumbHeight = image.getHeight(null);
	if (height != null && width != null) {
	    try {
		float w = Float.parseFloat(width);
		float h = Float.parseFloat(height);
		thumbWidth = w;
		thumbHeight = h;
		xScale = thumbWidth/image.getWidth(null);
		yScale = thumbHeight/image.getHeight(null);
	    } catch (NumberFormatException ne) {}
	}
	else if (width != null) {
	    try {
		float w = Float.parseFloat(width);
		thumbWidth = w;
		xScale = thumbWidth/image.getWidth(null);
		yScale = xScale;
		thumbHeight = yScale * image.getHeight(null);
	    } catch (NumberFormatException ne) {}
	}
	else if (height != null) {
	    try {
		float h = Float.parseFloat(height);
		thumbHeight = h;
		xScale = thumbHeight/image.getHeight(null);
		yScale = xScale;
		thumbWidth = yScale * image.getWidth(null);
	    } catch (NumberFormatException ne) {}
	}

	double rad = 0.0;
	if (rotation != null) {
	    try {
		rad = Float.parseFloat(rotation)*Math.PI/180.0;
	    } catch (NumberFormatException ne) {}
	}

	double s1 = 0.0;
	if (ulx != null) {
	    try {
		s1 = Float.parseFloat(ulx);
	    } catch (NumberFormatException ne) {}
	}
	double t1 = 0.0;
	if (uly != null) {
	    try {
		t1 = Float.parseFloat(uly);
	    } catch (NumberFormatException ne) {}
	}
	double s2 = 1.0;
	if (lrx != null) {
	    try {
		s2 = Float.parseFloat(lrx);
	    } catch (NumberFormatException ne) {}
	}
	double t2 = 1.0;
	if (lry != null) {
	    try {
		t2 = Float.parseFloat(lry);
	    } catch (NumberFormatException ne) {}
	}
	
	int transWidth = (int)Math.abs(Math.cos(rad)*thumbWidth +
				       Math.sin(rad)*thumbHeight);
	int transHeight = (int)Math.abs(Math.cos(rad)*thumbHeight +
					Math.sin(rad)*thumbWidth);

	double cScale = 1/(t2-t1);
	int cropWidth = (int)Math.floor(cScale * (s2-s1) * transWidth);
	int cropHeight = transHeight;

	BufferedImage thumbImage =
	    new BufferedImage(cropWidth,
			      cropHeight,
			      BufferedImage.TYPE_INT_RGB);
	Graphics2D graphics2D = thumbImage.createGraphics();
	graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
	graphics2D.translate(-cropWidth*s1/(s2-s1), -cropHeight*t1*cScale); 
	graphics2D.scale(cScale, cScale);
	graphics2D.translate(transWidth/2, transHeight/2);
	graphics2D.rotate(rad);
	graphics2D.translate(-thumbWidth/2, -thumbHeight/2);
	graphics2D.scale(xScale, yScale);
	graphics2D.drawImage(image, 0, 0, null);

	ImageIO.write(thumbImage, "jpeg", out);
	out.flush();
    }

    public static void scaleImage2(InputStream in,
				   String width,
				   String height,
				   String rotation,
				   String ulx,
				   String uly,
				   String lrx,
				   String lry,
				   OutputStream out)
	throws IOException
    {
	BufferedImage image = ImageIO.read(in);
	float origWidth = image.getWidth(null);
	float origHeight = image.getHeight(null);

	float s1 = 0.0f;
	if (ulx != null) {
	    try {
		s1 = Float.parseFloat(ulx);
	    } catch (NumberFormatException ne) {}
	}
	float t1 = 0.0f;
	if (uly != null) {
	    try {
		t1 = Float.parseFloat(uly);
	    } catch (NumberFormatException ne) {}
	}
	float s2 = 1.0f;
	if (lrx != null) {
	    try {
		s2 = Float.parseFloat(lrx);
	    } catch (NumberFormatException ne) {}
	}
	float t2 = 1.0f;
	if (lry != null) {
	    try {
		t2 = Float.parseFloat(lry);
	    } catch (NumberFormatException ne) {}
	}
	
	float cropWidth = origWidth * (s2-s1);
	float cropHeight = origHeight * (t2-t1);

	float rad = 0.0f;
	if (rotation != null) {
	    try {
		rad = Float.parseFloat(rotation)*(float)(Math.PI/180.0);
	    } catch (NumberFormatException ne) {}
	}

	float rotWidth = (float)Math.abs(Math.cos(rad)*cropWidth +
					 Math.sin(rad)*cropHeight);
        float rotHeight = (float)Math.abs(Math.cos(rad)*cropHeight +
					  Math.sin(rad)*cropWidth);
	
	float thumbWidth = rotWidth;
	float thumbHeight = rotHeight;
	float xScale = 1.0f;
	float yScale = 1.0f;
	if (height != null && width != null) {
	    try {
		float w = Float.parseFloat(width);
		float h = Float.parseFloat(height);
		thumbWidth = w;
		thumbHeight = h;
		xScale = thumbWidth/rotWidth;
		yScale = thumbHeight/rotHeight;
	    } catch (NumberFormatException ne) {}
	}
	else if (width != null) {
	    try {
		float w = Float.parseFloat(width);
		thumbWidth = w;
		xScale = thumbWidth/rotWidth;
		yScale = xScale;
		thumbHeight = (int)Math.floor(yScale * rotHeight);
	    } catch (NumberFormatException ne) {}
	}
	else if (height != null) {
	    try {
		float h = Float.parseFloat(height);
		thumbHeight = h;
		yScale = thumbHeight/rotHeight;
		xScale = yScale;
		thumbWidth = (int)Math.floor(xScale * rotWidth);
	    } catch (NumberFormatException ne) {}
	}

	BufferedImage thumbImage =
	    new BufferedImage((int)Math.floor(thumbWidth),
			      (int)Math.floor(thumbHeight),
			      BufferedImage.TYPE_INT_RGB);
	Graphics2D graphics2D = thumbImage.createGraphics();
	graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
	graphics2D.translate(thumbWidth/2, thumbHeight/2);
	graphics2D.rotate(rad);
	graphics2D.scale(xScale, yScale);
	graphics2D.translate(-origWidth*(s1-(1.0f-s2))/2, -origHeight*(t1-(1.0f-t2))/2); 
	graphics2D.translate(-origWidth/2, -origHeight/2);
	graphics2D.drawImage(image, 0, 0, null);

	ImageIO.write(thumbImage, "jpeg", out);
	out.flush();
    }

    public static void testImage(OutputStream out)
	throws IOException {
	BufferedImage img = new BufferedImage(400, 300,
					      BufferedImage.TYPE_INT_RGB);
	Graphics2D gfx = img.createGraphics();
	gfx.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
			     RenderingHints.VALUE_ANTIALIAS_ON);
	gfx.setColor(Color.red);
	gfx.fill(new Ellipse2D.Double(25, 25, 350, 250));
	gfx.setColor(Color.blue);
	gfx.fill(new Ellipse2D.Double(25, 25, 125, 125));
	ImageIO.write(img, "png", out);
	out.flush();
    }

    public static void help(String root, OutputStream out)
	throws IOException {
	String ex0 = root + "/testimage";
	String ex1 = root + "?url=" + root + "/testimage";
	String ex2 = root + "?url=" + root + "/testimage&h=200";
	String ex3 = root + "?url=" + root + "/testimage&w=200";
	String ex4 = root + "?url=" + root + "/testimage&h=200&w=200";
	String ex5 = root + "?url=" + root + "/testimage&h=200&ulx=0.2&uly=0.1&lrx=0.5&lry=0.8";
	String ex6 = root + "?url=" + root + "/testimage&h=200&ulx=0.2&uly=0.1&lrx=0.5&lry=0.8&r=90";
	String page =
	    "<!DOCTYPE html>" +
	    "<html lang=\"en\">" +
	    "<head>" +
	    "<meta charset=\"utf-8\">" +
	    "<title>The Photo Lab</title>" +
	    "</head>" +
	    "<body>" +
	    "<blockquote>" +
	    "<hr>" +
	    "<b>The Photo Lab -</b> Crop, rotate and change the size of an image." +
	    "<hr>" +
	    "Original:<br>" +
	    ex0 + "<br>" +
	    "<img src=\"" + ex0 + "\"/><hr>" +
	    "Encoded as image/jpeg:<br>" +
	    ex1 + "<br>" +
	    "<img src=\"" + ex1 + "\"/><hr>" +
	    "Scaled to specific height:<br>" +
	    ex2 + "<br>" +
	    "<img src=\"" + ex2 + "\"/><hr>" +
	    "Scaled to specific width:<br>" +
	    ex3 + "<br>" +
	    "<img src=\"" + ex3 + "\"/><hr>" +
	    "Changed aspect ratio:<br>" +
	    ex4 + "<br>" +
	    "<img src=\"" + ex4 + "\"/><hr>" +
	    "Cropped:<br>" +
	    ex5 + "<br>" +
	    "<img src=\"" + ex5 + "\"/><hr>" +
	    "Cropped and rotated:<br>" +
	    ex6 + "<br>" +
	    "<img src=\"" + ex6 + "\"/><hr>" +
	    "</blockquote>" +
	    "</body>" +
	    "</html>";
	out.write(page.getBytes("UTF-8"));
	out.flush();
    }
}
