import it.polito.appeal.traci.SumoTraciConnection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Random;
import java.util.Map.Entry;

import de.tudresden.sumo.cmd.Lane;


public class CS {
	String location;		//located node id, same as id
	ArrayList<String> edgeList;
	HashMap<String, Integer> camera;
	LinkedHashMap<String, String> tlightMap;
	String tlight;
	
	public CS(String l){
		location = l;
		edgeList = new ArrayList<String>();		
		camera = new HashMap<String, Integer>();
		tlightMap = new LinkedHashMap<String, String>();
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
		tlightMap.put(direction, signal);
	}
	
	public void initCamera(){
		for (String s: edgeList)
			camera.put(s, 0);
	}
	
	public void initTLight(){
		for (Entry<String, String> e: tlightMap.entrySet())
			tlight = tlight + e.getValue();
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
	
	public LinkedHashMap<String, String> gettlightMap(){
		return tlightMap;
	}
	
	public String getLocation(){
		return location;
	}
	
	public String getTLight(){
		return tlight;
	}
	
	//lane이 한개밖에 없는 상황에서 작성되어 추후 lane의 개수가 늘어나면 수정되어야 함. it is written when the number of lane is 1. After increasing the number of lane,
	//this should be modified too.
	public void updateCamera(SumoTraciConnection conn) throws Exception{
		String laneDefaultName="_0";
		for (String s: edgeList){
			int nCar = (int) conn.do_job_get(Lane.getLastStepVehicleNumber(s+laneDefaultName));
			camera.put(s, nCar);
		}
	}
	
	//randomly assign
	public void updateAllTrafficLight(SumoTraciConnection conn, String[] signal) throws Exception{
		for (Entry<String, String> e: tlightMap.entrySet()){
			int rand = new Random().nextInt(signal.length);
			tlightMap.put(e.getKey(), signal[rand]);
		}
		tlight = "";
		initTLight();
	}
	
	//update traffic lights of this CS to red
	public void updateAllTrafficLightToRed(SumoTraciConnection conn) throws Exception{
		for (Entry<String, String> e: tlightMap.entrySet()){
			tlightMap.put(e.getKey(), "r");
		}
		tlight = "";
		initTLight();
	}
	
	//update one traffic light to some string
	public void updateTrafficLight(SumoTraciConnection conn, String key, String light) throws Exception{
		for (Entry<String, String> e: tlightMap.entrySet()){
			tlightMap.put(key, light);
		}
		tlight = "";
		initTLight();
	}
}
