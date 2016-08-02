import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

import de.tudresden.sumo.cmd.Edge;
import de.tudresden.sumo.cmd.Junction;
import de.tudresden.sumo.cmd.Lane;
import de.tudresden.sumo.cmd.Route;
import de.tudresden.sumo.cmd.Simulation;
import de.tudresden.sumo.cmd.Trafficlights;
import de.tudresden.sumo.cmd.Vehicle;
import de.tudresden.ws.container.SumoLink;
import de.tudresden.ws.container.SumoLinkList;
import de.tudresden.ws.container.SumoStringList;
import it.polito.appeal.traci.SumoTraciConnection;


public class MainController {

	static String trafficLightSignal[] = {"y", "r", "g"};
	static int trafficLightUpdateCycle = 0;			// written in normal policy
	static SumoTraciConnection conn;

	public static void main(String[] args) throws Exception {

		//config file read and initiation
		Config cf = new Config();
		HashMap<String, CS> csList;

		int arrivedCar =0;
		conn = new SumoTraciConnection(cf.getSumoBinDir(), cf.getConfigDir());
		conn.addOption("step-length", "1");
		conn.runServer();

		// #### CS initiation ####
		csList = initCSs(cf, conn);

		// ####### policy initiation ######
		ArrayList<Policy> policyList = parsingPolicy(cf.getPolicyDir());
		HashMap<String, List<String>> monitoringEdges = initPolicies(policyList);			//<Policy Id, edges>
		ArrayList<String> pidOrderByPriority = orderPidPriority(policyList);			//낮은 priority --> 높은 priority 순으로 정렬된 policy id를 가지고 있는 리스트.

		// ######## variable initiation needed for simulation before simulation #########
		int SoSstate = 0;			// 0-- normal state, minus number-- SoS state before control, plus number -- SoS state after control
		int[] flagedTime = new int[policyList.size()];			// state 가 sos로 바뀐 시점을 감지하여 저장, flag의 인덱스는 policyList의 인덱스 순서와 동일.

		/*		//ambulance를 위한 변수들
		List<String> ambulanceRoute = null;
		HashMap<String, List<String>> ambulances = new HashMap<String, List<String>>();
		 */
		//vehicle의 수를 i보다 더 늘리기 위한 숫자.
		int vehicleIdx = 0;

		/*		//현재의 priority를 기억한다.. 임의의 priority 숫자(가장 높은 숫자)
		int priority = Integer.MAX_VALUE;
		Policy nowPolicy = null;

		 */

		// #### simulation start ####
		for (int i=0; i<3600; i++){
			// car generation
			int simtime = (int) conn.do_job_get(Simulation.getCurrentTime());
			vehicleIdx = generateCar(vehicleIdx, simtime);

			// normal policy apply
			if (i%trafficLightUpdateCycle ==0){		//정해진 traffic light update cycle마다
				for (Entry<String, CS> e: csList.entrySet()){
					if (getPassingNodes().contains(e.getKey()))
						continue;
					e.getValue().updateAllTrafficLight(conn, trafficLightSignal);
				}
			}

			// policyList iteration from the lowest priority (biggest value) to the highest priority (lowest value) 
			for (String pid: pidOrderByPriority){
				// getting now's policy in policyList
				Policy pol = null;
				for (Policy p: policyList){
					if (p.getId().compareTo(pid) == 0 ){
						pol = p;
						break;
					}
				}

				//checking the pol's factor is satisfied.
				boolean factorSatisfied = factorCheck(pol, monitoringEdges, csList);			// if factor is satisfied, change to true.
				System.out.println((simtime/1000) + " tick / " + pid + "\t" + factorSatisfied);
				
				// do operation if pol's factor is satisfied.
				if (factorSatisfied){
					
				}
				else{			// or not satisfied..
					
				}
			}


			
			//monitoring camera update
			for (Entry<String, CS> c: csList.entrySet()){
				c.getValue().updateCamera(conn);
			}
			
			// do operation according to each CS's determined value just before. 
			for (Entry<String, CS> e: csList.entrySet()){
				if (getPassingNodes().contains(e.getKey()))
					continue;
				conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
			}
			conn.do_timestep();
		}

		System.out.println("# Arrived car is " + arrivedCar);

		conn.close();
	}

