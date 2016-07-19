import it.polito.appeal.traci.SumoTraciConnection;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.Map.Entry;

import de.tudresden.sumo.cmd.Edge;
import de.tudresden.sumo.cmd.Lane;


public class CS {
	private String location;		//located node id, same as id
	private ArrayList<String> edgeList;
	private HashMap<String, Integer> camera;
	//LinkedHashMap<String, String> tlightMap;
	private ArrayList<TLight> tlightMap;			//중복을 허용하지 않기 때문에 한 엣지에서 두 레인이 모두 같은 방향으로 가게 될 경우 겹쳐버려서 tlight가 저장되지 않는 문제 극복.
	private String tlight;
	
	public CS(String l){
		location = l;
		edgeList = new ArrayList<String>();		
		camera = new HashMap<String, Integer>();
		tlightMap = new ArrayList<TLight>();
		tlight = "";
	}
	
	public Boolean hasEdge(String e){
		Boolean ret = false;
		for (String s: edgeList){
			if (s.compareTo(e)==0){
				ret = true;
				break;
			}
		}
		return ret;
	}
	
	public void addEdge(String e){
		edgeList.add(e);
	}
	
	public void addTLight(String direction, String signal){
		//tlightMap.put(direction, signal);
		tlightMap.add(new TLight(direction, signal));
	}
	
	public void initCamera(){
		for (String s: edgeList)
			camera.put(s, 0);
	}
	
	public void initTLight(){
		for (TLight t: tlightMap)
			tlight = tlight + t.getValue();
	}
	
	public ArrayList<String> getEdgeList(){
		return edgeList;
	}

	public HashMap<String, Integer> getCamera(){
		return camera;
	}
	
	public int getNumOfCar(String e){
		return camera.get(e);
	}
	
	public ArrayList<TLight> gettlightMap(){
		return tlightMap;
	}
	
	public String getLocation(){
		return location;
	}
	
	public String getTLight(){
		return tlight;
	}
	
	public void updateCamera(SumoTraciConnection conn) throws Exception{
		for (String s: edgeList){
			int nCar = (int) conn.do_job_get(Edge.getLastStepVehicleNumber(s));
			camera.put(s, nCar);
		}
	}
	
	//randomly assign
	public void updateAllTrafficLight(SumoTraciConnection conn, String[] signal) throws Exception{
		for (TLight t: tlightMap){
			int rand = new Random().nextInt(signal.length);
//			tlightMap.put(e.getKey(), signal[rand]);
			t.setTLight(t.getKey(), signal[rand]);
		}
		tlight = "";
		initTLight();
	}
	
	//update traffic lights of this CS to red
	public void updateAllTrafficLightToRed(SumoTraciConnection conn) throws Exception{
//		for (Entry<String, String> e: tlightMap.entrySet()){
		for (TLight t: tlightMap){
//			tlightMap.put(e.getKey(), "r");
			t.setTLight(t.getKey(), "r");
		}
		tlight = "";
		initTLight();
	}
	
	//update one traffic light to some string
	public void updateTrafficLight(SumoTraciConnection conn, String key, String light) throws Exception{
		for (TLight t: tlightMap){
			if (t.getKey().compareTo(key)==0)
				t.setTLight(t.getKey(), light);
		}
		tlight = "";
		initTLight();
	}
}


class TLight{
	private String loc;
	private String light;
	
	public TLight(String key, String value){
		loc = key;
		light = value;
	}
	
	public String getKey(){
		return loc;
	}
	
	public String getValue(){
		return light;
	}
	
	public void setTLight(String key, String value){
		loc = key;
		light = value;
	}
}
