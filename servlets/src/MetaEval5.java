
import java.io.*;
import java.util.*;
import java.sql.*;
import java.net.*;
import org.json.*;
import java.util.regex.*;
import java.text.*;

import com.Ostermiller.util.ConcatReader;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

/** 
 * Interpreter for a text composition language.
 * @author maran
 *
 */
public class MetaEval5 {
    public Connection con;
    public String auth;
    public String contextUrl;

    /**
     * Extract a type annotated name from an input stream reader.
     * 
     * @param isr Stream to read from.
     * @param names Append name to this list.
     * @param types Append "#" to this list if name is preceeded by '#', "" otherwise.
     * @param end Character that indicates that the name is complete. Will be discarded.
     * @return The name.
     * @throws IOException Thrown when reading from the input stream reader fails.
     */
    private String parseName(Reader isr,
			     LinkedList<String> names,
			     LinkedList<String> types,
			     int end)
            throws IOException {
        StringBuilder name = new StringBuilder();
        int ch = isr.read();
        if (ch == '#') {
            types.add("#");
        } else {
            types.add("");
            name.append((char)ch);
        }
        while ((ch = isr.read()) != -1 && ch != end) {
            name.append((char)ch);
        }
	String n = name.toString(); 
        names.add(n);
        return n;
    }

    /**
     * Parse an SQL query and extract variable bindings and parameters.
     * [name] yields a binding and ?name? yields a query parameter. Both can
     * be preceeded with '#' for integer data.
     * Special characters can be escaped with '\'.
     * 
     * @param in Stream to read from.
     * @param bindings List of names to bind the result columns to.
     * @param bindingTypes List of types, represented by "#" and "".
     * @param params List of names to use as query parameters.
     * @param paramTypes List of types, represented by "#" and "".
     * @return Query string suitable for PreparedStatement creation.
     * @throws IOException Thrown when the stream ends with a single '\'
     *                         or when reading from the input stream reader fails.
     */
    private String parseQuery(Reader isr,
			      LinkedList<String> bindings,
			      LinkedList<String> bindingTypes,
			      LinkedList<String> params,
			      LinkedList<String> paramTypes)
            throws IOException {
        StringBuilder sqlQuery = new StringBuilder();
        int ch = -1;
        while ((ch = isr.read()) != -1) {
            if (ch == '\\') {
                if ((ch = isr.read()) != -1) {
                    sqlQuery.append((char)ch);
                } else {
                    throw new IOException("Missing character after '\\' in query.");
                }
            } else if (ch == '[') {
                sqlQuery.append(parseName(isr, bindings, bindingTypes, ']'));
            } else if (ch == '?') {
                parseName(isr, params, paramTypes, '?');
                sqlQuery.append('?');
            } else {
                sqlQuery.append((char)ch);
            }
        }
        return sqlQuery.toString();
    }

    /**
     * Parse a regex and extract variable bindings for match groups.
     * 
     * @param in Stream to read from.
     * @param params List of names to bind the result of group matches to.
     * @return Regex string suitable for Pattern.compile().
     * @throws IOException Thrown when reading from the input stream reader fails.
     */
    private String parseRegex(Reader isr, LinkedList<String> params)
            throws IOException {
        LinkedList<String> types = new LinkedList<String>();
        StringBuilder regex = new StringBuilder();
        int ch = -1;
        while ((ch = isr.read()) != -1) {
            if (ch == '(') {
                regex.append((char)ch);
                int ch2 = isr.read();
                if (ch2 == '?') {
                    int ch3 = isr.read();
                    if (ch3 == 'P') {
                        int ch4 = isr.read();
                        if (ch4 == '<') {
                            parseName(isr, params, types, '>');
                        } else {
			    regex.append((char)ch2);
			    regex.append((char)ch3);
			    regex.append((char)ch4);
                        }
                    } else {
			regex.append((char)ch2);
			regex.append((char)ch3);
                    }
                } else {
		    regex.append((char)ch2);
                }
            } else {
		regex.append((char)ch);
            }
        }
        return regex.toString();
    }

