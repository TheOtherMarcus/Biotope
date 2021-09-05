
public class Counter
{    
    public int count;

    public Counter(int count)
    {
	this.count = count;
    }

    public boolean down()
    {
	return --count <= 0;
    }
}
