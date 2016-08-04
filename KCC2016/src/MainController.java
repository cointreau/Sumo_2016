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
		ArrayList<String> pidOrderByPriority = orderPidPriority(policyList);			//낮은 priority(나중) --> 높은 priority(우선) 순으로 정렬된 policy id를 가지고 있는 리스트.
		int vehicleIdx = 0;				// generate하는 차량의 id를 구분하기 위한 숫자.

		HashMap<String, Timeflag> flagTime = new HashMap<String, Timeflag>();			//time flag의 map을 policy 개수만큼 만듬.
		for (Policy p: policyList) flagTime.put(p.getId(), new Timeflag(p.getId(), p.getPriority()));

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
					
					//만약 policy의 적용을 받고 있으면 pass.
					if (e.getValue().getPOP() == Integer.MAX_VALUE)
						e.getValue().updateAllTrafficLight(conn);
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
				if (!factorSatisfied){			// not satisfied.
					Timeflag flag = flagTime.get(pol.getId());			//get the timeflag of now policy
					if (flag.getState().compareTo("monitor")==0){		//만약 모니터 중이었다면 정상화함.
						flag.setState("none");
						flag.setstartTime(-1);
						flag.setleftTime(-1);
						System.out.println(pol.getId()+" back to original state");
					}
				}
				else{			// satisfied..
					Timeflag flag = flagTime.get(pol.getId());			//get the timeflag of now policy
					if (flag.getState().compareTo("none")==0){			
						/* if the state is none.. 
						 * start monitoring.
						 */
						System.out.println(pol.getId()+" monitoring start");
						flag.setState("monitor");
						flag.setstartTime(i);
						flag.setleftTime(pol.getFactor().getTime()-1);
						if (flag.getLeftTime()==-1)	flag.setleftTime(0);
					}
					else if (flag.getState().compareTo("monitor")==0 && flag.getLeftTime() != 0){			
						/* if the state is monitor and still has lefttime, 
						 * keep monitoring.
						 */ 
						flag.setleftTime(flag.getLeftTime()-1);
						System.out.println(flag.getLeftTime());
					}
					else if (flag.getState().compareTo("monitor")==0 && flag.getLeftTime() == 0){			
						/* if the state is monitor and lefttime is over,
						 * start operation.
						 */ 

						//operation 을 하게끔 함.
						System.out.println("operation start.");
						csList = doOperation(pol, csList);
						//flag 수정.
						flag.setState("applied");
						flag.setstartTime(i);
						flag.setleftTime(pol.getOperation().getSustainTime()-1);
						if (flag.getLeftTime()==-1)	flag.setleftTime(0);
					}
					else if (flag.getState().compareTo("applied")==0 && flag.getLeftTime() != 0){			
						/* if the state is applied and still has lefttime,
						 * keep operation.
						 */ 
						System.out.println("under operation");
						flag.setleftTime(flag.getLeftTime()-1);
					}
					else if (flag.getState().compareTo("applied")==0 && flag.getLeftTime() == 0){			
						/* if the state is applied and lefttime is over,
						 * return to none state.
						 */ 
						System.out.println("operation end.");
						csList = endOperation(pol, csList);
						flag.setState("none");
						flag.setstartTime(-1);
						flag.setleftTime(-1);
					}

					//after all, update the flagTime with this modified flag.
					flagTime.put(pol.getId(), flag);
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

	private static HashMap<String, CS> endOperation(Policy pol, HashMap<String, CS> csList) {
		/*
		 * list중 pName의 이름이 pol과 동일한 애들을 전부 정상화 시킴.
		 */

		try{
			for (Entry<String, CS> cs: csList.entrySet()){
				//pop의 이름이 동일한 cs들만 정상화를 시켜야함. 그러나 낮은 순위 operation 발동 중에 높은 순위가 끝나면 겹치는 구간의 경우 원래대로 돌아가버리기 때문에 낮은 순위의 operation발동을 한번 더
				//보는 것이 필요하지 않을까 싶다-
				if (cs.getValue().getpName().compareTo(pol.getId())==0){
					cs.getValue().updateAllTrafficLight(conn);
					cs.getValue().setPOP(Integer.MAX_VALUE);
					cs.getValue().setpName("");
				}
			}
		}catch (Exception e){
			e.printStackTrace();
		}

		return csList;
	}

	private static HashMap<String, CS> doOperation(Policy pol, HashMap<String, CS> csList) {
		//location을 일단 한정해야 함.
		HashMap<String, CS> targetCSs = new HashMap<String, CS>();
		List<ArrayList<String>> targetEdges = new ArrayList<ArrayList<String>>();
		if (pol.getOperation().getLocation_target().compareTo("all")==0){
			//모든 CS가 대상이므로 그냥 카피.
			for (Entry<String, CS> cs: csList.entrySet())	targetCSs.put(cs.getKey(), cs.getValue());
		}
		else if (pol.getOperation().getLocation_target().compareTo("edges")==0){
			//edges에 구성된 CS들로만 한정.
			for (String edge: pol.getOperation().getEdges()){
				String nodeName = getToOfEdge(edge);
				targetCSs.put(nodeName, csList.get(nodeName));
			}
			ArrayList<String> tmpedges = new ArrayList<String>();
			for (String eg: pol.getOperation().getEdges())	tmpedges.add(eg);
			targetEdges.add(tmpedges);
		}
		else if (pol.getOperation().getLocation_target().compareTo("follow-all")==0){
			//pol의 location_target의 route 정보를 가져와야함. 이 때 앰뷸런스의 루트가 각각 다를 경우를 고려해야 함.
			//앰뷸런스가 나타나야지만 이 조건이 발동되므로, vehicle에서 ambulance의 id를 모두 찾아와서 하는 것이 좋을 듯 함.
			HashMap<String, ArrayList<String>> ambulances = new HashMap<String, ArrayList<String>>();
			try {
				//ambulance들의 루트에 해당되는 노드들을 가져옴.
				List<String> vehicles = (List<String>)conn.do_job_get(Vehicle.getIDList());
				for (String vid: vehicles){
					if (vid.startsWith("ambulance")){
						//current와 비교하여 남은 노드들만을 추가해야함. 그러므로 routeAmbl에서 지나간 노드들은 다 빼버려야 함.
						List<String> routeAmbl = (List<String>) conn.do_job_get(Vehicle.getRoute(vid));
						targetEdges.add(new ArrayList<String>(routeAmbl));
						String locationAmbulance = (String)conn.do_job_get(Vehicle.getRoadID(vid));		//현재의 location, 
						if (locationAmbulance.contains("_")){
							// 현재 위치가 junction으로 잡히는 경우임. 이 경우엔 _뒤에 있는 것을 떼버리고, 
							locationAmbulance = locationAmbulance.substring(1, locationAmbulance.indexOf("_"));
							//해당 노드의 edgeList중에, 앰뷸런스와 겹치는 엣지를 넣어놓움
							for (String edge: csList.get(locationAmbulance).getEdgeList()){
								if (routeAmbl.contains(edge)){
									locationAmbulance = edge;
									break;
								}
							}
						}

						int loop = routeAmbl.indexOf(locationAmbulance);
						for (int i=0; i<loop; i++){		//지나간 노드의 제거
							routeAmbl.remove(i);
						}

						ambulances.put(vid, getNodesFromRoutes(routeAmbl, true));
					}
				}

				// 가져온 노드들을 찾아서 csList에서 찾아서 넣어둠.
				for (Entry<String, ArrayList<String>> ent: ambulances.entrySet()){
					for (String node: ent.getValue()){
						targetCSs.put(node, csList.get(node));
					}
				}
				// 엣지들도 같이 넣어놓음.

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		else if (pol.getOperation().getLocation_target().contains("follow-current")){
			//ambulance들의 루트에 해당되는 노드들을 가져오고, current의 숫자만큼을 또 추가로 넣어두어야함.
			HashMap<String, ArrayList<String>> ambulances = new HashMap<String, ArrayList<String>>();
			try {
				//ambulance들의 루트에 해당되는 노드들을 가져옴.
				List<String> vehicles = (List<String>)conn.do_job_get(Vehicle.getIDList());
				for (String vid: vehicles){
					if (vid.startsWith("ambulance")){
						//current와 비교하여 남은 노드들만을 추가해야함. 그러므로 routeAmbl에서 지나간 노드들은 다 빼버려야 함.
						List<String> routeAmbl = (List<String>) conn.do_job_get(Vehicle.getRoute(vid));
						targetEdges.add(new ArrayList<String>(routeAmbl));
						String locationAmbulance = (String)conn.do_job_get(Vehicle.getRoadID(vid));		//현재의 location, 
						if (locationAmbulance.contains("_")){
							// 현재 위치가 junction으로 잡히는 경우임. 이 경우엔 _뒤에 있는 것을 떼버리고, 
							locationAmbulance = locationAmbulance.substring(1, locationAmbulance.indexOf("_"));
							//해당 노드의 edgeList중에, 앰뷸런스와 겹치는 엣지를 넣어놓움
							for (String edge: csList.get(locationAmbulance).getEdgeList()){
								if (routeAmbl.contains(edge)){
									locationAmbulance = edge;
									break;
								}
							}
						}

						int loop = routeAmbl.indexOf(locationAmbulance);
						for (int i=0; i<loop; i++){		//지나간 노드도 제거하고
							routeAmbl.remove(0);
						}

						List<String> leftovers = new ArrayList<String>();
						//제거된데서, 딱 n개까지만 남겨놓아야함.
						for (int i=0; i<=pol.getOperation().getFollowCurrentEdgesNumber(); i++){
							leftovers.add(routeAmbl.get(i));
						}

						ambulances.put(vid, getNodesFromRoutes(leftovers, true));
					}
				}
				// 가져온 노드들을 찾아서 csList에서 찾아서 넣어둠.
				for (Entry<String, ArrayList<String>> ent: ambulances.entrySet()){
					for (String node: ent.getValue()){
						targetCSs.put(node, csList.get(node));
					}
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}



		//주어진 location에 operation을 적용한다. 만약 null일 경우 return하게 하며, passing nodes일 경우에는 pass하게 한다. 
		//또한 priority를 체크하여 현재 CS의 priority가 낮을 때만 적용하도록 하여야 한다.
		if (targetCSs.isEmpty())
			return csList;

		try{
			for (Entry<String, CS> target: targetCSs.entrySet()){
				if (getPassingNodes().contains(target.getKey()))
					continue;

				//pop적용 전 해당 CS의 priority 체크
				if (!(pol.getPriority() < target.getValue().getPOP()))		//낮아야 발동. 높으면 continue;
					continue;

				// CS 단위의 처리
				if (pol.getOperation().getLocation_target().compareTo("all")==0){
					for (TLight t: target.getValue().gettlightMap()){
						target.getValue().updateTrafficLight(conn, t.getKey(), pol.getOperation().getLight());
					}
					target.getValue().setPOP(pol.getPriority());
					target.getValue().setpName(pol.getId());
				}

				//edge 단위의 처리
				else{
					for (String edges: target.getValue().getEdgeList()){			//target CS의 모든 엣지들에 대해
						//targetEdges에 포함된 곳이 있는지 체크
						boolean targetHasThisEdge = false;
						for (ArrayList<String> targetEdgesElm: targetEdges){
							if (targetEdgesElm.contains(edges)){		targetHasThisEdge=true;	break;}
						}
						if (targetHasThisEdge && getToOfEdge(edges).compareTo(target.getKey())==0){		//만약 이 엣지가 target에 포함된 엣지이며 나가는 엣지라면
							for (TLight t: target.getValue().gettlightMap()){
								if (t.getKey().startsWith(edges)){
									target.getValue().updateTrafficLight(conn, t.getKey(), pol.getOperation().getLight());
								}
								else{
									if (pol.getOperation().getLight().compareTo("g")==0)	target.getValue().updateTrafficLight(conn, t.getKey(), "r");
									else if (pol.getOperation().getLight().compareTo("r")==0)	target.getValue().updateTrafficLight(conn, t.getKey(), "g");
								}
							}
							target.getValue().setPOP(pol.getPriority());
							target.getValue().setpName(pol.getId());
						}
					}
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}

		//csList에 업데이트 해야함.
		for (Entry<String, CS> target: targetCSs.entrySet()){
			csList.put(target.getKey(), target.getValue());
		}

		return csList;
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
					if (satisfied) 
						System.out.println("# of car"+number);
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
/*			else if (randNum >= 450 && randNum <499){
				for (int j=0; j<5; j++)
					conn.do_job_set(Vehicle.add("genr"+vehicleIdx++, "car", "genr3", simtime, 0, 0.0, (byte) 0));
			}*/
			else if (randNum >= 450 && randNum <500){
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
					String signal[] = {"y", "r", "g"};
					int rand = new Random().nextInt(3);
					cs.getValue().addTLight(linkname, signal[rand]);
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