    /** Helper function for the two functions below. */
    private String parseArgumentCommon(int ch, StringBuilder argument, Reader isr, int delimiter, int start, int end) throws IOException {
        while (ch != -1 && ch != delimiter && ch != end) {
	    if (ch == '\\') {
		argument.append((char)ch);
		ch = isr.read();
		if (ch == -1) {
                    throw new IOException("Missing character after '\\' in template code.");
		}
	    }
	    else if (ch == start) {
		argument.append((char)ch);
		argument.append(parseArgument1(isr, end, start, end));
		ch = end;
	    }
	    argument.append((char)ch);
	    ch = isr.read();
	}
	return argument.toString();
    }

    /** Read characters from stream until delimiter character. Treat
     * apply blocks as atomic. Used at beginning of block.
     */
    private String parseArgument1(Reader isr, int delimiter, int start, int end)
	throws IOException {
        StringBuilder argument = new StringBuilder();
        int ch = isr.read();
	if (start != '{' && ch == '{') {
	    argument.append((char)ch);
	    argument.append(parseArgument1(isr, delimiter, '{', '}'));
	    ch = '}';
	}
	else if (start != '[' && ch == '[') {
	    argument.append((char)ch);
	    argument.append(parseArgument1(isr, delimiter, '[', ']'));
	    ch = ']';
	} 
	else if (start != '<' && ch == '<') {
	    argument.append((char)ch);
	    argument.append(parseArgument1(isr, delimiter, '<', '>'));
	    ch = '>';
	}
	else if (start != '(' && ch == '(') {
	    argument.append((char)ch);
	    argument.append(parseArgument1(isr, delimiter, '(', ')'));
	    ch = ')';
	}
	return parseArgumentCommon(ch, argument, isr, delimiter, start, end);
    }

    /**
     * Read characters from stream until delimiter character. Treat
     * apply blocks as atomic.
     *
     * @param isr Stream to read from.
     * @param delimiter Character to stop on.
     * @returns Characters before delimiter.
     * @thros IOException Thrown when reading from the input stream fails.
     */
    private String parseArgument(Reader isr, int ch, int delimiter, int start, int end) throws IOException {
	if (ch == end) {
	    return "";
	}
        StringBuilder argument = new StringBuilder();
        ch = isr.read();
	return parseArgumentCommon(ch, argument, isr, delimiter, start, end);
    }

    /**
     * Read characters from input and write them to output.
     *
     * @param in Stream to read from.
     * @param out Stream to write to.
     * @thros IOException Thrown when reading or writing fails.
     */
    private void writeStream(InputStream in, OutputStream out)
	throws IOException {
	int n = in.available();
	if (n < 1024*1024) {
	    n = 1024*1024;
	}
	byte b[] = new byte[n];
	while ((n = in.read(b)) != -1) {
	    out.write(b, 0, n);
	}
    }
    
