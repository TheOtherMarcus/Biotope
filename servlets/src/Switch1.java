
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.util.Date;

public class Switch1 extends RFProtocol {

    public static String name = "switch1";
    
    public Switch1() {}
    
    private long graceTime = 3000;
    private Map<String, Long> lastTime = new HashMap<String, Long>();
    
    public String process(String msg) throws IOException {
	if (msg.length() != 132) throw new IOException(getName() + ": Wrong message lenght");
	// Extract data fields
	int id = parsePulses(msg, 2, 26, "0001", "0100");
	int all = parsePulses(msg, 106, 1, "0001", "0100");
	int state = parsePulses(msg, 110, 1, "0001", "0100");
	int unit = parsePulses(msg, 114, 4, "0001", "0100");

	return "protocol=" + getName() + "&id=" + id + "&all=" + all + "&state=" + state + "&unit=" + unit;
    }
    
    public String generate(Map<String, String[]> parameters) throws IOException {
	throw new IOException(getName() + ": Method generate() not implemented");
    }

    public String getName() {
	return name;
    }
}