	// return true if factor of this policy (named pol) is satisfied.
	private static boolean factorCheck(Policy pol, HashMap<String, List<String>> monitoringEdges, HashMap<String, CS> csList) {
		/* checking the pol's factor is satisfied.
		 * the case is divided as 4 cases.
		 * location_target				vehicle_target			description
		 *     all							all					do operation if the number of all vehicles on all edges is over N.
		 *     all 							ambulance			do operation if the number of ambulances on all edges is over N.
		 *     edges						all					do operation if the number of all vehicles on edges targetted is over N.
		 *     edges						ambulance			do operation if the number of ambulances on edges targetted is over N. 
		 */
		boolean satisfied = false;
		try{
			if (pol.getFactor().getLocation_target().compareToIgnoreCase("all")==0){
				//since the location is all, just getting vehicles on all edges.
				List<String> vehicles = (List<String>) conn.do_job_get(Vehicle.getIDList());
				if (pol.getFactor().getVehicle_target().compareToIgnoreCase("all")==0){
					//case all all
					satisfied = switchBySign(pol, vehicles.size());
				}
				else if (pol.getFactor().getVehicle_target().compareToIgnoreCase("ambulance")==0){			 
					//case all ambulance
					//extract ambulances from vehicles list.
					ArrayList<String> ambulances = new ArrayList<String>();
					for (String str: vehicles){
						if (str.startsWith("ambulance")){
							ambulances.add(str);
						}
					}
					
					satisfied = switchBySign(pol, ambulances.size());
				}
			}
			
			else if (pol.getFactor().getLocation_target().compareToIgnoreCase("edges")==0){
				List<String> edges = monitoringEdges.get(pol.getId());
				if (pol.getFactor().getVehicle_target().compareToIgnoreCase("all")==0){
					// case edges all
					// getting the number of vehicles on these specific monitored edges. 
					int number = 0;
					for (String str: edges){			// edges에서 하나씩 빼서
						for (Entry<String, CS> cs: csList.entrySet()){			// CS를 돌며 그 엣지를 가지고 있는게 보이면.. 그 엣지 위에 있는 차의 대수를 가지고옴.
							if (cs.getValue().getEdgeList().contains(str)){
								number += cs.getValue().getCamera().get(str);
								break;
							}
						}
					}
					// determine if the number of vehicles on these edges is over N.
					satisfied = switchBySign(pol, number);
				}
				else if (pol.getFactor().getVehicle_target().compareToIgnoreCase("ambulance")==0){
					//case edges ambulance
					// getting the number of ambulances on these specific monitored edges.
					int number = 0;
					for (String str: edges){
						List<String> vlist = (List<String>)conn.do_job_get(Edge.getLastStepVehicleIDs(str));
						for (String aid: vlist){			// count ambulance on the vehicles. 
							if (aid.startsWith("ambulance")){
								number++;
							}
						}
					}
					
					// determine if the number of ambulances on these edges is over N.
					satisfied = switchBySign(pol, number);
				}
			}
		} catch (Exception e){
			e.printStackTrace();
		}

		return satisfied;
	}


	// according to pol's vehicle number sign, determine nVehicles is satisfied.
	private static boolean switchBySign(Policy pol, int nVehicles) {
		boolean ret = false;

		switch (pol.getFactor().getVehicle_number_sign()){
		case "GE": 
		{
			if (nVehicles>=pol.getFactor().getVehicle_number())
				ret = true;
			else
				ret = false;
			break;
		}
		case "E":
		{
			if (nVehicles==pol.getFactor().getVehicle_number())
				ret = true;
			else
				ret = false;
			break;
		}
		case "G":
		{
			if (nVehicles>pol.getFactor().getVehicle_number())
				ret = true;
			else
				ret = false;
			break;
		}
		case "LE":
		{
			if (nVehicles<=pol.getFactor().getVehicle_number())
				ret = true;
			else
				ret = false;
			break;
		}
		case "L":
		{
			if (nVehicles<pol.getFactor().getVehicle_number())
				ret = true;
			else
				ret = false;
			break;
		}
		default:
		{
			if (nVehicles>=pol.getFactor().getVehicle_number())
				ret = true;
			else
				ret = false;
			break;
		}
		}

		return ret;
	}

