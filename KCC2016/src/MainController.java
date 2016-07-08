import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import de.tudresden.sumo.cmd.Route;
import de.tudresden.sumo.cmd.Simulation;
import de.tudresden.sumo.cmd.Trafficlights;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.ws.container.SumoStringList;
import it.polito.appeal.traci.SumoTraciConnection;


public class MainController {
	
	static String trafficLightSignal[] = {"y", "r", "g"};
	static int changeToSoS = 3;
	static int trafficLightUpdateCycle = 0;
	
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub

		String sumo_bin = "D:/Coursework/Thesis/sumo-win32-0.25.0/sumo-0.25.0/bin/sumo-gui.exe";
		String config = "D:/Coursework/Thesis/sumo-win32-0.25.0/sumo-0.25.0/KCCPROJECT/sim.cfg";
		String relEdgesFileDir = "C:/Users/WonKyung/git/KCC2016/relatedEdges.txt";
		String trafDirectionFileDir = "C:/Users/WonKyung/git/KCC2016/trafficDirection.txt";
		BufferedReader br = new BufferedReader(new FileReader(new File(relEdgesFileDir)));
		String policyDir = "policy1.xml";
		parsingPolicy(policyDir);
		
		int arrivedCar =0;
		//initiation of CS-monitoring camera
		String line = "";
		HashMap<String, CS> csList = new HashMap<String, CS>();
		while((line = br.readLine())!=null){
			String[] split = line.split("\\t");
			CS cs = new CS(split[0]);
			
			for (int i=1; i<split.length; i++){
				cs.addEdge(split[i]);
				cs.initCamera();
			}
			csList.put(cs.getLocation(), cs);
		}
		
		//initiation of CS-traffic light
		br = new BufferedReader(new FileReader(new File(trafDirectionFileDir)));
		while((line = br.readLine())!=null){
			String[] split = line.split("\\t");
			CS cs = csList.get(split[0]);
			
			//randomly assign the traffic light
			for (int i=1; i<split.length; i++){
				int rand = new Random().nextInt(3);
				cs.addTLight(split[i], trafficLightSignal[rand]);
			}
			cs.initTLight();
			//System.out.println(cs.getLocation() + " / "+ cs.getTLight());
		}
//		System.out.println(csList.get("12").getTLight());
		SumoTraciConnection conn = new SumoTraciConnection(sumo_bin, config);
		conn.addOption("step-length", "1");
		conn.runServer();

		conn.do_job_set(Vehicle.add("init", "car", "rush1", 0, 0, 13.8, (byte) 0));

		
		
		//assign traffic light initially
		for (Entry<String, CS> e: csList.entrySet()){
			if (e.getKey().compareTo("01")==0 || e.getKey().compareTo("04")==0 || e.getKey().compareTo("31")==0 || e.getKey().compareTo("34")==0)
				continue;
	
			conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
		}
		

