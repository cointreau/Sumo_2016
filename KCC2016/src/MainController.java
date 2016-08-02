import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
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
	static int changeToTrafficJamState;
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

		// ######## variable initiation needed for simulation before simulation #########
		int SoSstate = 0;			// 0-- normal state, minus number-- SoS state before control, plus number -- SoS state after control
		int[] flagedTime = new int[policyList.size()];			// state 가 sos로 바뀐 시점을 감지하여 저장, flag의 인덱스는 policyList의 인덱스 순서와 동일.

		//ambulance를 위한 변수들
		List<String> ambulanceRoute = null;
		HashMap<String, List<String>> ambulances = new HashMap<String, List<String>>();

		//vehicle의 수를 i보다 더 늘리기 위한 숫자.
		int vehicleIdx = 0;

		//현재의 priority를 기억한다.. 임의의 priority 숫자(가장 높은 숫자)
		int priority = Integer.MAX_VALUE;
		Policy nowPolicy = null;



		// #### simulation start ####
		for (int i=0; i<3600; i++){

			int simtime = (int) conn.do_job_get(Simulation.getCurrentTime());
			vehicleIdx = generateCar(vehicleIdx, simtime);

			//=============================================== normal policy ========================================================
			//traffic light 주기적 업데이트. normal state.
			if (i%trafficLightUpdateCycle ==0 && SoSstate==0){		//정해진 traffic light update cycle마다
				for (Entry<String, CS> e: csList.entrySet()){
					if (getPassingNodes().contains(e.getKey()))
						continue;
					e.getValue().updateAllTrafficLight(conn, trafficLightSignal);
					conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
				}
			}

			//traffic jam 발생 체크를 위하여 현재 도로의 상황을 카메라를 통해 받아옴.
			HashMap<String, Integer> nCar = new HashMap<String, Integer>();
			for (Policy p: policyList){
				//만약 edges라면
				if (p.getFactor().getLocation_target().compareTo("edges")==0){
					int number = 0;
					//monitoringEdges 맵에서 해당 해당 policy에 해당하는 edges를 가지고와서
					for (String str: monitoringEdges.get(p.getId())){
						//CS를 돌며 해당 edge를 가지고 있는게 보이면 그 edge위에 있는 차를 가지고옴
						for (Entry<String, CS> cs: csList.entrySet()){
							if (cs.getValue().getEdgeList().contains(str)){
								number += cs.getValue().getCamera().get(str);
								break;
							}
						}
						nCar.put(p.getId(), number);
					}
				}
			}


			//=============================================== policy2 ambulance ========================================================			
			//ambulance가 나타났는지 체크함(factor에 비교하여 체크함)
			List<String> vehicles = (List<String>) conn.do_job_get(Vehicle.getIDList());
			if (SoSstate != 2 /*&& SoSstate != -2*/ /*&& ambulancePolicyExist*/){ 

				//policyList에서 ambulance에 해당하는 policy을 뽑아옴
				Policy ambulPolicy=null;
				for (Policy p: policyList){
					if (p.getId().contains("ambulance")){
						ambulPolicy = p;
						break;
					}
				}

				//이 state가 새로이 시작하게 되므로 amblanceList 초기화
				ambulances.clear();

				//ambulance의 id와 그의 route를 각각 ambulances에 저장함.
				for (String str: vehicles){
					if (str.startsWith("ambul")){
						ambulanceRoute = (List<String>)conn.do_job_get(Vehicle.getRoute(str));
						ambulances.put(str, ambulanceRoute);
					}
				}


				if (ambulPolicy.getPriority() < priority) {					
					//policy의 sign에 따라 다르게 SoSstate를 규정할 수 있게 되므로 추가해야함
					switch(ambulPolicy.getFactor().getVehicle_number_sign()){
					case "GE": 
						if (ambulances.size()>=ambulPolicy.getFactor().getVehicle_number()){
							SoSstate = -2;
							//							priority = ambulPolicy.getPriority();
						}
						else{
							flagedTime[policyList.indexOf(ambulPolicy)] = 0;
							if (SoSstate == -2)
								SoSstate = 0;
						}
						break;
					case "E":
						if (ambulances.size()==ambulPolicy.getFactor().getVehicle_number()){
							SoSstate = -2;
							//							priority = ambulPolicy.getPriority();
						}
						else{
							flagedTime[policyList.indexOf(ambulPolicy)] = 0;
							if (SoSstate == -2)
								SoSstate = 0;
						}
						break;
					case "G":
						if (ambulances.size()>ambulPolicy.getFactor().getVehicle_number()){
							SoSstate = -2;
							//							priority = ambulPolicy.getPriority();
						}
						else{
							flagedTime[policyList.indexOf(ambulPolicy)] = 0;
							if (SoSstate == -2)
								SoSstate = 0;
						}
						break;
					case "LE":
						if (ambulances.size()<=ambulPolicy.getFactor().getVehicle_number()){
							SoSstate = -2;
							//							priority = ambulPolicy.getPriority();
						}
						else{
							flagedTime[policyList.indexOf(ambulPolicy)] = 0;
							if (SoSstate == -2)
								SoSstate = 0;
						}
						break;
					case "L":
						if (ambulances.size()<ambulPolicy.getFactor().getVehicle_number()){
							SoSstate = -2;
							//							priority = ambulPolicy.getPriority();
						}
						else{
							flagedTime[policyList.indexOf(ambulPolicy)] = 0;
							if (SoSstate == -2)
								SoSstate = 0;
						}
						break;
					default:
						if (ambulances.size()>=ambulPolicy.getFactor().getVehicle_number()){
							SoSstate = -2;
							//							priority = ambulPolicy.getPriority();
						}
						else{
							flagedTime[policyList.indexOf(ambulPolicy)] = 0;
							if (SoSstate == -2)
								SoSstate = 0;
						}
						break;
					}
				}
			}

			// 만약 ambulance가 도로상에 나타났을 경우, 처리를 위해 현재 시간을 flag로 못박고 SoS state를 policy2로 업데이트 함.
			if (SoSstate == -2){
				int time=0;
				int index=0;
				int prior = 0;
				for (Policy p: policyList){
					if (p.getId().contains("ambulance")){
						index = policyList.indexOf(p);
						time = p.getFactor().getTime();
						priority = p.getPriority();
						break;
					}
				}

				//time을 넘어섰는지 확인 후 SoSstate를 변경한다.
				if (flagedTime[index] == 0)
					flagedTime[index] = i;
				else if (flagedTime[index] != 0 && i>=flagedTime[index] + time-1) {
					SoSstate = 2;
					flagedTime[index] = i;
					priority = prior;
					System.out.println("-------------------------------------------------Ambulance appears!!");
				}
			}

			//policy2의 적용 -- 기존에 policy1의 발동보다 상위의 priority.
			if (SoSstate == 2){
				//기존의 신호등을 원래대로 돌린 뒤
				//policy를 가지고옴
				Policy ambulPolicy=null;
				for (Policy pol: policyList){
					if (pol.getId().contains("ambulance")){
						ambulPolicy = pol;
						break;
					}
				}
				nowPolicy = ambulPolicy;

				//만약 미해당되는 CS라면
				for (Entry<String, CS> e: csList.entrySet()){
					if (getPassingNodes().contains(e.getKey()))
						continue;

					for (Entry<String, List<String>> ambul: ambulances.entrySet()){
						//사라진 ambulance가 아직 처리되기 전이라면... 
						if (!((List<String>) conn.do_job_get(Vehicle.getIDList())).contains(ambul.getKey()))
							continue;

						if (i%trafficLightUpdateCycle == 0)
							e.getValue().updateAllTrafficLight(conn, trafficLightSignal);		//기존의 SoSstate==1이었으면 red로 일부 바뀐 신호등이 있기 때문에 이렇게 해주어야함

						String locationAmbulance = (String)conn.do_job_get(Vehicle.getRoadID(ambul.getKey()));
						String locationAmbulanceTo = getToOfEdge(locationAmbulance);

						//만약 현재의 location이 edge가 아닌 junction으로 잡히는 경우(가 존재함, SUMO 상의 오류..) 패스하게 함.
						if (locationAmbulanceTo == null){
							break;
						}

						ArrayList<String> ambulanceRouteNodes = getNodesFromRoutes(ambulanceRoute, true);

						//현재 ambulance route 상의 CS이며 & ambulance가 위치한 앞 x구간 이내의 CS이면(위치 구간=1, 또는 x구간)  
						if (ambulPolicy.getOperation().getLocation_target().contains("follow-current")){
							if (ambulanceRouteNodes.contains(e.getKey()) && 
									(ambulanceRouteNodes.indexOf(locationAmbulanceTo) + ambulPolicy.getOperation().getFollowCurrentEdgesNumber() > ambulanceRouteNodes.indexOf(e.getKey())) && 
									(ambulanceRouteNodes.indexOf(locationAmbulanceTo) <= ambulanceRouteNodes.indexOf(e.getKey()))){
								for (String edge: e.getValue().getEdgeList()){
									if (ambulanceRoute.contains(edge) && getToOfEdge(edge).compareTo(e.getKey())==0){		// 해당 CS가 포함한 모든 엣지에 대해 이 엣지가 앰뷸런스 루트에 포함된 엣지이며 이 엣지의 to가 현재 CS의 키이면 (이후 조건은 CS의 키 방향으로 진입하는 앰뷸런스 루트 상의 엣지가 if문 안으로 들어가는 것을 막기 위함)
										for (TLight t: e.getValue().gettlightMap()){
											if (t.getKey().startsWith(edge)){
												e.getValue().updateTrafficLight(conn, t.getKey(), policyList.get(1).getOperation().getLight());		//해당 edge에서 빠져나가는 노드는 모두 g로 바꾸어줌.
											}
											else
												e.getValue().updateTrafficLight(conn, t.getKey(), "r");
										}
									}
								}
								conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
							}
						}

						//policy2의 신호체계 -- 앰뷸런스의 모든 루트를 전부 g로 바꾸며 지나간 자리는 건드리지 않음.. 
						else if (ambulPolicy.getOperation().getLocation_target().contains("follow-all")){
							//이미 현재 앰뷸런스의 위치가 ambul route상에서 지나간 edge라면 아래의 동작을 수행하지 않고 continue하게 함.
							if (ambulanceRouteNodes.contains(e.getKey()) && ambulanceRouteNodes.indexOf(locationAmbulanceTo) > ambulanceRouteNodes.indexOf(e.getKey()))
								continue;

							if (ambulanceRouteNodes.contains(e.getKey()))
								e.getValue().updateAllTrafficLightToRed(conn);

							for (String tl: ambul.getValue()){
								for (TLight t: e.getValue().gettlightMap()){
									if (t.getKey().startsWith(tl)){		//만약 이 edge 명으로 시작하면,
										e.getValue().updateTrafficLight(conn, t.getKey(), policyList.get(1).getOperation().getLight());		//해당 edge에서 빠져나가는 노드는 모두 g로 바꾸어줌.
									}
								}
								conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
							}
						}

						//edges일 경우의 수행.
						else if (ambulPolicy.getOperation().getLocation_target().contains("edges")){
							//일단 traffic jam에 해당하는 policy을 가져옴.

							//해당 길목(rush1)에 해당하는 신호등들의 신호만 g로 바꿈. 이부분 코드 수정 필요? rush1에 해당하는 노드를 하드 코딩 말고 뭔가 다른 방법으로 알아내어야 함
							if (getNodesFromRoutes(ambulPolicy.getOperation().getEdges(), false).contains(e.getKey())){
								e.getValue().updateAllTrafficLightToRed(conn);

								for (String tl: ambulPolicy.getOperation().getEdges()){
									for (TLight t: e.getValue().gettlightMap()){
										if (t.getKey().startsWith(tl)){		//만약 이 edge 명으로 시작하면,
											e.getValue().updateTrafficLight(conn, t.getKey(), ambulPolicy.getOperation().getLight());		//해당 edge에서 빠져나가는 노드는 모두 g로 바꾸어줌.
										}
									}
									conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
								}
							}
						}
					}
				}
			}


			//=============================================== policy 1 -- traffic jam========================================================

			//policy1의 적용. 우선순위가 가장 낮으므로 어떠한 상황도 아닌 normal 상황에서만 발동하게 된다
			if (SoSstate!=1 /*&& SoSstate!=-1*/ /*&& trafficjamPolicyExist*/){		//SoS 상황 설정 -- 해당 차로에 x대가 넘는 차량이 몰려있을 경우를 SoS 상황으로 설정함.
				//일단 traffic jam에 해당하는 policy을 가져옴.
				Policy jamPolicy=null;
				for (Policy p: policyList){
					if (p.getId().contains("traffic_jam")){
						jamPolicy = p;
						break;
					}
				}

				if (jamPolicy.getPriority() < priority){
					//policy1의 sign에 따라 SoSstate를 trigger함.
					switch(jamPolicy.getFactor().getVehicle_number_sign()){
					case "GE": 
						if (nCar.get(jamPolicy.getId())>=changeToTrafficJamState){
							SoSstate = -1;
						}
						else{
							flagedTime[policyList.indexOf(jamPolicy)] = 0;
							if (SoSstate == -1)
								SoSstate = 0;
						}
						break;
					case "E":
						if (nCar.get(jamPolicy.getId())==changeToTrafficJamState){
							SoSstate = -1;
						}
						else{
							flagedTime[policyList.indexOf(jamPolicy)] = 0;
							if (SoSstate == -1)
								SoSstate = 0;
						}
						break;
					case "G":
						if (nCar.get(jamPolicy.getId())>changeToTrafficJamState){
							SoSstate = -1;
						}
						else{
							flagedTime[policyList.indexOf(jamPolicy)] = 0;
							if (SoSstate == -1)
								SoSstate = 0;
						}
						break;
					case "LE":
						if (nCar.get(jamPolicy.getId())<=changeToTrafficJamState){
							SoSstate = -1;
						}
						else{
							flagedTime[policyList.indexOf(jamPolicy)] = 0;
							if (SoSstate == -1)
								SoSstate = 0;
						}
						break;
					case "L":
						if (nCar.get(jamPolicy.getId())<changeToTrafficJamState){
							SoSstate = -1;
						}
						else{
							flagedTime[policyList.indexOf(jamPolicy)] = 0;
							if (SoSstate == -1)
								SoSstate = 0;
						}
						break;
					default:
						if (nCar.get(jamPolicy.getId())>=changeToTrafficJamState){
							SoSstate = -1;
						}
						else{
							flagedTime[policyList.indexOf(jamPolicy)] = 0;
							if (SoSstate == -1)
								SoSstate = 0;
						}
						break;
					}
				}

			}

			System.out.println((simtime/1000)+" tick / "+nCar.get("emergency_by_traffic_jam") +" cars on the monitored road now in state = " + SoSstate);

			//SoS 상황시 대응 (policy1)
			if (SoSstate == -1){
				//모든 신호등을 r로 바꾼 뒤, 

				Policy jamPolicy=null;
				for (Policy p: policyList){
					if (p.getId().contains("traffic_jam")){
						jamPolicy = p;
						break;
					}
				}

				if (flagedTime[policyList.indexOf(jamPolicy)] == 0)
					flagedTime[policyList.indexOf(jamPolicy)] = i;
				else if (flagedTime[policyList.indexOf(jamPolicy)] != 0 && i>=flagedTime[policyList.indexOf(jamPolicy)] + jamPolicy.getFactor().getTime()-1){
					SoSstate = 1;
					priority = jamPolicy.getPriority();
					flagedTime[policyList.indexOf(jamPolicy)] = i;
					System.out.println("-----------------------------------------------SOS!!!!");
				}
			}

			if (SoSstate == 1){
				for (Entry<String, CS> e: csList.entrySet()){
					if (getPassingNodes().contains(e.getKey()))
						continue;

					//일단 traffic jam에 해당하는 policy을 가져옴.
					Policy jamPolicy=null;
					for (Policy p: policyList){
						if (p.getId().contains("traffic_jam")){
							jamPolicy = p;
							break;
						}
					}
					nowPolicy = jamPolicy;

					//해당 길목(rush1)에 해당하는 신호등들의 신호만 g로 바꿈. 이부분 코드 수정 필요? rush1에 해당하는 노드를 하드 코딩 말고 뭔가 다른 방법으로 알아내어야 함
					if (getNodesFromRoutes(jamPolicy.getOperation().getEdges(), false).contains(e.getKey())){
						e.getValue().updateAllTrafficLightToRed(conn);

						for (String tl: jamPolicy.getOperation().getEdges()){
							for (TLight t: e.getValue().gettlightMap()){
								if (t.getKey().startsWith(tl)){		//만약 이 edge 명으로 시작하면,
									e.getValue().updateTrafficLight(conn, t.getKey(), jamPolicy.getOperation().getLight());		//해당 edge에서 빠져나가는 노드는 모두 g로 바꾸어줌.
								}
							}
							//							System.out.println("??????????"+SoSstate);
							conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
						}
					}
				}
			}

			//최소 15tick 후, flag를 0으로 바꿔줌.
			int policyKeepingTime=0;
			if (SoSstate > 0){
				//				policyKeepingTime = policyList.get(SoSstate-1).getOperation().getSustainTime();
				policyKeepingTime = nowPolicy.getOperation().getSustainTime();
				//				System.out.println(nowPolicy.getId());
			}
			if (nowPolicy != null){
				if ((flagedTime[policyList.indexOf(nowPolicy)] +policyKeepingTime <= i && SoSstate == 1) || (flagedTime[policyList.indexOf(nowPolicy)] +policyKeepingTime <= i && SoSstate == 2)){
					//					System.out.println("##### " + policyKeepingTime + " @@@ "+flagedTime[policyList.indexOf(nowPolicy)]);
					SoSstate = 0;
					for (int j=0; j<flagedTime.length; j++)
						flagedTime[j] = 0;
					priority = Integer.MAX_VALUE;
					nowPolicy = null;

					//tlight 정상화
					for (Entry<String, CS> e: csList.entrySet()){
						if (getPassingNodes().contains(e.getKey()))
							continue;
						e.getValue().updateAllTrafficLight(conn, trafficLightSignal);
						conn.do_job_set(Trafficlights.setRedYellowGreenState(e.getKey(), e.getValue().getTLight()));
					}
					System.out.println("------------------------------------------------Emergency finished");
				}
			}

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
				conn.do_job_set(Vehicle.add("ambul"+vehicleIdx, "ambulance", "ambul1", simtime, 0, 0.0, (byte) 0));
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
		boolean ambulancePolicyExist = false;
		boolean trafficjamPolicyExist = false;
		for (Policy p: policyList){
			//policy/factor/location/target=edges 인 경우.
			if (p.getFactor().getLocation_target().compareTo("edges")==0){
				List<String> tempEdgesList = new ArrayList<String>();
				for (String edg: p.getFactor().getEdges()){
					tempEdgesList.add(edg);
				}
				monitoringEdges.put(p.getId(), tempEdgesList);
			}
			if (p.getId().contains("traffic_jam")){
				changeToTrafficJamState = p.getFactor().getVehicle_number();
			}
			//policy에 ambulance 관련된 것이 있는지 우선 체크함
			if (p.getId().toLowerCase().contains("ambulance")){
				ambulancePolicyExist = true;
			}
			if (p.getId().toLowerCase().contains("traffic_jam")){
				trafficjamPolicyExist = true;
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
