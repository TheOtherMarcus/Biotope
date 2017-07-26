
import java.io.*;
import java.util.*;

/**
 * A hierarchical mapping of names to scopes, parameters and values.
 * 
 * @author maran
 *
 */
public class SymbolTable implements Symbols {
    
    /** Outer scope. */
    private Symbols scope;

    /** Mapping of a name to a value. */
    private HashMap<String, byte[]> valueMap = new HashMap<String, byte[]>();

    /**
     * Create a new local scope.
     * 
     * @param scope Outer scope, null if none.
     */
    public SymbolTable(Symbols scope) {
        this.scope = scope;
    }

    /**
     * Bind a name to a value.
     * 
     * @param name Name of binding.
     * @param value Value to bind.
     * @throws IOException Thrown when name already bound in the local scope.
     */
    public void bind(String name, byte[] value) throws IOException {
        if (valueMap.containsKey(name)) {
            throw new IOException(name + " already defined.");
        }
        valueMap.put(name, value);
    }

    /**
     * Bind a name to a value.
     * 
     * @param name Name of binding.
     * @param value Value to bind.
     * @throws IOException Thrown when name already bound in the local scope.
     */
    public void bind(String name, String value) throws IOException {
        if (valueMap.containsKey(name)) {
            throw new IOException(name + " already defined.");
        }
        valueMap.put(name, value.getBytes("UTF-8"));
    }

    /**
     * Check if a name is bound.
     * 
     * @param name Name to check.
     * @return True if bound.
     */
    public boolean isBound(String name) throws IOException {
        return valueMap.containsKey(name) ? true : scope != null ? scope.isBound(name) : false;
    }

    /**
     * Get the value bound to a name.
     * 
     * @param name The name.
     * @return The value.
     */
    public byte[] lookup(String name) throws IOException {
        if (valueMap.containsKey(name)) {
            byte[] value = valueMap.get(name);
            return value;
        } else if (scope == null) {
            return new byte[0];
        } else {
            return scope.lookup(name);
        }
    }

    /** Get the URL location for a name.
     *
     * @param name The name.
     * @return URL.
     */
    public String location(String name) throws IOException
    {
	if (valueMap.containsKey(name)) {
	    return "";
	}
	else if (scope != null) {
	    return scope.location(name);
	}
	else {
	    return "";
	}
    }

    /**
     * Get the parent scope.
     *
     * @return Symbols
     */
    public Symbols getParent()
    {
	return scope;
    }
}
