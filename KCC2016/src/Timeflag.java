
public class Timeflag {

	private String pid;
	private int priority;
	private String state;			//none, monitor, applied
	private int startTime;			//state를 시작한 time tick
	private int leftTime;			//해당 state의 남은 time tick
	
	public Timeflag(String pid, int priority){
		this.pid = pid;
		this.priority = priority;
		state = "none";
		startTime = -1;
		leftTime = -1;
	}
	
	public void setState (String state){
		this.state = state;
	}
	public void setstartTime (int startTime){
		this.startTime = startTime;
	}
	public void setleftTime (int leftTime){
		this.leftTime = leftTime;
	}
	
	public String getState (){
		return state;
	}
	public int getStartTime (){
		return startTime;
	}
	public int getLeftTime (){
		return leftTime;
	}
}