    /**
     * Apply works on a stream with one of several special forms. It
     * is responsible for figuring out which special form to use and
     * apply it on the rest of the input stream.  If no special form
     * matches, the stream will be evaluated and the result will be
     * written to the out stream.
     * 
     * @param in The stream to read from.
     * @param symbols The current scope with bound names.
     * @param out Stream to write result to.
     * @return Updated symbol table to be used from now on in the current scope.
     * @throws IOException
     */
    private SymbolTable apply(Reader isr, SymbolTable symbols,
			      OutputStream out, int start, int end) throws IOException {
	int ch = isr.read();
	String form = "";
	if (start != '{' && ch == '{') {
	    symbols = apply(isr, symbols, out, '{', '}');
	    ch = isr.read();
	}
	else if (start != '[' && ch == '[') {
	    symbols = apply(isr, symbols, out, '[', ']');
	    ch = isr.read();
	} 
	else if (start != '<' && ch == '<') {
	    symbols = apply(isr, symbols, out, '<', '>');
	    ch = isr.read();
	}
	else if (start != '(' && ch == '(') {
	    symbols = apply(isr, symbols, out, '(', ')');
	    ch = isr.read();
	}
	while (ch != -1) {
	    if (Character.isLetter(ch) || Character.isDigit(ch) || ch == '_' || ch == '-') {
		form += (char) ch;        
	    }
	    else if (ch == start) {
		// Apply and concatenate the result with what is left in isr.
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		symbols = apply(isr, symbols, baos, start, end);
		isr = new ConcatReader
		    (new InputStreamReader(new ByteArrayInputStream(baos.toByteArray()), "UTF-8"),
		     isr);
	    }
	    else {
		// Delimiter ch reached
		break;
	    }
	    ch = isr.read();
	}
	
	if (form.equals("")) {
	    if        (ch == '\'') { // Quote
		out.write(parseArgument(isr, ch, end, start, end).getBytes("UTF-8"));
	    } else if (ch == '$') { // Value
		String encoding = evalArgument(isr, symbols, ch, ch, start, end);
		String name = evalArgument(isr, symbols, ch, -1, start, end);
		if (encoding.equals("")) {
		    out.write(symbols.lookup(name));
		}
		else if (encoding.equals("html")) {
		    out.write(escapeHtml4(new String(symbols.lookup(name), "UTF-8")).getBytes("UTF-8"));
		}
		else if (encoding.equals("quote")) {
		    out.write((new String(symbols.lookup(name), "UTF-8")).getBytes("UTF-8"));
		}
		else if (encoding.equals("squote")) {
		    out.write((new String(symbols.lookup(name), "UTF-8")).replaceAll("'", "\\'").getBytes("UTF-8"));
		}
		else if (encoding.equals("json")) {
		    out.write(JSONObject.quote(new String(symbols.lookup(name), "UTF-8")).getBytes("UTF-8"));
		}
	    } else if (ch == ' ') { // Identity
		eval(isr, symbols, out, start, end);
	    } else if (ch == '+') { // Source/include
		String name = evalArgument(isr, symbols, ch, -1, start, end);
		symbols = eval(new ByteArrayInputStream(symbols.lookup(name)), symbols, out);
	    } else if (symbols.isBound("" + (char)ch)) { // Call
		SymbolTable bindings = new SymbolTable(symbols);
		bindings.bind("self", "" + (char)ch);
		bindings.bind(".", "" + (char)ch);
		bindings.bind("start", "" + (char)start);
		bindings.bind("end", "" + (char)end);
		bindings.bind("body", parseArgument(isr, ch, -1, start, end));
		eval(new ByteArrayInputStream(symbols.lookup("" + (char)ch)), bindings, out);
	    } else { // Function undefined, ignore
		evalBody(isr, symbols, ch, start, end);
	    }
	} else if (form.equals("first")) { // Before delimiter
	    String arg = parseArgument(evalBody(isr, symbols, ch, start, end), ch, ch, start, end);
	    out.write(arg.getBytes("UTF-8"));		
	} else if (form.equals("rest")) { // After delimiter
	    Reader body = evalBody(isr, symbols, ch, start, end);
	    parseArgument(body, ch, ch, start, end); // Skip first
	    out.write(parseArgument(body, ch, -1, start, end).getBytes("UTF-8"));
	} else if (form.equals("arg")) { // Set symbol, eval in parent scope
	    String name = evalArgument(isr, symbols, ch, ch, start, end);
	    String value = evalArgument(isr, symbols, ch, -1, start, end);
	    SymbolTable parent = new SymbolTable(symbols.getParent());
	    value = evalArgument(new StringReader(value), parent, -1, -1,
				 new InputStreamReader(new ByteArrayInputStream(symbols.lookup("start"))).read(),
				 new InputStreamReader(new ByteArrayInputStream(symbols.lookup("end"))).read());
	    symbols.bind(name, value);
	} else if (form.equals("let")) { // Set symbol
	    String name = evalArgument(isr, symbols, ch, ch, start, end);
	    String value = evalArgument(isr, symbols, ch, -1, start, end);
	    symbols.bind(name, value);
	} else if (form.equals("newentity")) {
	    String uuid = UUID.randomUUID().toString();
	    out.write(uuid.getBytes("UTF-8"));
	} else if (form.equals("urlenc")) {
	    String raw = evalArgument(isr, symbols, ch, -1, start, end);
	    out.write(URLEncoder.encode(raw, "UTF-8").getBytes("UTF-8"));
	} else if (form.equals("urldec")) {
	    String raw = evalArgument(isr, symbols, ch, -1, start, end);
	    out.write(URLDecoder.decode(raw, "UTF-8").getBytes("UTF-8"));
	} else if (form.equals("htmlenc")) {
	    String raw = evalArgument(isr, symbols, ch, -1, start, end);
	    out.write(escapeHtml4(raw).getBytes("UTF-8"));
	} else if (form.equals("quoteenc")) {
	    String raw = evalArgument(isr, symbols, ch, -1, start, end);
	    out.write(raw.replaceAll("\"", "\\\"").getBytes("UTF-8"));
	} else if (form.equals("location")) {
	    String name = evalArgument(isr, symbols, ch, -1, start, end);
	    out.write(symbols.location(name).getBytes("UTF-8"));
	} else if (form.equals("now")) {
	    TimeZone tz = TimeZone.getTimeZone("UTC");
	    SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'+00:00'");
	    df.setTimeZone(tz);
	    out.write(df.format(new java.util.Date()).getBytes("UTF-8"));
	} else if (form.equals("query")) {
	    String query = evalArgument(isr, symbols, ch, ch, start, end);
	    LinkedList<String> binds = new LinkedList<String>();
	    LinkedList<String> bindTypes = new LinkedList<String>();
	    LinkedList<String> params = new LinkedList<String>();
	    LinkedList<String> paramTypes = new LinkedList<String>();
	    String sqlQuery = parseQuery(new StringReader(query),
					 binds, bindTypes, params, paramTypes);
	    String code = evalArgument(isr, symbols, ch, ch, start, end);
	    String comma = evalArgument(isr, symbols, ch, -1, start, end);
	    try {
		PreparedStatement stmt = con.prepareStatement(sqlQuery);
		for (int i = 0; i < params.size(); i++) {
		    if (paramTypes.get(i).equals("#")) {
			stmt.setInt(i + 1, Integer.parseInt(new String(symbols.lookup(params.get(i)), "UTF-8")));
		    } else {
			stmt.setString(i + 1, new String(symbols.lookup(params.get(i)), "UTF-8"));
		    }
		}
		ResultSet rs = stmt.executeQuery();
		while (rs.next()) {
		    SymbolTable bindings = new SymbolTable(symbols);
		    for (int i = 0; i < binds.size(); i++) {
			if (bindTypes.get(i).equals("#")) {
			    bindings.bind(binds.get(i),
					  ("" + rs.getInt(binds
							  .get(i))).getBytes("UTF-8"));
			} else {
			    String value = rs.getString(binds.get(i));
			    if (value == null) {
				value = "";
			    }
			    bindings.bind(binds.get(i),
					  value.getBytes("UTF-8"));
			}
		    }
		    eval(new StringReader(code), bindings, out, start, end);
		    if (!rs.isLast()) {
			eval(new StringReader(comma), bindings, out, start, end);
		    }
		}
	    } catch (SQLException e) {
		System.out.println("query: \"" + sqlQuery + "\"");
		System.out.println(e.toString());
	    }
	} else if (form.equals("regex")) {
	    String input = evalArgument(isr, symbols, ch, ch, start, end);
            LinkedList<String> params = new LinkedList<String>();
            String regex = parseRegex(new StringReader(evalArgument(isr, symbols, ch, ch, start, end)), params);
            Matcher matcher = Pattern.compile(regex).matcher(input);
	    if (matcher.matches()) {
		SymbolTable bindings = new SymbolTable(symbols);
		try {
		    for (int i = 0; i < params.size(); i++) {
			bindings.bind(params.get(i),
				      matcher.group(i + 1).getBytes("UTF-8"));
		    }
		}
		catch (NullPointerException npe) {
		    // A group did not match
		}
		eval(new StringReader(evalArgument(isr, symbols, ch, ch, start, end)),
		     bindings, out, start, end);
		parseArgument(isr, ch, -1, start, end); // Skip negative branch
	    }
	    else {
		parseArgument(isr, ch, ch, start, end); // Skip positive branch
		eval(new StringReader(evalArgument(isr, symbols, ch, -1, start, end)),
		     symbols, out, start, end);
	    }
	} else if (form.equals("foreach")) {
	    JSONArray array = new JSONArray(evalArgument(isr, symbols, ch, ch, start, end));
	    String code = evalArgument(isr, symbols, ch, ch, start, end);
	    String comma = evalArgument(isr, symbols, ch, -1, start, end);
	    for (int i = 0; i < array.length(); i++) {
		System.out.println("" + i);
		SymbolTable bindings = new SymbolTable(symbols);
		JSONObject obj = array.getJSONObject(i);
		for (Object key : obj.keySet()) {
		    System.out.println((String)key);
		    bindings.bind((String)key, obj.getString((String)key));
		}
		eval(new StringReader(code), bindings, out, start, end);
		if (i+1 < array.length()) out.write(comma.getBytes("UTF-8"));
	    }
	} else if (form.equals("format")) {
	    String pattern = evalArgument(isr, symbols, ch, ch, start, end);
	    String number = evalArgument(isr, symbols, ch, -1, start, end);
	    out.write(String.format(pattern, Integer.parseInt(number)).getBytes("UTF-8"));
	} else if (form.equals("httpget")) {
	    String url = evalArgument(isr, symbols, ch, -1, start, end);
	    if (url.indexOf("/") == 0) {
		url = contextUrl + url;
	    }
	    try {
		URLConnection urlConnection = (new URL(url)).openConnection();
		if (url.indexOf(contextUrl) == 0) {
		    urlConnection.setRequestProperty("Authorization", auth);
		}
		InputStream indata = urlConnection.getInputStream();
		writeStream(indata, out);
		indata.close();
	    } catch (Exception ex) {
		// Something wrong with the URL.
	    }
	} else if (form.equals("markdown")) {
	    String text = evalArgument(isr, symbols, ch, -1, start, end).replace("\n", "<br>");
	    out.write(text.getBytes("UTF-8"));
	} else if (symbols.isBound(form)) { // Call
	    SymbolTable bindings = new SymbolTable(symbols);
	    bindings.bind("self", form);
	    bindings.bind(".", "" + (char)ch);
	    bindings.bind("start", "" + (char)start);
	    bindings.bind("end", "" + (char)end);
	    bindings.bind("body", parseArgument(isr, ch, -1, start, end));
	    eval(new ByteArrayInputStream(symbols.lookup(form)), bindings, out);
	} else { // Function undefined, ignore
	    evalBody(isr, symbols, ch, start, end);
	}
	return symbols;
    }