		int flagSoS = -1;			// -1-- normal state, 0-- SoS state before control, else -- SoS state after control
		for (int i=0; i<3600; i++){

			int simtime;
			simtime = (int) conn.do_job_get(Simulation.getCurrentTime());
			  
			 //차 랜덤생성(확률)
			 int randNum = (int)(Math.random() * 100);
			 if (randNum < 70)
				 conn.do_job_set(Vehicle.add("rush1"+i, "car", "rush1", simtime, 0, 13.8, (byte) 0));
			 else if (randNum >= 70 && randNum <80)
				 conn.do_job_set(Vehicle.add("genr"+i, "car", "genr1", simtime, 0, 13.8, (byte) 0));
			 else if (randNum >= 80 && randNum <90)
				 conn.do_job_set(Vehicle.add("gern"+i, "car", "genr2", simtime, 0, 13.8, (byte) 0));
			 else if (randNum >= 90 && randNum <100)
				 conn.do_job_set(Vehicle.add("genr"+i, "car", "genr3", simtime, 0, 13.8, (byte) 0));
			
			//traffic light 주기적 업데이트
			if (i%trafficLightUpdateCycle ==0 && flagSoS==-1){		//10초마다
				for (Entry<String, CS> e: csList.entrySet()){
					if (e.getKey().compareTo("01")==0 || e.getKey().compareTo("04")==0 || e.getKey().compareTo("31")==0 || e.getKey().compareTo("34")==0)
						continue;
					e.getValue().updateAllTrafficLight(conn, trafficLightSignal);
					conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
				}
			}
//			System.out.println(csList.get("12").getTLight());

			
			//SoS 상황 설정
			int nCar = csList.get("12").getCamera().get("A2S") + csList.get("12").getCamera().get("B2E") +
					csList.get("23").getCamera().get("B3S") + csList.get("23").getCamera().get("C3S");
			
			if (nCar >= changeToSoS && flagSoS==-1){		//SoS 상황 설정 -- 해당 차로에 100대가 넘는 차량이 몰려있을 경우를 SoS 상황으로 설정함.
				flagSoS = 0;
			}
			
			System.out.println(simtime+" / "+nCar);
			
			//SoS 상황시 대응
			if (flagSoS == 0){
				//모든 신호등을 r로 바꾼 뒤, 
				System.out.println("SOS!!!!");
				List<String> rushEdges = (List<String>) conn.do_job_get(Route.getEdges("rush1"));
				SumoStringList strList = new SumoStringList(rushEdges);
				
				for (Entry<String, CS> e: csList.entrySet()){
					if (e.getKey().compareTo("01")==0 || e.getKey().compareTo("04")==0 || e.getKey().compareTo("31")==0 || e.getKey().compareTo("34")==0)
						continue;
					
					//해당 길목(rush1)에 해당하는 신호등들의 신호만 g로 바꿈.
					if (e.getKey().compareTo("02")==0 || e.getKey().compareTo("12")==0 || e.getKey().compareTo("13")==0 || e.getKey().compareTo("23")==0 || e.getKey().compareTo("33")==0){
						e.getValue().updateAllTrafficLightToRed(conn);
						
						for (String tl: strList){
							for (String key: e.getValue().gettlightMap().keySet()){
								if (key.startsWith(tl)){		//만약 이 edge 명으로 시작하면,
									e.getValue().updateTrafficLight(conn, key, "g");		//해당 edge에서 빠져나가는 노드는 모두 g로 바꾸어줌.
								}
							}
							conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
						}
					}
				}
				
				flagSoS = i;
				//만약 상황이 종료되었으면 flag를 다시 0으로 바꿔줌
			}
			
			//최소 15tick 후, flag를 0으로 바꿔줌.
			if (flagSoS +15 < i && flagSoS != -1 && flagSoS != 0){
				flagSoS = -1;
				//tlight 정상화
				for (Entry<String, CS> e: csList.entrySet()){
					if (e.getKey().compareTo("01")==0 || e.getKey().compareTo("04")==0 || e.getKey().compareTo("31")==0 || e.getKey().compareTo("34")==0)
						continue;
					e.getValue().updateAllTrafficLight(conn, trafficLightSignal);
					conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
				}
			}
							
			
/*
			List<String> rushEdges = (List<String>) conn.do_job_get(Route.getEdges("rush1"));
			SumoStringList str = new SumoStringList(rushEdges);
			for (String s: str)
				System.out.print(" / "+s);*/
			
			//monitoring update
			for (Entry<String, CS> c: csList.entrySet()){
				 c.getValue().updateCamera(conn);
			 }

			
			arrivedCar += (int) conn.do_job_get(Simulation.getArrivedNumber());
			 conn.do_timestep();
			 
		}
		
		System.out.println("# Arrived car is " + arrivedCar);
		
		conn.close();

		
		
	}

	private static void parsingPolicy(String policyDir) {
		// TODO Auto-generated method stub
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(policyDir));
			
			//normal_policy가 가지고 있는 non-traffic jam 일 때의 신호등 update cycle을 가장 먼저 초기화함.
			NodeList nList = doc.getElementsByTagName("normal_policy");
			Element el = (Element) nList.item(0);
			trafficLightUpdateCycle = Integer.parseInt(el.getAttribute("updateCycle"));
			
			nList = doc.getElementsByTagName("policy");
			el = (Element) nList.item(0);
			Policy exPolicy = new Policy(el);
		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