	//return the policy ids ordered by descending order.
	private static ArrayList<String> orderPidPriority(ArrayList<Policy> policyList) {

		ArrayList<String> pidOrdered = new ArrayList<String>();
		ArrayList<Policy> tempPolicies = new ArrayList<Policy>();

		// copy policyList to pidOrdered and descending sort... (start by the lowest priority)
		for (Policy p: policyList) tempPolicies.add(p);
		Collections.sort(tempPolicies, new Comparator<Policy>(){
			public int compare (Policy a, Policy b){
				return b.getPriority() - a.getPriority();
			}
		});

		// extract id
		for (Policy p: tempPolicies) pidOrdered.add(p.getId());

		return pidOrdered;
	}


	//generate car.
	private static int generateCar(int vehicleIdx, int simtime) {

		//차 랜덤생성(확률)
		int randNum = (int)(Math.random() * 500);
		try{
			if (randNum < 350){
				for (int j=0; j<10; j++)
					conn.do_job_set(Vehicle.add("rush1"+vehicleIdx++, "car", "rush1", simtime, 0, 0.0, (byte) 0));
			}
			else if (randNum >= 350 && randNum <400){
				for (int j=0; j<5; j++)
					conn.do_job_set(Vehicle.add("genr"+vehicleIdx++, "car", "genr1", simtime, 0, 0.0, (byte) 0));
			}
			else if (randNum >= 400 && randNum <450){
				for (int j=0; j<5; j++)
					conn.do_job_set(Vehicle.add("genr"+vehicleIdx++, "car", "genr2", simtime, 0, 0.0, (byte) 0));
			}
			else if (randNum >= 450 && randNum <499){
				for (int j=0; j<5; j++)
					conn.do_job_set(Vehicle.add("genr"+vehicleIdx++, "car", "genr3", simtime, 0, 0.0, (byte) 0));
			}
			else if (randNum >= 499 && randNum <500 /*&& ambulancePolicyExist*/){
				conn.do_job_set(Vehicle.add("ambulance"+vehicleIdx, "ambulance", "ambul1", simtime, 0, 0.0, (byte) 0));
				vehicleIdx++;
			}
		}catch (Exception e){
			e.printStackTrace();
		}

		return vehicleIdx;
	}


	//Policy initiation
	private static HashMap<String, List<String>> initPolicies(ArrayList<Policy> policyList) {
		//policy에 해당하는 edges를 넣는다. 본 list는 policy factor의 location이 edges인 것에 대하여 하나씩 있어야 한다. 
		HashMap<String, List<String>> monitoringEdges = new HashMap<String, List<String>>();
		for (Policy p: policyList){
			//policy/factor/location/target=edges 인 경우.
			if (p.getFactor().getLocation_target().compareTo("edges")==0){
				List<String> tempEdgesList = new ArrayList<String>();
				for (String edg: p.getFactor().getEdges()){
					tempEdgesList.add(edg);
				}
				monitoringEdges.put(p.getId(), tempEdgesList);
			}

			else if (p.getFactor().getLocation_target().compareTo("all")==0){
				monitoringEdges.put(p.getId(), null);
			}

		}

		return monitoringEdges;
	}

