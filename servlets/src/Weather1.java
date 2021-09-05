
import java.util.Map;
import java.util.HashMap;
import java.io.IOException;
import java.util.Date;

public class Weather1 extends RFProtocol {

    public static String name = "weather1";
    
    public Weather1() {}
    
    private long graceTime = 3000;
    private Map<String, Long> lastTime = new HashMap<String, Long>();
    
    public String process(String msg) throws IOException {
	if (msg.length() != 74) throw new IOException(getName() + ": Wrong message lenght");
	// Extract data fields
	int id = parsePulses(msg, 8, 8, "01", "02");
	int battery = parsePulses(msg, 24, 2, "01", "02");
	int channel = parsePulses(msg, 28, 2, "01", "02");
	int temperature = parsePulses(msg, 32, 12, "01", "02");
	int humidity = parsePulses(msg, 56, 8, "01", "02");

	try {
	    long time = (new Date()).getTime();
	    String sensor = "" + getName() + "_" + id + "_" + channel;
	    lastTime.putIfAbsent(sensor, 0L);
	    if (lastTime.get(sensor) + graceTime < time) {
		lastTime.put(sensor, time);
		// Write to sensor log
		SensorLog4.log(sensor,
			       ":battery:temperature:humidity",
			       battery + " " +
			       temperature / 10  + "." + temperature % 10 + " " +
			       humidity, null);
	    }
	} catch (IOException e) { e.printStackTrace(); }

	return "protocol=" + getName() + "&id=" + id + "&channel=" + channel + "&battery=" + battery + "&temperature=" + temperature + "&humidity=" + humidity;
    }
    
    public String generate(Map<String, String[]> parameters) throws IOException {
	throw new IOException(getName() + ": Method generate() not implemented");
    }

    public String getName() {
	return name;
    }
}
