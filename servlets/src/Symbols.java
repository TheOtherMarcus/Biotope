
import java.io.*;

/**
 * A symbol lookup interface.
 * 
 * @author maran
 *
 */
public interface Symbols {
    
    /**
     * Check if a name is bound.
     * 
     * @param name Name to check.
     * @return True if bound.
     */
    public boolean isBound(String name) throws IOException;

    /**
     * Get the value bound to a name.
     * 
     * @param name The name.
     * @return The value.
     */
    public byte[] lookup(String name) throws IOException;

    /** Get the URL location for a name.
     *
     * @param name The name.
     * @return URL.
     */
    public String location(String name) throws IOException;
}