	//CS(Camera, traffic light) initiation
	private static HashMap<String, CS> initCSs(Config cf, SumoTraciConnection conn) {
		// TODO Auto-generated method stub
		HashMap<String, CS> csList = new HashMap<String, CS>();
		BufferedReader br;
		//#### initiation of CS-monitoring camera
		String line = "";

		try {
			br = new BufferedReader(new FileReader(new File(cf.getRelEdgesFileDir())));
			while((line = br.readLine())!=null){
				String[] split = line.split("\\t");
				CS cs = new CS(split[0]);

				for (int i=1; i<split.length; i++){
					cs.addEdge(split[i]);
					cs.initCamera();
				}
				csList.put(cs.getLocation(), cs);
			}
			br.close();

			//#### initiation of CS-traffic light -- traffic light에 의해 control되는 link들을 "순서대로" tlight에 집어넣음
			//순서가 지켜지지 않을 시 tlight가 꼬여서 control하는데 문제가 생김.
			for (Entry<String, CS> cs: csList.entrySet()){
				if (getPassingNodes().contains(cs.getKey()))
					continue;
				//randomly assign the links of traffic lights
				SumoLinkList sll = (SumoLinkList)conn.do_job_get(Trafficlights.getControlledLinks(cs.getKey()));
				for (SumoLink link: sll){
					String linkname = link.from.substring(0, link.from.length()-2) +"@" + link.to.substring(0, link.to.length()-2);
					int rand = new Random().nextInt(3);
					cs.getValue().addTLight(linkname, trafficLightSignal[rand]);
				}
				cs.getValue().initTLight();
			}

			// #### assign traffic light initially
			for (Entry<String, CS> e: csList.entrySet()){
				if (getPassingNodes().contains(e.getKey()))
					continue;

				conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return csList;
	}

	private static ArrayList<Policy> parsingPolicy(String policyDir) {
		// TODO Auto-generated method stub
		ArrayList<Policy> policyList = null;
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(policyDir));

			//normal_policy가 가지고 있는 non-traffic jam 일 때의 신호등 update cycle을 가장 먼저 초기화함.
			NodeList nList = doc.getElementsByTagName("normal_policy");
			Element el = (Element) nList.item(0);
			trafficLightUpdateCycle = Integer.parseInt(el.getAttribute("updateCycle"));

			nList = doc.getElementsByTagName("policy");
			policyList = new ArrayList<Policy>();
			for (int i=0; i<nList.getLength(); i++){
				Element n = (Element)nList.item(i);
				Policy tmpPol = new Policy(n);
				policyList.add(tmpPol);
			}
		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return policyList;
	}

	//route가 포함하는 노드를 받아옴. 시작 노드와 end 노드는 제외됨.
	private static ArrayList<String> getNodesFromRoutes(List<String> routes, Boolean removeFinalNodes){
		Config cf = new Config();
		String edgeDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_v"+cf.getMapVersion()+".edg.xml";
		ArrayList<String> nodes = new ArrayList<String>();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		try {
			db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(edgeDir));
			//일단 노드를 뽑아내서 hashmap에 넣어둠. 
			NodeList nList = doc.getElementsByTagName("edge");
			for (String ru: routes){
				for (int i=0; i<nList.getLength(); i++){
					Element n = (Element)nList.item(i);
					if (n.getAttribute("id").compareTo(ru) == 0)
						nodes.add(n.getAttribute("to"));
				}
			}
			if (removeFinalNodes){
				if (nodes.size()>1)
					nodes.remove(nodes.size()-1);
			}

		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return nodes;
	}

	private static ArrayList<String> getPassingNodes(){
		Config cf = new Config();
		String nodeDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_v"+cf.getMapVersion()+".nod.xml";
		ArrayList<String> nodes = new ArrayList<String>();

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		try {
			db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(nodeDir));
			//일단 노드를 뽑아내서 hashmap에 넣어둠. 
			NodeList nList = doc.getElementsByTagName("node");

			for (int i=0; i<nList.getLength(); i++){
				Element n = (Element)nList.item(i);
				if(n.getAttribute("type").compareTo("traffic_light")!=0)
					nodes.add(n.getAttribute("id"));
			}

		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return nodes;
	}

	//Edge명을 받았을 때 edge의 방향(to)를 return 하는 메소드.
	private static String getToOfEdge(String edge){
		Config cf = new Config();
		String edgeDir = "C:/Users/WonKyung/git/KCC2016/DJproject/DJMap_v"+cf.getMapVersion()+".edg.xml";

		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db;
		try {
			db = dbf.newDocumentBuilder();
			Document doc = db.parse(new File(edgeDir));
			//일단 노드를 뽑아내서 hashmap에 넣어둠. 
			NodeList nList = doc.getElementsByTagName("edge");
			for (int i=0; i<nList.getLength(); i++){
				Element e = (Element)nList.item(i);
				if (e.getAttribute("id").compareTo(edge)==0)
					return e.getAttribute("to");
			}


		} catch (ParserConfigurationException | SAXException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

}
