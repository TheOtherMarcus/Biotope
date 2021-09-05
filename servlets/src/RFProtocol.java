
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;

public abstract class RFProtocol {

    public static Map<String, RFProtocol> protocols = new HashMap<String, RFProtocol>();

    static {
	protocols.put(Weather1.name, new Weather1());
	protocols.put(Switch1.name, new Switch1());
    }
    
    public int parsePulses(String msg, int start, int length, String zero, String one)
	throws IOException {
	int value = 0;
	msg = msg.substring(start);
	for (int bits = 0; bits < length; bits++) {
	    if (msg.startsWith(zero)) {
		value = value << 1;
		msg = msg.substring(zero.length());
	    }
	    else if (msg.startsWith(one)) {
		value = (value << 1) + 1;
		msg = msg.substring(one.length());
	    }
	    else {
		throw new IOException(getName() + ": Wrong message format");
	    }
	}
	return value;
    }

    public abstract String process(String msg) throws IOException;

    public abstract String generate(Map<String, String[]> parameters) throws IOException;

    public abstract String getName();
}