    private Reader evalBody(Reader isr, SymbolTable symbols, int ch, int start, int end)
	throws IOException {
	if (ch == end) {
	    return new StringReader("");
	}
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	eval(isr, symbols, baos, start, end);
	return new InputStreamReader(new ByteArrayInputStream(baos.toByteArray()), "UTF-8");
    }
    
    private String evalArgument(Reader isr, SymbolTable symbols, int ch, int delimiter, int start, int end)
	throws IOException {
	String arg = parseArgument(isr, ch, delimiter, start, end);
	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	eval(new StringReader(arg), symbols, baos, start, end);
	return new String(baos.toByteArray(), "UTF-8");
    }
    
    /**
     * Evaluate a stream of meta code until end of block or stream.
     * 
     * @param isr The stream to read from.
     * @param symbols The current scope with bound names.
     * @param out Stream to write result to.
     * @return The local symbol table as it is at the end of the evaluation.
     * @throws IOException Thrown when the stream ends with a single '\'
     *                         or when reading from the input stream fails.
     */
    private SymbolTable eval(Reader isr, SymbolTable scope, OutputStream out, int start, int end)
	throws IOException {
        SymbolTable symbols = new SymbolTable(scope);
        OutputStreamWriter osw = new OutputStreamWriter(out, "UTF-8");
        int ch = isr.read();
	while (ch != -1 && ch != end) {
            if (ch == '\\') {
                if ((ch = isr.read()) != -1) {
                    if (ch == 'n') {
                        osw.write('\n');
                    } else if (ch == 't') {
                        osw.write('\t');
                    } else {
                        osw.write(ch);
                    }
                } else {
                    throw new IOException("Missing character after '\\' in template code.");
                }
            } else if (ch == start) {
                osw.flush();
                symbols = apply(isr, symbols, out, start, end);
            } else {
                osw.write(ch);
            }
	    ch = isr.read();
        }
        osw.flush();
        return symbols;
    }
    
    /** Evaluation starts here. Block start and end characters are
     * reset to default..
     *
     * @param in The stream to read from.
     * @param symbols The current scope with bound names.
     * @param out Stream to write result to.
     * @return The local symbol table as it is at the end of the evaluation.
     * @throws IOException Thrown when the stream ends with a single '\'
     */
    public SymbolTable eval(InputStream in, SymbolTable scope, OutputStream out)
	throws IOException {
	return eval(new InputStreamReader(in, "UTF-8"), scope, out, '(', ')');
    }
}
